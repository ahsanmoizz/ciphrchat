use axum::{routing::get, Json, Router};
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
    time::{Duration, SystemTime, UNIX_EPOCH},
};

const MAILBOX_PROTOCOL: &str = "/ciphrchat/mailbox/1";
const MAX_MAILBOX_PAYLOAD_BYTES: usize = 8 * 1024 * 1024;
const MAX_MESSAGES_PER_RECIPIENT: usize = 100;
const MAX_BYTES_PER_RECIPIENT: u64 = 64 * 1024 * 1024;
const MAX_RETENTION_MS: u64 = 7 * 24 * 60 * 60 * 1000;
const MAX_FETCH_MESSAGES: u16 = 16;
const MAILBOX_MAGIC: &[u8; 4] = b"CMB1";

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

async fn run_health_server(
    peer_id: PeerId,
    tcp_port: String,
    quic_port: String,
) -> anyhow::Result<()> {
    let health_port = env::var("CIPHRCHAT_HEALTH_PORT").unwrap_or_else(|_| "8080".to_owned());
    let address: SocketAddr = format!("0.0.0.0:{health_port}").parse()?;
    let info = RelayInfo {
        peer_id: peer_id.to_string(),
        tcp_port,
        quic_port,
    };
    let app = Router::new().route("/health", get(health)).route(
        "/info",
        get(move || {
            let info = info.clone();
            async move { Json(info) }
        }),
    );
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
    tokio::spawn(run_health_server(local_peer_id, tcp_port, quic_port));

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
}
