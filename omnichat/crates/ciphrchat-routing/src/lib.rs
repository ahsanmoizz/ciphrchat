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
const MAILBOX_PROTOCOL: &str = "/ciphrchat/mailbox/1";
const MAILBOX_FETCH_LIMIT: u16 = 16;

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct WireMessage {
    pub payload: Vec<u8>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct WireAcknowledgement {
    pub accepted: bool,
}

type MessageBehaviour = cbor::Behaviour<WireMessage, WireAcknowledgement>;

#[derive(Debug, Clone, Serialize, Deserialize)]
pub enum MailboxRequest {
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
pub struct MailboxMessage {
    pub message_id: String,
    pub sender_peer_id: String,
    pub payload: Vec<u8>,
    pub expires_at_epoch_ms: u64,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub enum MailboxResponse {
    Stored,
    Messages(Vec<MailboxMessage>),
    Acknowledged,
    Rejected(String),
}

type MailboxBehaviour = cbor::Behaviour<MailboxRequest, MailboxResponse>;

#[derive(NetworkBehaviour)]
pub struct CiphrChatBehaviour {
    messages: MessageBehaviour,
    mailbox: MailboxBehaviour,
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
        expires_at_epoch_ms: u64,
    },
    AcknowledgeMailbox {
        message_id: String,
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
    RelayConnected {
        relay_peer_id: PeerId,
    },
    InboundMessage {
        peer_id: PeerId,
        payload: Vec<u8>,
    },
    MailboxMessage {
        peer_id: PeerId,
        message_id: String,
        payload: Vec<u8>,
    },
    MailboxReady,
    MailboxUnavailable {
        detail: String,
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

fn mailbox_behaviour() -> MailboxBehaviour {
    cbor::Behaviour::new(
        [(
            StreamProtocol::new(MAILBOX_PROTOCOL),
            ProtocolSupport::Outbound,
        )],
        request_response::Config::default().with_request_timeout(Duration::from_secs(45)),
    )
}

#[derive(Debug)]
struct DeliveryTracker {
    peer_id: PeerId,
    direct_pending: bool,
    mailbox_pending: bool,
    failures: Vec<String>,
}

#[derive(Debug)]
enum PendingMailboxRequest {
    Put { message_id: String },
    Fetch,
    Ack,
}

fn relay_peer_id(address: &Multiaddr) -> Option<PeerId> {
    address.iter().find_map(|protocol| match protocol {
        libp2p::multiaddr::Protocol::P2p(peer_id) => Some(peer_id),
        _ => None,
    })
}

fn complete_delivery(
    message_id: &str,
    accepted: bool,
    reason: Option<String>,
    source_is_mailbox: bool,
    deliveries: &mut HashMap<String, DeliveryTracker>,
    events: &mpsc::UnboundedSender<ClientEvent>,
) {
    let Some(tracker) = deliveries.get_mut(message_id) else {
        return;
    };
    if source_is_mailbox {
        tracker.mailbox_pending = false;
    } else {
        tracker.direct_pending = false;
    }
    if accepted {
        let peer_id = tracker.peer_id;
        deliveries.remove(message_id);
        let _ = events.send(ClientEvent::DeliveryAccepted {
            peer_id,
            message_id: message_id.to_owned(),
        });
        return;
    }
    if let Some(reason) = reason {
        tracker.failures.push(reason);
    }
    if !tracker.direct_pending && !tracker.mailbox_pending {
        let tracker = deliveries
            .remove(message_id)
            .expect("delivery tracker exists");
        let _ = events.send(ClientEvent::DeliveryFailed {
            peer_id: tracker.peer_id,
            message_id: message_id.to_owned(),
            reason: tracker.failures.join("; "),
        });
    }
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
    let relay_peer_id =
        relay_peer_id(&relay_address).ok_or("relay address must contain /p2p/<relay-peer-id>")?;

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
            mailbox: mailbox_behaviour(),
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
    swarm.add_peer_address(relay_peer_id, relay_address.clone());
    swarm.dial(relay_address)?;

    let mut pending_direct: HashMap<request_response::OutboundRequestId, String> = HashMap::new();
    let mut pending_mailbox: HashMap<request_response::OutboundRequestId, PendingMailboxRequest> =
        HashMap::new();
    let mut deliveries: HashMap<String, DeliveryTracker> = HashMap::new();
    let mut mailbox_fetch_pending = false;
    let mut mailbox_poll = tokio::time::interval(Duration::from_secs(10));
    mailbox_poll.set_missed_tick_behavior(tokio::time::MissedTickBehavior::Delay);
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
                Some(SwarmCommand::Send { peer_id, message_id, payload, expires_at_epoch_ms }) => {
                    if deliveries.contains_key(&message_id) {
                        continue;
                    }
                    let direct_request_id = swarm.behaviour_mut().messages.send_request(
                        &peer_id,
                        WireMessage { payload: payload.clone() },
                    );
                    pending_direct.insert(direct_request_id, message_id.clone());
                    let mailbox_request_id = swarm.behaviour_mut().mailbox.send_request(
                        &relay_peer_id,
                        MailboxRequest::Put {
                            recipient_peer_id: peer_id.to_string(),
                            message_id: message_id.clone(),
                            payload,
                            expires_at_epoch_ms,
                        },
                    );
                    pending_mailbox.insert(mailbox_request_id, PendingMailboxRequest::Put { message_id: message_id.clone() });
                    deliveries.insert(message_id, DeliveryTracker {
                        peer_id,
                        direct_pending: true,
                        mailbox_pending: true,
                        failures: Vec::new(),
                    });
                }
                Some(SwarmCommand::AcknowledgeMailbox { message_id }) => {
                    let request_id = swarm.behaviour_mut().mailbox.send_request(
                        &relay_peer_id,
                        MailboxRequest::Ack { message_id },
                    );
                    pending_mailbox.insert(request_id, PendingMailboxRequest::Ack);
                }
                None => break,
            },
            _ = mailbox_poll.tick(), if !mailbox_fetch_pending => {
                let request_id = swarm.behaviour_mut().mailbox.send_request(
                    &relay_peer_id,
                    MailboxRequest::Fetch { limit: MAILBOX_FETCH_LIMIT },
                );
                pending_mailbox.insert(request_id, PendingMailboxRequest::Fetch);
                mailbox_fetch_pending = true;
            }
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
                                if let Some(message_id) = pending_direct.remove(&request_id) {
                                    complete_delivery(
                                        &message_id,
                                        response.accepted,
                                        (!response.accepted).then(|| "remote peer rejected message".to_owned()),
                                        false,
                                        &mut deliveries,
                                        &events,
                                    );
                                }
                            }
                        },
                        request_response::Event::OutboundFailure { peer, request_id, error } => {
                            if let Some(message_id) = pending_direct.remove(&request_id) {
                                complete_delivery(
                                    &message_id,
                                    false,
                                    Some(format!("direct delivery to {peer} failed: {error}")),
                                    false,
                                    &mut deliveries,
                                    &events,
                                );
                            }
                        }
                        request_response::Event::InboundFailure { peer, error, .. } => {
                            let _ = events.send(ClientEvent::NetworkError {
                                detail: format!("inbound request from {peer} failed: {error}"),
                            });
                        }
                        request_response::Event::ResponseSent { .. } => {}
                    }
                }
                Some(SwarmEvent::Behaviour(CiphrChatBehaviourEvent::Mailbox(event))) => {
                    match event {
                        request_response::Event::Message { message, .. } => match message {
                            Message::Response { request_id, response } => {
                                if let Some(pending_request) = pending_mailbox.remove(&request_id) {
                                    match pending_request {
                                        PendingMailboxRequest::Put { message_id } => match response {
                                            MailboxResponse::Stored => {
                                                let _ = events.send(ClientEvent::MailboxReady);
                                                complete_delivery(
                                                    &message_id, true, None, true, &mut deliveries, &events,
                                                )
                                            }
                                            MailboxResponse::Rejected(reason) => {
                                                let detail = format!("mailbox rejected message: {reason}");
                                                let _ = events.send(ClientEvent::MailboxUnavailable { detail: detail.clone() });
                                                complete_delivery(
                                                    &message_id, false, Some(detail), true, &mut deliveries, &events,
                                                )
                                            }
                                            _ => complete_delivery(
                                                &message_id, false, Some("mailbox returned an invalid response".to_owned()), true, &mut deliveries, &events,
                                            ),
                                        },
                                        PendingMailboxRequest::Fetch => {
                                            mailbox_fetch_pending = false;
                                            match response {
                                                MailboxResponse::Messages(messages) => {
                                                    let _ = events.send(ClientEvent::MailboxReady);
                                                    for message in messages {
                                                        match message.sender_peer_id.parse::<PeerId>() {
                                                            Ok(peer_id) => {
                                                                let _ = events.send(ClientEvent::MailboxMessage {
                                                                    peer_id,
                                                                    message_id: message.message_id,
                                                                    payload: message.payload,
                                                                });
                                                            }
                                                            Err(error) => {
                                                                let _ = events.send(ClientEvent::NetworkError {
                                                                    detail: format!("mailbox returned an invalid sender peer id: {error}"),
                                                                });
                                                            }
                                                        }
                                                    }
                                                }
                                                MailboxResponse::Rejected(reason) => {
                                                    let _ = events.send(ClientEvent::MailboxUnavailable {
                                                        detail: format!("mailbox fetch rejected: {reason}"),
                                                    });
                                                    let _ = events.send(ClientEvent::NetworkError { detail: format!("mailbox fetch rejected: {reason}") });
                                                }
                                                _ => {
                                                    let _ = events.send(ClientEvent::NetworkError { detail: "mailbox returned an invalid fetch response".to_owned() });
                                                }
                                            }
                                        }
                                        PendingMailboxRequest::Ack => {}
                                    }
                                }
                            }
                            Message::Request { .. } => {}
                        },
                        request_response::Event::OutboundFailure { request_id, error, .. } => {
                            if let Some(pending_request) = pending_mailbox.remove(&request_id) {
                                match pending_request {
                                    PendingMailboxRequest::Put { message_id } => complete_delivery(
                                        &message_id,
                                        false,
                                        Some(format!("encrypted mailbox unavailable: {error}")),
                                        true,
                                        &mut deliveries,
                                        &events,
                                    ),
                                    PendingMailboxRequest::Fetch => {
                                        mailbox_fetch_pending = false;
                                        let _ = events.send(ClientEvent::MailboxUnavailable {
                                            detail: format!("encrypted mailbox unavailable: {error}"),
                                        });
                                    }
                                    PendingMailboxRequest::Ack => {}
                                }
                            }
                        }
                        request_response::Event::InboundFailure { .. }
                        | request_response::Event::ResponseSent { .. } => {}
                    }
                }
                Some(SwarmEvent::Behaviour(CiphrChatBehaviourEvent::RelayClient(event))) => {
                    if let relay::client::Event::ReservationReqAccepted { relay_peer_id, .. } = event {
                        let _ = events.send(ClientEvent::RelayReservationReady { relay_peer_id });
                    }
                }
                Some(SwarmEvent::ConnectionEstablished { peer_id, .. }) if peer_id == relay_peer_id => {
                    let _ = events.send(ClientEvent::RelayConnected { relay_peer_id: peer_id });
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
                Some(SwarmEvent::ListenerError { listener_id, error }) => {
                    let _ = events.send(ClientEvent::NetworkError {
                        detail: format!("relay listener {listener_id} failed: {error}"),
                    });
                }
                Some(SwarmEvent::ListenerClosed { listener_id, reason, .. }) => {
                    let _ = events.send(ClientEvent::NetworkError {
                        detail: format!("relay listener {listener_id} closed: {reason:?}"),
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
