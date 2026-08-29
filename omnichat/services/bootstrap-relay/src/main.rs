use axum::{
    body::Bytes,
    extract::{Path as AxumPath, State},
    http::StatusCode,
    response::IntoResponse,
    routing::{delete, get, post},
    Json, Router,
};
use libp2p::{
    identify, identity, ping, relay,
    request_response::{self, cbor, Message, ProtocolSupport},
    swarm::{NetworkBehaviour, StreamProtocol, SwarmEvent},
    Multiaddr, PeerId, SwarmBuilder,
};
use serde::{Deserialize, Serialize};
#[cfg(unix)]
use std::os::unix::fs::{OpenOptionsExt, PermissionsExt};
use std::{
    env, fs,
    fs::OpenOptions,
    io::{Read, Write},
    net::SocketAddr,
    path::{Path, PathBuf},
    sync::Arc,
    time::{Duration, SystemTime, UNIX_EPOCH},
};

const MAILBOX_PROTOCOL: &str = "/ciphrchat/mailbox/1";
const MAX_MAILBOX_PAYLOAD_BYTES: usize = 8 * 1024 * 1024;
const MAX_MESSAGES_PER_RECIPIENT: usize = 100;
const MAX_BYTES_PER_RECIPIENT: u64 = 64 * 1024 * 1024;
const MAX_RETENTION_MS: u64 = 7 * 24 * 60 * 60 * 1000;
const MAX_FETCH_MESSAGES: u16 = 16;
const MAILBOX_MAGIC: &[u8; 4] = b"CMB1";

// 5 GiB maximum file size constraint
const MAX_FILE_SIZE_BYTES: u64 = 5 * 1024 * 1024 * 1024;
const MAX_TOTAL_FILE_QUOTA_BYTES: u64 = 50 * 1024 * 1024 * 1024;
const MAX_CHUNK_BYTES: usize = 4 * 1024 * 1024;

#[derive(Debug, Clone, Serialize, Deserialize)]
enum MailboxRequest {
    Put {
        recipient_peer_id: String,
        message_id: String,
        payload: Vec<u8>,
        expires_at_epoch_ms: u64,
    },
    Fetch {
        limit: u16,
    },
    Ack {
        message_id: String,
    },
}

#[derive(Debug, Clone, Serialize, Deserialize)]
struct MailboxMessage {
    message_id: String,
    sender_peer_id: String,
    payload: Vec<u8>,
    expires_at_epoch_ms: u64,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
enum MailboxResponse {
    Stored,
    Messages(Vec<MailboxMessage>),
    Acknowledged,
    Rejected(String),
}

type MailboxBehaviour = cbor::Behaviour<MailboxRequest, MailboxResponse>;

#[derive(Clone)]
struct MailboxStore {
    root: PathBuf,
}

impl MailboxStore {
    fn new(root: PathBuf) -> anyhow::Result<Self> {
        fs::create_dir_all(&root)?;
        Ok(Self { root })
    }

    fn put(
        &self,
        sender: PeerId,
        recipient_peer_id: String,
        message_id: String,
        payload: Vec<u8>,
        expires_at_epoch_ms: u64,
    ) -> anyhow::Result<()> {
        let recipient: PeerId = recipient_peer_id.parse()?;
        validate_message_id(&message_id)?;
        anyhow::ensure!(
            payload.len() <= MAX_MAILBOX_PAYLOAD_BYTES,
            "encrypted envelope exceeds 8 MiB"
        );
        let now = now_epoch_ms();
        anyhow::ensure!(expires_at_epoch_ms > now, "message is already expired");
        anyhow::ensure!(
            expires_at_epoch_ms <= now.saturating_add(MAX_RETENTION_MS),
            "message retention exceeds seven days"
        );

        let directory = self.recipient_directory(recipient);
        fs::create_dir_all(&directory)?;
        self.cleanup_expired(&directory)?;
        let target = directory.join(record_file_name(&message_id));
        if target.exists() {
            return Ok(());
        }
        let (count, bytes) = directory_usage(&directory)?;
        anyhow::ensure!(
            count < MAX_MESSAGES_PER_RECIPIENT,
            "recipient mailbox is full"
        );
        anyhow::ensure!(
            bytes.saturating_add(payload.len() as u64) <= MAX_BYTES_PER_RECIPIENT,
            "recipient mailbox byte quota exceeded"
        );

        let message = MailboxMessage {
            message_id,
            sender_peer_id: sender.to_string(),
            payload,
            expires_at_epoch_ms,
        };
        let temporary = directory.join(format!(
            ".{}.{}.tmp",
            std::process::id(),
            SystemTime::now().duration_since(UNIX_EPOCH)?.as_nanos()
        ));
        write_record(&temporary, &message)?;
        fs::rename(temporary, target)?;
        Ok(())
    }

