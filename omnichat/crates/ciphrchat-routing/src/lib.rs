use libp2p::{
    core::upgrade,
    identity, noise,
    swarm::{SwarmBuilder, SwarmEvent, NetworkBehaviour},
    tcp, yamux, PeerId, Transport,
    kad::{store::MemoryStore, Kademlia},
    autonat,
    relay,
    dcutr,
    rendezvous,
    identify,
    Multiaddr,
    futures::StreamExt,
};
use std::error::Error;
use std::time::Duration;

#[derive(NetworkBehaviour)]
pub struct CiphrChatBehaviour {
    kademlia: Kademlia<MemoryStore>,
    autonat: autonat::Behaviour,
    relay_client: relay::client::Behaviour,
    dcutr: dcutr::Behaviour,
    rendezvous: rendezvous::client::Behaviour,
    identify: identify::Behaviour,
}

pub async fn start_swarm() -> Result<(), Box<dyn Error>> {
    let local_key = identity::Keypair::generate_ed25519();
    let local_peer_id = PeerId::from(local_key.public());
    println!("Local peer id: {:?}", local_peer_id);

    let tcp_transport = tcp::tokio::Transport::default()
        .upgrade(upgrade::Version::V1Lazy)
        .authenticate(noise::Config::new(&local_key)?)
        .multiplex(yamux::Config::default())
        .boxed();

    let (relay_transport, relay_client) = relay::client::new(local_peer_id);
    let transport = relay_transport
        .or_transport(tcp_transport)
        .map(|fut, _| fut)
        .boxed();

    let behaviour = CiphrChatBehaviour {
        kademlia: Kademlia::new(local_peer_id, MemoryStore::new(local_peer_id)),
        autonat: autonat::Behaviour::new(local_peer_id, autonat::Config::default()),
        relay_client,
        dcutr: dcutr::Behaviour::new(local_peer_id),
        rendezvous: rendezvous::client::Behaviour::new(local_key.clone()),
        identify: identify::Behaviour::new(identify::Config::new("/ciphrchat/1.0.0".into(), local_key.public())),
    };

    let mut swarm = SwarmBuilder::with_tokio_executor(transport, behaviour, local_peer_id).build();

    swarm.listen_on("/ip4/0.0.0.0/tcp/0".parse()?)?;

    // Dial a bootstrap node
    let bootstrap_node: Multiaddr = "/dnsaddr/bootstrap.libp2p.io/p2p/QmNnooDu7bfjPFoTZYxMNLWUQJyrVwtbZg5gBMjTezGAJN".parse()?;
    let bootstrap_peer: PeerId = "QmNnooDu7bfjPFoTZYxMNLWUQJyrVwtbZg5gBMjTezGAJN".parse()?;
    
    swarm.behaviour_mut().kademlia.add_address(&bootstrap_peer, bootstrap_node.clone());
    swarm.dial(bootstrap_node)?;
    swarm.behaviour_mut().kademlia.bootstrap()?;

    loop {
        match swarm.next().await {
            Some(SwarmEvent::NewListenAddr { address, .. }) => {
                println!("Listening on {:?}", address);
            }
            Some(SwarmEvent::Behaviour(CiphrChatBehaviourEvent::Identify(identify::Event::Received { info, peer_id }))) => {
                for addr in info.listen_addrs {
                    swarm.behaviour_mut().kademlia.add_address(&peer_id, addr);
                }
            }
            _ => {}
        }
    }
}
