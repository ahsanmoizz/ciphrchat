use libp2p::{
    core::upgrade,
    identity, noise,
    swarm::{SwarmBuilder, SwarmEvent},
    tcp, yamux, PeerId, Transport,
};
use std::error::Error;
use std::time::Duration;

pub async fn start_swarm() -> Result<(), Box<dyn Error>> {
    let local_key = identity::Keypair::generate_ed25519();
    let local_peer_id = PeerId::from(local_key.public());
    println!("Local peer id: {:?}", local_peer_id);

    let tcp_transport = tcp::tokio::Transport::default()
        .upgrade(upgrade::Version::V1Lazy)
        .authenticate(noise::Config::new(&local_key)?)
        .multiplex(yamux::Config::default())
        .boxed();

    // Use dummy behaviour for now
    let behaviour = libp2p::swarm::dummy::Behaviour;

    let mut swarm = SwarmBuilder::with_tokio_executor(tcp_transport, behaviour, local_peer_id).build();

    swarm.listen_on("/ip4/0.0.0.0/tcp/0".parse()?)?;

    loop {
        match swarm.next().await {
            Some(SwarmEvent::NewListenAddr { address, .. }) => {
                println!("Listening on {:?}", address);
            }
            _ => {}
        }
    }
}