    fn fetch(&self, recipient: PeerId, limit: u16) -> anyhow::Result<Vec<MailboxMessage>> {
        let directory = self.recipient_directory(recipient);
        if !directory.exists() {
            return Ok(Vec::new());
        }
        self.cleanup_expired(&directory)?;
        let mut paths = fs::read_dir(&directory)?
            .filter_map(Result::ok)
            .map(|entry| entry.path())
            .filter(|path| path.extension().and_then(|value| value.to_str()) == Some("msg"))
            .collect::<Vec<_>>();
        paths.sort();
        let limit = limit.clamp(1, MAX_FETCH_MESSAGES) as usize;
        Ok(paths
            .into_iter()
            .filter_map(|path| read_record(&path).ok())
            .take(limit)
            .collect())
    }

    fn acknowledge(&self, recipient: PeerId, message_id: &str) -> anyhow::Result<()> {
        validate_message_id(message_id)?;
        let target = self
            .recipient_directory(recipient)
            .join(record_file_name(message_id));
        if target.exists() {
            fs::remove_file(target)?;
        }
        Ok(())
    }

    fn recipient_directory(&self, recipient: PeerId) -> PathBuf {
        self.root.join(recipient.to_string())
    }

    fn cleanup_expired(&self, directory: &Path) -> anyhow::Result<()> {
        let now = now_epoch_ms();
        for entry in fs::read_dir(directory)? {
            let path = entry?.path();
            if path.extension().and_then(|value| value.to_str()) != Some("msg") {
                continue;
            }
            if read_record(&path)
                .map(|message| message.expires_at_epoch_ms <= now)
                .unwrap_or(true)
            {
                let _ = fs::remove_file(path);
            }
        }
        Ok(())
    }
}

// -------------------------------------------------------------------------------------------------
// Large File Store (Up to 5 GiB Streaming & Resume)
// -------------------------------------------------------------------------------------------------

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct FileMetadata {
    pub file_id: String,
    pub sender_peer_id: String,
    pub recipient_peer_id: String,
    pub file_size: u64,
    pub chunk_size: u32,
    pub total_chunks: u32,
    pub sha256: String,
    pub expires_at_epoch_ms: u64,
    pub completed: bool,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct FileStatusResponse {
    pub file_id: String,
    pub file_size: u64,
    pub chunk_size: u32,
    pub total_chunks: u32,
    pub uploaded_chunks: Vec<u32>,
    pub completed: bool,
    pub expires_at_epoch_ms: u64,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct InitFileRequest {
    pub file_id: String,
    pub sender_peer_id: String,
    pub recipient_peer_id: String,
    pub file_size: u64,
    pub chunk_size: u32,
    pub total_chunks: u32,
    pub sha256: String,
    pub expires_at_epoch_ms: u64,
}

#[derive(Clone)]
pub struct FileStore {
    root: PathBuf,
    max_total_quota: u64,
}

impl FileStore {
    pub fn new(root: PathBuf, max_total_quota: u64) -> anyhow::Result<Self> {
        fs::create_dir_all(&root)?;
        Ok(Self {
            root,
            max_total_quota,
        })
    }

    pub fn init_file(&self, req: InitFileRequest) -> anyhow::Result<()> {
        validate_file_id(&req.file_id)?;
        anyhow::ensure!(
            req.file_size > 0 && req.file_size <= MAX_FILE_SIZE_BYTES,
            "File size exceeds 5 GiB maximum limit or is 0"
        );
        anyhow::ensure!(
            req.chunk_size as usize <= MAX_CHUNK_BYTES && req.chunk_size >= 1024,
            "Chunk size must be between 1 KiB and 4 MiB"
        );
        let expected_chunks = req.file_size.div_ceil(req.chunk_size as u64) as u32;
        anyhow::ensure!(
            req.total_chunks == expected_chunks,
            "Total chunks mismatch for file size"
        );
        let now = now_epoch_ms();
        anyhow::ensure!(req.expires_at_epoch_ms > now, "File is already expired");
        anyhow::ensure!(
            req.expires_at_epoch_ms <= now.saturating_add(MAX_RETENTION_MS),
            "Retention exceeds 7 days"
        );

        let total_used = self.total_disk_usage()?;
        anyhow::ensure!(
            total_used.saturating_add(req.file_size) <= self.max_total_quota,
            "Server disk quota exceeded for large files"
        );

        let file_dir = self.file_directory(&req.file_id)?;
        fs::create_dir_all(&file_dir)?;

        let meta = FileMetadata {
            file_id: req.file_id,
            sender_peer_id: req.sender_peer_id,
            recipient_peer_id: req.recipient_peer_id,
            file_size: req.file_size,
            chunk_size: req.chunk_size,
            total_chunks: req.total_chunks,
            sha256: req.sha256,
            expires_at_epoch_ms: req.expires_at_epoch_ms,
            completed: false,
        };

        let meta_json = serde_json::to_vec_pretty(&meta)?;
        let meta_path = file_dir.join("meta.json");
        fs::write(meta_path, meta_json)?;
        Ok(())
    }

    pub fn save_chunk(&self, file_id: &str, chunk_index: u32, data: &[u8]) -> anyhow::Result<()> {
        validate_file_id(file_id)?;
        let file_dir = self.file_directory(file_id)?;
        let meta_path = file_dir.join("meta.json");
        anyhow::ensure!(meta_path.exists(), "File transfer has not been initialized");

        let meta: FileMetadata = serde_json::from_slice(&fs::read(&meta_path)?)?;
        anyhow::ensure!(chunk_index < meta.total_chunks, "Chunk index out of bounds");
        anyhow::ensure!(
            data.len() <= meta.chunk_size as usize,
            "Chunk data exceeds negotiated chunk size"
        );

        let chunk_file = file_dir.join(format!("chunk_{chunk_index}.part"));
        let temp_file = file_dir.join(format!(
            ".tmp_chunk_{chunk_index}_{}",
            SystemTime::now().duration_since(UNIX_EPOCH)?.as_nanos()
        ));
        fs::write(&temp_file, data)?;
        fs::rename(temp_file, chunk_file)?;
        Ok(())
    }

    pub fn get_chunk(&self, file_id: &str, chunk_index: u32) -> anyhow::Result<Vec<u8>> {
        validate_file_id(file_id)?;
        let file_dir = self.file_directory(file_id)?;
        let chunk_file = file_dir.join(format!("chunk_{chunk_index}.part"));
        anyhow::ensure!(chunk_file.exists(), "Chunk not found");
        Ok(fs::read(chunk_file)?)
    }

    pub fn get_status(&self, file_id: &str) -> anyhow::Result<FileStatusResponse> {
        validate_file_id(file_id)?;
        let file_dir = self.file_directory(file_id)?;
        let meta_path = file_dir.join("meta.json");
        anyhow::ensure!(meta_path.exists(), "File transfer not found");

        let meta: FileMetadata = serde_json::from_slice(&fs::read(&meta_path)?)?;
        let mut uploaded = Vec::new();
        for i in 0..meta.total_chunks {
            if file_dir.join(format!("chunk_{i}.part")).exists() {
                uploaded.push(i);
            }
        }

        Ok(FileStatusResponse {
            file_id: meta.file_id,
            file_size: meta.file_size,
            chunk_size: meta.chunk_size,
            total_chunks: meta.total_chunks,
            uploaded_chunks: uploaded,
            completed: meta.completed,
            expires_at_epoch_ms: meta.expires_at_epoch_ms,
        })
    }

    pub fn complete_file(&self, file_id: &str) -> anyhow::Result<()> {
        validate_file_id(file_id)?;
        let file_dir = self.file_directory(file_id)?;
        let meta_path = file_dir.join("meta.json");
        anyhow::ensure!(meta_path.exists(), "File not found");

        let mut meta: FileMetadata = serde_json::from_slice(&fs::read(&meta_path)?)?;
        for i in 0..meta.total_chunks {
            anyhow::ensure!(
                file_dir.join(format!("chunk_{i}.part")).exists(),
                "Cannot complete: missing chunk {i}"
            );
        }
        meta.completed = true;
        fs::write(meta_path, serde_json::to_vec_pretty(&meta)?)?;
        Ok(())
    }

    pub fn delete_file(&self, file_id: &str) -> anyhow::Result<()> {
        validate_file_id(file_id)?;
        let file_dir = self.file_directory(file_id)?;
        if file_dir.exists() {
            fs::remove_dir_all(file_dir)?;
        }
        Ok(())
    }

    fn file_directory(&self, file_id: &str) -> anyhow::Result<PathBuf> {
        validate_file_id(file_id)?;
        Ok(self.root.join(file_id))
    }

    fn total_disk_usage(&self) -> anyhow::Result<u64> {
        let mut total = 0u64;
        if !self.root.exists() {
            return Ok(0);
        }
        for entry in fs::read_dir(&self.root)? {
            let entry = entry?;
            if entry.path().is_dir() {
                for file_entry in fs::read_dir(entry.path())? {
                    let file_entry = file_entry?;
                    total = total.saturating_add(file_entry.metadata()?.len());
                }
            }
        }
        Ok(total)
    }

    pub fn cleanup_expired(&self) -> anyhow::Result<()> {
        let now = now_epoch_ms();
        if !self.root.exists() {
            return Ok(());
        }
        for entry in fs::read_dir(&self.root)? {
            let entry = entry?;
            let path = entry.path();
            if path.is_dir() {
                let meta_path = path.join("meta.json");
                if meta_path.exists() {
                    if let Ok(meta) = fs::read(&meta_path)
                        .and_then(|b| Ok(serde_json::from_slice::<FileMetadata>(&b)?))
                    {
                        if meta.expires_at_epoch_ms <= now {
                            let _ = fs::remove_dir_all(path);
                        }
                    }
                } else {
                    let _ = fs::remove_dir_all(path);
                }
            }
        }
        Ok(())
    }
}

fn validate_message_id(message_id: &str) -> anyhow::Result<()> {
    anyhow::ensure!(
        !message_id.is_empty() && message_id.len() <= 256,
        "invalid message id"
    );
    anyhow::ensure!(
        message_id
            .bytes()
            .all(|byte| byte.is_ascii_alphanumeric() || b"-_:".contains(&byte)),
        "message id contains unsupported characters"
    );
    Ok(())
}

fn validate_file_id(file_id: &str) -> anyhow::Result<()> {
    anyhow::ensure!(
        !file_id.is_empty() && file_id.len() <= 64,
        "File ID must be 1-64 characters"
    );
    anyhow::ensure!(
        file_id
            .bytes()
            .all(|byte| byte.is_ascii_alphanumeric() || byte == b'-' || byte == b'_'),
        "File ID contains invalid path characters (path traversal defense)"
    );
    Ok(())
}

fn record_file_name(message_id: &str) -> String {
    let hex = message_id
        .as_bytes()
        .iter()
        .map(|byte| format!("{byte:02x}"))
        .collect::<String>();
    format!("{hex}.msg")
}

fn directory_usage(directory: &Path) -> anyhow::Result<(usize, u64)> {
    let mut count = 0;
    let mut bytes = 0u64;
    for entry in fs::read_dir(directory)? {
        let entry = entry?;
        if entry.path().extension().and_then(|value| value.to_str()) == Some("msg") {
            count += 1;
            bytes = bytes.saturating_add(entry.metadata()?.len());
        }
    }
    Ok((count, bytes))
}

fn write_record(path: &Path, message: &MailboxMessage) -> anyhow::Result<()> {
    let sender = message.sender_peer_id.as_bytes();
    let message_id = message.message_id.as_bytes();
    anyhow::ensure!(
        sender.len() <= u16::MAX as usize,
        "sender peer id is too long"
    );
    anyhow::ensure!(
        message_id.len() <= u16::MAX as usize,
        "message id is too long"
    );
    let mut file = OpenOptions::new().write(true).create_new(true).open(path)?;
    file.write_all(MAILBOX_MAGIC)?;
    file.write_all(&message.expires_at_epoch_ms.to_be_bytes())?;
    file.write_all(&(sender.len() as u16).to_be_bytes())?;
    file.write_all(&(message_id.len() as u16).to_be_bytes())?;
    file.write_all(&(message.payload.len() as u32).to_be_bytes())?;
    file.write_all(sender)?;
    file.write_all(message_id)?;
    file.write_all(&message.payload)?;
    file.sync_all()?;
    Ok(())
}

fn read_record(path: &Path) -> anyhow::Result<MailboxMessage> {
    let mut file = fs::File::open(path)?;
    let mut magic = [0u8; 4];
    let mut expires = [0u8; 8];
    let mut sender_len = [0u8; 2];
    let mut message_len = [0u8; 2];
    let mut payload_len = [0u8; 4];
    file.read_exact(&mut magic)?;
    anyhow::ensure!(&magic == MAILBOX_MAGIC, "invalid mailbox record");
    file.read_exact(&mut expires)?;
    file.read_exact(&mut sender_len)?;
    file.read_exact(&mut message_len)?;
    file.read_exact(&mut payload_len)?;
    let sender_len = u16::from_be_bytes(sender_len) as usize;
    let message_len = u16::from_be_bytes(message_len) as usize;
    let payload_len = u32::from_be_bytes(payload_len) as usize;
    anyhow::ensure!(
        payload_len <= MAX_MAILBOX_PAYLOAD_BYTES,
        "invalid mailbox payload size"
    );
    let mut sender = vec![0u8; sender_len];
    let mut message_id = vec![0u8; message_len];
    let mut payload = vec![0u8; payload_len];
    file.read_exact(&mut sender)?;
    file.read_exact(&mut message_id)?;
    file.read_exact(&mut payload)?;
    Ok(MailboxMessage {
        message_id: String::from_utf8(message_id)?,
        sender_peer_id: String::from_utf8(sender)?,
        payload,
        expires_at_epoch_ms: u64::from_be_bytes(expires),
    })
}

fn now_epoch_ms() -> u64 {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .unwrap_or_default()
        .as_millis() as u64
}

#[derive(Serialize)]
struct Health {
    service: &'static str,
    status: &'static str,
    version: &'static str,
}

#[derive(Clone, Serialize)]
struct RelayInfo {
    peer_id: String,
    tcp_port: String,
    quic_port: String,
}

async fn health() -> Json<Health> {
    Json(Health {
        service: "ciphrchat-bootstrap-relay",
        status: "ok",
        version: env!("CARGO_PKG_VERSION"),
    })
}

// -------------------------------------------------------------------------------------------------
// Axum Handlers for Large Files
// -------------------------------------------------------------------------------------------------

#[derive(Clone)]
struct AppState {
    info: RelayInfo,
    file_store: Arc<FileStore>,
}

async fn init_file_handler(
    State(state): State<AppState>,
    Json(req): Json<InitFileRequest>,
) -> Result<StatusCode, (StatusCode, String)> {
    state
        .file_store
        .init_file(req)
        .map(|_| StatusCode::CREATED)
        .map_err(|e| (StatusCode::BAD_REQUEST, e.to_string()))
}

async fn upload_chunk_handler(
    State(state): State<AppState>,
    AxumPath((file_id, chunk_index)): AxumPath<(String, u32)>,
    body: Bytes,
) -> Result<StatusCode, (StatusCode, String)> {
    state
        .file_store
        .save_chunk(&file_id, chunk_index, &body)
        .map(|_| StatusCode::OK)
        .map_err(|e| (StatusCode::BAD_REQUEST, e.to_string()))
}

async fn download_chunk_handler(
    State(state): State<AppState>,
    AxumPath((file_id, chunk_index)): AxumPath<(String, u32)>,
) -> Result<impl IntoResponse, (StatusCode, String)> {
    state
        .file_store
        .get_chunk(&file_id, chunk_index)
        .map(|data| {
            (
                [(axum::http::header::CONTENT_TYPE, "application/octet-stream")],
                data,
            )
        })
        .map_err(|e| (StatusCode::NOT_FOUND, e.to_string()))
}

async fn file_status_handler(
    State(state): State<AppState>,
    AxumPath(file_id): AxumPath<String>,
) -> Result<Json<FileStatusResponse>, (StatusCode, String)> {
    state
        .file_store
        .get_status(&file_id)
        .map(Json)
        .map_err(|e| (StatusCode::NOT_FOUND, e.to_string()))
}

async fn complete_file_handler(
    State(state): State<AppState>,
    AxumPath(file_id): AxumPath<String>,
) -> Result<StatusCode, (StatusCode, String)> {
    state
        .file_store
        .complete_file(&file_id)
        .map(|_| StatusCode::OK)
        .map_err(|e| (StatusCode::BAD_REQUEST, e.to_string()))
}

async fn delete_file_handler(
    State(state): State<AppState>,
    AxumPath(file_id): AxumPath<String>,
) -> Result<StatusCode, (StatusCode, String)> {
    state
        .file_store
        .delete_file(&file_id)
        .map(|_| StatusCode::OK)
        .map_err(|e| (StatusCode::BAD_REQUEST, e.to_string()))
}

#[derive(NetworkBehaviour)]
struct RelayBehaviour {
    relay: relay::Behaviour,
    mailbox: MailboxBehaviour,
    identify: identify::Behaviour,
    ping: ping::Behaviour,
}

fn relay_key_path() -> PathBuf {
    env::var_os("CIPHRCHAT_RELAY_KEY_FILE")
        .map(PathBuf::from)
        .unwrap_or_else(|| PathBuf::from("/var/lib/ciphrchat/relay-key.bin"))
}

fn mailbox_path() -> PathBuf {
    env::var_os("CIPHRCHAT_MAILBOX_DIRECTORY")
        .map(PathBuf::from)
        .unwrap_or_else(|| PathBuf::from("/var/lib/ciphrchat/mailbox"))
}

fn files_path() -> PathBuf {
    env::var_os("CIPHRCHAT_FILES_DIRECTORY")
        .map(PathBuf::from)
        .unwrap_or_else(|| PathBuf::from("/var/lib/ciphrchat/files"))
}

fn handle_mailbox_request(
    store: &MailboxStore,
    peer: PeerId,
    request: MailboxRequest,
) -> MailboxResponse {
    match request {
        MailboxRequest::Put {
            recipient_peer_id,
            message_id,
            payload,
            expires_at_epoch_ms,
        } => store
            .put(
                peer,
                recipient_peer_id,
                message_id,
                payload,
                expires_at_epoch_ms,
            )
            .map(|_| MailboxResponse::Stored),
        MailboxRequest::Fetch { limit } => store.fetch(peer, limit).map(MailboxResponse::Messages),
        MailboxRequest::Ack { message_id } => store
            .acknowledge(peer, &message_id)
            .map(|_| MailboxResponse::Acknowledged),
    }
    .unwrap_or_else(|error| MailboxResponse::Rejected(error.to_string()))
}

fn load_or_create_relay_key(path: &Path) -> anyhow::Result<identity::Keypair> {
    if let Some(parent) = path.parent() {
        fs::create_dir_all(parent)?;
    }

    if path.exists() {
        let encoded = fs::read(path)?;
        return identity::Keypair::from_protobuf_encoding(&encoded)
            .map_err(|error| anyhow::anyhow!("invalid relay key at {}: {error}", path.display()));
    }

    let key = identity::Keypair::generate_ed25519();
    let encoded = key.to_protobuf_encoding()?;
    let temporary_path = path.with_extension(format!("{}.tmp", std::process::id()));
    let mut options = OpenOptions::new();
    options.write(true).create_new(true);
    #[cfg(unix)]
    options.mode(0o600);
    let mut file = options.open(&temporary_path)?;
    file.write_all(&encoded)?;
    file.sync_all()?;
    fs::rename(&temporary_path, path)?;
    #[cfg(unix)]
    fs::set_permissions(path, fs::Permissions::from_mode(0o600))?;
    Ok(key)
}

async fn run_http_server(
    peer_id: PeerId,
    tcp_port: String,
    quic_port: String,
    file_store: Arc<FileStore>,
) -> anyhow::Result<()> {
    let health_port = env::var("CIPHRCHAT_HEALTH_PORT").unwrap_or_else(|_| "8080".to_owned());
    let address: SocketAddr = format!("0.0.0.0:{health_port}").parse()?;
    let info = RelayInfo {
        peer_id: peer_id.to_string(),
        tcp_port,
        quic_port,
    };
    let app_state = AppState { info, file_store };

    let app = Router::new()
        .route("/health", get(health))
        .route(
            "/info",
            get(|State(state): State<AppState>| async move { Json(state.info) }),
        )
        .route("/files/init", post(init_file_handler))
        .route(
            "/files/upload/{file_id}/{chunk_index}",
            post(upload_chunk_handler),
        )
        .route(
            "/files/download/{file_id}/{chunk_index}",
            get(download_chunk_handler),
        )
        .route("/files/status/{file_id}", get(file_status_handler))
        .route("/files/complete/{file_id}", post(complete_file_handler))
        .route("/files/{file_id}", delete(delete_file_handler))
        .with_state(app_state);

    let listener = tokio::net::TcpListener::bind(address).await?;
    axum::serve(listener, app).await?;
    Ok(())
}

#[tokio::main]
async fn main() -> anyhow::Result<()> {
    let key_path = relay_key_path();
    let local_key = load_or_create_relay_key(&key_path)?;
    let local_peer_id = PeerId::from(local_key.public());
    let mailbox = MailboxStore::new(mailbox_path())?;
    let file_store = Arc::new(FileStore::new(files_path(), MAX_TOTAL_FILE_QUOTA_BYTES)?);

    let relay_config = relay::Config {
        max_reservations: 128,
        max_reservations_per_peer: 2,
        reservation_duration: Duration::from_secs(60 * 60),
        max_circuits: 512,
        max_circuits_per_peer: 8,
        max_circuit_duration: Duration::from_secs(15 * 60),
        max_circuit_bytes: 64 * 1024 * 1024,
        ..relay::Config::default()
    };

    let mut swarm = SwarmBuilder::with_existing_identity(local_key)
        .with_tokio()
        .with_tcp(
            libp2p::tcp::Config::default().nodelay(true),
            libp2p::noise::Config::new,
            libp2p::yamux::Config::default,
        )?
        .with_quic()
        .with_behaviour(|key| RelayBehaviour {
            relay: relay::Behaviour::new(key.public().to_peer_id(), relay_config),
            mailbox: cbor::Behaviour::new(
                [(
                    StreamProtocol::new(MAILBOX_PROTOCOL),
                    ProtocolSupport::Inbound,
                )],
                request_response::Config::default().with_request_timeout(Duration::from_secs(45)),
            ),
            identify: identify::Behaviour::new(identify::Config::new(
                "/ciphrchat/1.0.0".to_owned(),
                key.public(),
            )),
            ping: ping::Behaviour::new(ping::Config::new()),
        })?
        .with_swarm_config(|config| config.with_idle_connection_timeout(Duration::from_secs(90)))
        .build();

    let tcp_port = env::var("CIPHRCHAT_RELAY_TCP_PORT").unwrap_or_else(|_| "4001".to_owned());
    let quic_port = env::var("CIPHRCHAT_RELAY_QUIC_PORT").unwrap_or_else(|_| "4001".to_owned());
    let tcp_address: Multiaddr = format!("/ip4/0.0.0.0/tcp/{tcp_port}").parse()?;
    let quic_address: Multiaddr = format!("/ip4/0.0.0.0/udp/{quic_port}/quic-v1").parse()?;
    swarm.listen_on(tcp_address)?;
    swarm.listen_on(quic_address)?;

    eprintln!("CiphrChat relay peer id: {local_peer_id}");
    eprintln!("CiphrChat relay key file: {}", key_path.display());
    tokio::spawn(run_http_server(
        local_peer_id,
        tcp_port,
        quic_port,
        file_store.clone(),
    ));

    // Periodic cleanup of expired files every hour
    let cleanup_store = file_store.clone();
    tokio::spawn(async move {
        let mut interval = tokio::time::interval(Duration::from_secs(3600));
        loop {
            interval.tick().await;
            let _ = cleanup_store.cleanup_expired();
        }
    });

    while let Some(event) = futures::StreamExt::next(&mut swarm).await {
        match event {
            SwarmEvent::NewListenAddr { address, .. } => {
                eprintln!("CiphrChat relay listening on {address}");
            }
            SwarmEvent::Behaviour(RelayBehaviourEvent::Mailbox(
                request_response::Event::Message {
                    peer,
                    message:
                        Message::Request {
                            request, channel, ..
                        },
                    ..
                },
            )) => {
                let response = handle_mailbox_request(&mailbox, peer, request);
                let _ = swarm
                    .behaviour_mut()
                    .mailbox
                    .send_response(channel, response);
            }
            SwarmEvent::IncomingConnection { .. }
            | SwarmEvent::ConnectionEstablished { .. }
            | SwarmEvent::ConnectionClosed { .. }
            | SwarmEvent::Behaviour(_) => {}
            _ => {}
        }
    }

    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    fn test_store(label: &str) -> (MailboxStore, PathBuf) {
        let path = std::env::temp_dir().join(format!(
            "ciphrchat-mailbox-{label}-{}-{}",
            std::process::id(),
            SystemTime::now()
                .duration_since(UNIX_EPOCH)
                .unwrap()
                .as_nanos()
        ));
        (MailboxStore::new(path.clone()).unwrap(), path)
    }

    fn test_file_store(label: &str, quota: u64) -> (FileStore, PathBuf) {
        let path = std::env::temp_dir().join(format!(
            "ciphrchat-files-{label}-{}-{}",
            std::process::id(),
            SystemTime::now()
                .duration_since(UNIX_EPOCH)
                .unwrap()
                .as_nanos()
        ));
        (FileStore::new(path.clone(), quota).unwrap(), path)
    }

    #[test]
    fn encrypted_envelope_is_fetched_only_by_recipient_and_deleted_after_ack() {
        let (store, path) = test_store("roundtrip");
        let sender = PeerId::from(identity::Keypair::generate_ed25519().public());
        let recipient = PeerId::from(identity::Keypair::generate_ed25519().public());
        let stranger = PeerId::from(identity::Keypair::generate_ed25519().public());
        let payload = vec![0xA5; 1024];

        store
            .put(
                sender,
                recipient.to_string(),
                "message-1".to_owned(),
                payload.clone(),
                now_epoch_ms() + 60_000,
            )
            .unwrap();

        assert!(store.fetch(stranger, 16).unwrap().is_empty());
        let fetched = store.fetch(recipient, 16).unwrap();
        assert_eq!(fetched.len(), 1);
        assert_eq!(fetched[0].sender_peer_id, sender.to_string());
        assert_eq!(fetched[0].payload, payload);

        store.acknowledge(recipient, "message-1").unwrap();
        assert!(store.fetch(recipient, 16).unwrap().is_empty());
        let _ = fs::remove_dir_all(path);
    }

    #[test]
    fn expired_or_oversized_mail_is_rejected() {
        let (store, path) = test_store("limits");
        let sender = PeerId::from(identity::Keypair::generate_ed25519().public());
        let recipient = PeerId::from(identity::Keypair::generate_ed25519().public());

        assert!(store
            .put(
                sender,
                recipient.to_string(),
                "expired".to_owned(),
                vec![1],
                now_epoch_ms().saturating_sub(1),
            )
            .is_err());
        assert!(store
            .put(
                sender,
                recipient.to_string(),
                "oversized".to_owned(),
                vec![0; MAX_MAILBOX_PAYLOAD_BYTES + 1],
                now_epoch_ms() + 60_000,
            )
            .is_err());
        let _ = fs::remove_dir_all(path);
    }

    #[test]
    fn file_store_chunk_streaming_and_resume() {
        let (store, path) = test_file_store("resume", 100 * 1024 * 1024);
        let file_id = "550e8400-e29b-41d4-a716-446655440000";
        let chunk_size = 64 * 1024;
        let file_size = 150 * 1024; // 3 chunks
        let total_chunks = 3;

        store
            .init_file(InitFileRequest {
                file_id: file_id.to_owned(),
                sender_peer_id: "sender1".to_owned(),
                recipient_peer_id: "recipient1".to_owned(),
                file_size,
                chunk_size,
                total_chunks,
                sha256: "dummy-sha256".to_owned(),
                expires_at_epoch_ms: now_epoch_ms() + 60_000,
            })
            .unwrap();

        // Upload chunk 0 and 2 (simulate chunk 1 missing/interrupted)
        let chunk0 = vec![1u8; chunk_size as usize];
        let chunk2 = vec![3u8; (file_size - 2 * chunk_size as u64) as usize];
        store.save_chunk(file_id, 0, &chunk0).unwrap();
        store.save_chunk(file_id, 2, &chunk2).unwrap();

        let status = store.get_status(file_id).unwrap();
        assert_eq!(status.uploaded_chunks, vec![0, 2]);
        assert!(!status.completed);

        // Completion fails when chunk 1 is missing
        assert!(store.complete_file(file_id).is_err());

        // Resume: Upload missing chunk 1
        let chunk1 = vec![2u8; chunk_size as usize];
        store.save_chunk(file_id, 1, &chunk1).unwrap();

        let status2 = store.get_status(file_id).unwrap();
        assert_eq!(status2.uploaded_chunks, vec![0, 1, 2]);

        // Complete succeeds
        assert!(store.complete_file(file_id).is_ok());

        // Fetch chunk
        let fetched0 = store.get_chunk(file_id, 0).unwrap();
        assert_eq!(fetched0, chunk0);

        // Delete
        store.delete_file(file_id).unwrap();
        assert!(store.get_status(file_id).is_err());

        let _ = fs::remove_dir_all(path);
    }

    #[test]
    fn file_store_rejects_oversized_and_path_traversal() {
        let (store, path) = test_file_store("security", 100 * 1024 * 1024);

        // Reject > 5 GiB
        let oversized = MAX_FILE_SIZE_BYTES + 1;
        assert!(store
            .init_file(InitFileRequest {
                file_id: "valid-file-id".to_owned(),
                sender_peer_id: "sender".to_owned(),
                recipient_peer_id: "recipient".to_owned(),
                file_size: oversized,
                chunk_size: 1024 * 1024,
                total_chunks: 5121,
                sha256: "sha".to_owned(),
                expires_at_epoch_ms: now_epoch_ms() + 60_000,
            })
            .is_err());

        // Reject path traversal file ID
        assert!(store
            .init_file(InitFileRequest {
                file_id: "../../etc/passwd".to_owned(),
                sender_peer_id: "sender".to_owned(),
                recipient_peer_id: "recipient".to_owned(),
                file_size: 1024,
                chunk_size: 1024,
                total_chunks: 1,
                sha256: "sha".to_owned(),
                expires_at_epoch_ms: now_epoch_ms() + 60_000,
            })
            .is_err());

        let _ = fs::remove_dir_all(path);
    }

    #[test]
    fn file_store_enforces_disk_quota() {
        let small_quota = 500 * 1024; // 500 KiB quota
        let (store, path) = test_file_store("quota", small_quota);

        // File exceeding total server quota
        assert!(store
            .init_file(InitFileRequest {
                file_id: "large-file".to_owned(),
                sender_peer_id: "sender".to_owned(),
                recipient_peer_id: "recipient".to_owned(),
                file_size: 600 * 1024,
                chunk_size: 64 * 1024,
                total_chunks: 10,
                sha256: "sha".to_owned(),
                expires_at_epoch_ms: now_epoch_ms() + 60_000,
            })
            .is_err());

        let _ = fs::remove_dir_all(path);
    }
}
