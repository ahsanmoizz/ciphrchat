use axum::{routing::get, Json, Router};
use libp2p::{
    identify, identity, ping, relay,
    swarm::{NetworkBehaviour, SwarmEvent},
    Multiaddr, PeerId, SwarmBuilder,
};
use serde::Serialize;
#[cfg(unix)]
use std::os::unix::fs::{OpenOptionsExt, PermissionsExt};
use std::{
    env, fs,
    fs::OpenOptions,
    io::Write,
    net::SocketAddr,
    path::{Path, PathBuf},
    time::Duration,
};

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
    identify: identify::Behaviour,
    ping: ping::Behaviour,
}

fn relay_key_path() -> PathBuf {
    env::var_os("CIPHRCHAT_RELAY_KEY_FILE")
        .map(PathBuf::from)
        .unwrap_or_else(|| PathBuf::from("/var/lib/ciphrchat/relay-key.bin"))
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
            SwarmEvent::IncomingConnection { .. }
            | SwarmEvent::ConnectionEstablished { .. }
            | SwarmEvent::ConnectionClosed { .. }
            | SwarmEvent::Behaviour(_) => {}
            _ => {}
        }
    }

    Ok(())
}
