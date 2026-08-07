use jni::{
    objects::{JByteArray, JClass, JObject, JString, JValue},
    sys::jboolean,
    JNIEnv, JavaVM,
};
#[cfg(unix)]
use std::os::unix::fs::{OpenOptionsExt, PermissionsExt};
use std::{
    fs::{self, OpenOptions},
    io::Write,
    path::Path,
    sync::{Mutex, OnceLock},
    thread,
};
use tokio::{runtime::Runtime, sync::mpsc};

use ciphrchat_routing::{ClientEvent, SwarmCommand};
use libp2p::{identity, Multiaddr, PeerId};

static COMMANDS: OnceLock<Mutex<Option<mpsc::UnboundedSender<SwarmCommand>>>> = OnceLock::new();
static JVM: OnceLock<JavaVM> = OnceLock::new();
static LOCAL_PEER_ID: OnceLock<String> = OnceLock::new();

fn command_sender() -> Option<mpsc::UnboundedSender<SwarmCommand>> {
    COMMANDS
        .get()
        .and_then(|slot| slot.lock().ok())
        .and_then(|sender| sender.as_ref().cloned())
}

fn load_or_create_key(path: &Path) -> Result<identity::Keypair, String> {
    if let Some(parent) = path.parent() {
        fs::create_dir_all(parent).map_err(|error| error.to_string())?;
    }

    if path.exists() {
        let encoded = fs::read(path).map_err(|error| error.to_string())?;
        return identity::Keypair::from_protobuf_encoding(&encoded)
            .map_err(|error| format!("invalid peer key: {error}"));
    }

    let key = identity::Keypair::generate_ed25519();
    let encoded = key
        .to_protobuf_encoding()
        .map_err(|error| format!("cannot encode peer key: {error}"))?;
    let temporary_path = path.with_extension(format!("{}.tmp", std::process::id()));
    let mut options = OpenOptions::new();
    options.write(true).create_new(true);
    #[cfg(unix)]
    options.mode(0o600);
    let mut file = options
        .open(&temporary_path)
        .map_err(|error| error.to_string())?;
    file.write_all(&encoded)
        .map_err(|error| error.to_string())?;
    file.sync_all().map_err(|error| error.to_string())?;
    fs::rename(&temporary_path, path).map_err(|error| error.to_string())?;
    #[cfg(unix)]
    fs::set_permissions(path, fs::Permissions::from_mode(0o600))
        .map_err(|error| error.to_string())?;
    Ok(key)
}

fn jstring(env: &mut JNIEnv<'_>, value: JString<'_>) -> Result<String, String> {
    env.get_string(&value)
        .map(|value| value.to_string_lossy().into_owned())
        .map_err(|error| error.to_string())
}

fn call_callback(env: &mut JNIEnv<'_>, name: &str, signature: &str, args: &[JValue<'_, '_>]) {
    let class_name = "org/ciphrchat/app/transport/internet/RustP2pManager";
    if let Ok(class) = env.find_class(class_name) {
        if let Err(error) = env.call_static_method(class, name, signature, args) {
            eprintln!("CiphrChat JNI callback {name} failed: {error}");
        }
    }
}

