use libp2p::{
    dcutr, identify, identity, noise, relay,
    request_response::{self, cbor, Message, ProtocolSupport},
    swarm::{NetworkBehaviour, StreamProtocol, SwarmEvent},
    tcp, yamux, Multiaddr, PeerId, SwarmBuilder,
};
use serde::{Deserialize, Serialize};
use std::{collections::HashMap, error::Error, time::Duration};
use tokio::sync::mpsc;

const MESSAGE_PROTOCOL: &str = "/ciphrchat/message/1";

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct WireMessage {
    pub payload: Vec<u8>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct WireAcknowledgement {
    pub accepted: bool,
}

type MessageBehaviour = cbor::Behaviour<WireMessage, WireAcknowledgement>;

#[derive(NetworkBehaviour)]
pub struct CiphrChatBehaviour {
    messages: MessageBehaviour,
    relay_client: relay::client::Behaviour,
    dcutr: dcutr::Behaviour,
    identify: identify::Behaviour,
}

pub enum SwarmCommand {
    AddPeerAddress {
        peer_id: PeerId,
        address: Multiaddr,
    },
    Send {
        peer_id: PeerId,
        message_id: String,
        payload: Vec<u8>,
    },
}

#[derive(Debug)]
pub enum ClientEvent {
    Ready {
        peer_id: PeerId,
    },
    RelayReservationReady {
        relay_peer_id: PeerId,
    },
    InboundMessage {
        peer_id: PeerId,
        payload: Vec<u8>,
    },
    DeliveryAccepted {
        peer_id: PeerId,
        message_id: String,
    },
    DeliveryFailed {
        peer_id: PeerId,
        message_id: String,
        reason: String,
    },
    NetworkError {
        detail: String,
    },
}

fn message_behaviour() -> MessageBehaviour {
    cbor::Behaviour::new(
        [(StreamProtocol::new(MESSAGE_PROTOCOL), ProtocolSupport::Full)],
        request_response::Config::default().with_request_timeout(Duration::from_secs(30)),
    )
}

/// Invitations carry the relay's base address. A message dial must target the
/// recipient's circuit address on that relay, not the relay itself.
fn recipient_relay_address(address: Multiaddr, peer_id: PeerId) -> Multiaddr {
    if address
        .iter()
        .any(|protocol| matches!(protocol, libp2p::multiaddr::Protocol::P2pCircuit))
    {
        address
    } else {
        address
            .with(libp2p::multiaddr::Protocol::P2pCircuit)
            .with(libp2p::multiaddr::Protocol::P2p(peer_id))
    }
}

pub async fn run_client(
    local_key: identity::Keypair,
    relay_address: Multiaddr,
    mut commands: mpsc::UnboundedReceiver<SwarmCommand>,
    events: mpsc::UnboundedSender<ClientEvent>,
) -> Result<(), Box<dyn Error + Send + Sync>> {
    let local_peer_id = PeerId::from(local_key.public());
    let has_relay_peer_id = relay_address
        .iter()
        .any(|protocol| matches!(protocol, libp2p::multiaddr::Protocol::P2p(_)));
    if !has_relay_peer_id {
        return Err("relay address must contain /p2p/<relay-peer-id>".into());
    }

    let mut swarm = SwarmBuilder::with_existing_identity(local_key.clone())
        .with_tokio()
        .with_tcp(
            tcp::Config::default().nodelay(true),
            noise::Config::new,
            yamux::Config::default,
        )?
        .with_quic()
        .with_relay_client(noise::Config::new, yamux::Config::default)?
        .with_behaviour(|key, relay_client| CiphrChatBehaviour {
            messages: message_behaviour(),
            relay_client,
            dcutr: dcutr::Behaviour::new(key.public().to_peer_id()),
            identify: identify::Behaviour::new(identify::Config::new(
                "/ciphrchat/1.0.0".to_owned(),
                key.public(),
            )),
        })?
        .with_swarm_config(|config| config.with_idle_connection_timeout(Duration::from_secs(90)))
        .build();

    let relay_listener = relay_address
        .clone()
        .with(libp2p::multiaddr::Protocol::P2pCircuit);
    swarm.listen_on(relay_listener)?;
    swarm.dial(relay_address)?;

    let mut pending: HashMap<request_response::OutboundRequestId, (PeerId, String)> =
        HashMap::new();
    let _ = events.send(ClientEvent::Ready {
        peer_id: local_peer_id,
    });

    loop {
        tokio::select! {
            command = commands.recv() => match command {
                Some(SwarmCommand::AddPeerAddress { peer_id, address }) => {
                    let recipient_address = recipient_relay_address(address, peer_id);
                    swarm.add_peer_address(peer_id, recipient_address.clone());
                    if let Err(error) = swarm.dial(recipient_address) {
                        let _ = events.send(ClientEvent::DeliveryFailed {
                            peer_id,
                            message_id: String::new(),
                            reason: format!("dial failed: {error}"),
                        });
                    }
                }
                Some(SwarmCommand::Send { peer_id, message_id, payload }) => {
                    let request_id = swarm.behaviour_mut().messages.send_request(
                        &peer_id,
                        WireMessage { payload },
                    );
                    pending.insert(request_id, (peer_id, message_id));
                }
                None => break,
            },
            event = futures::StreamExt::next(&mut swarm) => match event {
                Some(SwarmEvent::Behaviour(CiphrChatBehaviourEvent::Messages(event))) => {
                    match event {
                        request_response::Event::Message { peer, message } => match message {
                            Message::Request { request, channel, .. } => {
                                let _ = swarm.behaviour_mut().messages.send_response(
                                    channel,
                                    WireAcknowledgement { accepted: true },
                                );
                                let _ = events.send(ClientEvent::InboundMessage {
                                    peer_id: peer,
                                    payload: request.payload,
                                });
                            }
                            Message::Response { request_id, response } => {
                                if let Some((peer_id, message_id)) = pending.remove(&request_id) {
                                    if response.accepted {
                                        let _ = events.send(ClientEvent::DeliveryAccepted { peer_id, message_id });
                                    } else {
                                        let _ = events.send(ClientEvent::DeliveryFailed {
                                            peer_id,
                                            message_id,
                                            reason: "remote peer rejected message".to_owned(),
                                        });
                                    }
                                }
                            }
                        },
                        request_response::Event::OutboundFailure { peer, request_id, error } => {
                            let message_id = pending.remove(&request_id).map(|(_, message_id)| message_id).unwrap_or_default();
                            let _ = events.send(ClientEvent::DeliveryFailed {
                                peer_id: peer,
                                message_id,
                                reason: error.to_string(),
                            });
                        }
                        request_response::Event::InboundFailure { peer, error, .. } => {
                            let _ = events.send(ClientEvent::NetworkError {
                                detail: format!("inbound request from {peer} failed: {error}"),
                            });
                        }
                        request_response::Event::ResponseSent { .. } => {}
                    }
                }
                Some(SwarmEvent::Behaviour(CiphrChatBehaviourEvent::RelayClient(event))) => {
                    if let relay::client::Event::ReservationReqAccepted { relay_peer_id, .. } = event {
                        let _ = events.send(ClientEvent::RelayReservationReady { relay_peer_id });
                    }
                }
                Some(SwarmEvent::OutgoingConnectionError { peer_id, error, .. }) => {
                    let detail = match peer_id {
                        Some(peer_id) => format!("connection to {peer_id} failed: {error}"),
                        None => format!("outgoing connection failed: {error}"),
                    };
                    let _ = events.send(ClientEvent::NetworkError { detail });
                }
                Some(SwarmEvent::IncomingConnectionError { error, .. }) => {
                    let _ = events.send(ClientEvent::NetworkError {
                        detail: format!("incoming connection failed: {error}"),
                    });
                }
                Some(_) => {}
                None => break,
            },
        }
    }

    Ok(())
}

/// Compatibility entry point for the standalone binary. Android uses `run_client` through JNI.
pub async fn start_swarm() -> Result<(), Box<dyn Error + Send + Sync>> {
    let relay_address: Multiaddr = std::env::var("CIPHRCHAT_RELAY_ADDRESS")?.parse()?;
    let (_command_tx, command_rx) = mpsc::unbounded_channel();
    let (event_tx, mut event_rx) = mpsc::unbounded_channel();
    tokio::spawn(async move {
        while let Some(event) = event_rx.recv().await {
            eprintln!("CiphrChat network event: {event:?}");
        }
    });
    run_client(
        identity::Keypair::generate_ed25519(),
        relay_address,
        command_rx,
        event_tx,
    )
    .await
}

#[cfg(test)]
mod tests {
    use super::recipient_relay_address;
    use libp2p::{identity, Multiaddr, PeerId};

    #[test]
    fn invitation_relay_address_is_expanded_to_recipient_circuit_route() {
        let relay_peer_id = PeerId::from(identity::Keypair::generate_ed25519().public());
        let relay: Multiaddr = format!("/ip4/127.0.0.1/tcp/4001/p2p/{relay_peer_id}")
            .parse()
            .unwrap();
        let peer_id = PeerId::from(identity::Keypair::generate_ed25519().public());

        let route = recipient_relay_address(relay, peer_id);

        assert!(route
            .iter()
            .any(|protocol| { matches!(protocol, libp2p::multiaddr::Protocol::P2pCircuit) }));
        assert!(route.iter().any(|protocol| {
            matches!(protocol, libp2p::multiaddr::Protocol::P2p(candidate) if candidate == peer_id)
        }));
    }

    #[test]
    fn existing_recipient_circuit_route_is_not_modified() {
        let peer_id = PeerId::from(identity::Keypair::generate_ed25519().public());
        let relay_peer_id = PeerId::from(identity::Keypair::generate_ed25519().public());
        let relay: Multiaddr = format!("/ip4/127.0.0.1/tcp/4001/p2p/{relay_peer_id}")
            .parse()
            .unwrap();
        let existing = relay
            .clone()
            .with(libp2p::multiaddr::Protocol::P2pCircuit)
            .with(libp2p::multiaddr::Protocol::P2p(peer_id));

        assert_eq!(recipient_relay_address(existing.clone(), peer_id), existing);
    }
}