fn dispatch_event(jvm: &JavaVM, event: ClientEvent) {
    let Ok(mut env) = jvm.attach_current_thread() else {
        eprintln!("CiphrChat could not attach network callback thread to JVM");
        return;
    };

    match event {
        ClientEvent::Ready { peer_id } => {
            if let Ok(peer_id) = env.new_string(peer_id.to_string()) {
                let peer_id_object: JObject<'_> = peer_id.into();
                call_callback(
                    &mut env,
                    "onSwarmReady",
                    "(Ljava/lang/String;)V",
                    &[JValue::Object(&peer_id_object)],
                );
            }
        }
        ClientEvent::InboundMessage { peer_id, payload } => {
            if let (Ok(peer_id), Ok(payload)) = (
                env.new_string(peer_id.to_string()),
                env.byte_array_from_slice(&payload),
            ) {
                let peer_id_object: JObject<'_> = peer_id.into();
                let payload_object: JObject<'_> = payload.into();
                call_callback(
                    &mut env,
                    "onMessageReceived",
                    "(Ljava/lang/String;[B)V",
                    &[
                        JValue::Object(&peer_id_object),
                        JValue::Object(&payload_object),
                    ],
                );
            }
        }
        ClientEvent::DeliveryAccepted { peer_id } => {
            if let Ok(peer_id) = env.new_string(peer_id.to_string()) {
                let peer_id_object: JObject<'_> = peer_id.into();
                call_callback(
                    &mut env,
                    "onDeliveryAccepted",
                    "(Ljava/lang/String;)V",
                    &[JValue::Object(&peer_id_object)],
                );
            }
        }
        ClientEvent::DeliveryFailed { peer_id, reason } => {
            if let (Ok(peer_id), Ok(reason)) =
                (env.new_string(peer_id.to_string()), env.new_string(reason))
            {
                let peer_id_object: JObject<'_> = peer_id.into();
                let reason_object: JObject<'_> = reason.into();
                call_callback(
                    &mut env,
                    "onDeliveryFailed",
                    "(Ljava/lang/String;Ljava/lang/String;)V",
                    &[
                        JValue::Object(&peer_id_object),
                        JValue::Object(&reason_object),
                    ],
                );
            }
        }
        ClientEvent::NetworkError { detail } => {
            if let Ok(detail) = env.new_string(detail) {
                let detail_object: JObject<'_> = detail.into();
                call_callback(
                    &mut env,
                    "onNetworkError",
                    "(Ljava/lang/String;)V",
                    &[JValue::Object(&detail_object)],
                );
            }
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_org_ciphrchat_app_transport_internet_RustP2pManager_nativeStartSwarm<
    'local,
>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    relay_address: JString<'local>,
    key_file: JString<'local>,
) -> jboolean {
    let relay_address = match jstring(&mut env, relay_address).and_then(|value| {
        value
            .parse::<Multiaddr>()
            .map_err(|error| format!("invalid relay address: {error}"))
    }) {
        Ok(value) => value,
        Err(error) => {
            eprintln!("CiphrChat cannot start swarm: {error}");
            return 0;
        }
    };
    let key_file = match jstring(&mut env, key_file) {
        Ok(value) => value,
        Err(error) => {
            eprintln!("CiphrChat cannot read peer key path: {error}");
            return 0;
        }
    };
    let local_key = match load_or_create_key(Path::new(&key_file)) {
        Ok(key) => key,
        Err(error) => {
            eprintln!("CiphrChat cannot load peer key: {error}");
            return 0;
        }
    };
    let local_peer_id = PeerId::from(local_key.public());
    let _ = LOCAL_PEER_ID.set(local_peer_id.to_string());
    let _ = JVM.set(env.get_java_vm().expect("JNI VM must be available"));

    let (command_tx, command_rx) = mpsc::unbounded_channel();
    let (event_tx, mut event_rx) = mpsc::unbounded_channel();
    let command_slot = COMMANDS.get_or_init(|| Mutex::new(None));
    let Ok(mut slot) = command_slot.lock() else {
        eprintln!("CiphrChat cannot initialize command channel");
        return 0;
    };
    if slot.is_some() {
        return 1;
    }
    *slot = Some(command_tx);
    drop(slot);

    let Some(jvm) = JVM.get() else {
        eprintln!("CiphrChat JVM handle was not initialized");
        return 0;
    };
    thread::spawn(move || {
        let Ok(runtime) = Runtime::new() else {
            eprintln!("CiphrChat could not create Tokio runtime");
            return;
        };
        runtime.block_on(async move {
            let network = tokio::spawn(ciphrchat_routing::run_client(
                local_key,
                relay_address,
                command_rx,
                event_tx,
            ));
            while let Some(event) = event_rx.recv().await {
                dispatch_event(jvm, event);
            }
            if let Err(error) = network.await {
                eprintln!("CiphrChat network task failed: {error}");
            }
        });
    });
    1
}

#[no_mangle]
pub extern "system" fn Java_org_ciphrchat_app_transport_internet_RustP2pManager_nativeConnectPeer<
    'local,
>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    peer_id: JString<'local>,
    address: JString<'local>,
) -> jboolean {
    let parsed_peer = jstring(&mut env, peer_id)
        .ok()
        .and_then(|value| value.parse().ok());
    let parsed_address = jstring(&mut env, address)
        .ok()
        .and_then(|value| value.parse().ok());
    match (command_sender(), parsed_peer, parsed_address) {
        (Some(sender), Some(peer_id), Some(address)) => sender
            .send(SwarmCommand::AddPeerAddress { peer_id, address })
            .map(|_| 1)
            .unwrap_or(0),
        _ => 0,
    }
}

#[no_mangle]
pub extern "system" fn Java_org_ciphrchat_app_transport_internet_RustP2pManager_nativeSendMessage<
    'local,
>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    peer_id: JString<'local>,
    payload: JByteArray<'local>,
) -> jboolean {
    let parsed_peer = jstring(&mut env, peer_id)
        .ok()
        .and_then(|value| value.parse().ok());
    let payload = env.convert_byte_array(payload).ok();
    match (command_sender(), parsed_peer, payload) {
        (Some(sender), Some(peer_id), Some(payload)) => sender
            .send(SwarmCommand::Send { peer_id, payload })
            .map(|_| 1)
            .unwrap_or(0),
        _ => 0,
    }
}

#[no_mangle]
pub extern "system" fn Java_org_ciphrchat_app_transport_internet_RustP2pManager_nativeLocalPeerId<
    'local,
>(
    env: JNIEnv<'local>,
    _class: JClass<'local>,
) -> JObject<'local> {
    LOCAL_PEER_ID
        .get()
        .and_then(|peer_id| env.new_string(peer_id).ok())
        .map(JObject::from)
        .unwrap_or_else(|| JObject::null())
}
