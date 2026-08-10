use ciphrchat_routing::{run_client, ClientEvent, SwarmCommand};
use libp2p::{identity, Multiaddr, PeerId};
use std::time::{Duration, SystemTime, UNIX_EPOCH};
use tokio::sync::mpsc;

async fn wait_for_mailbox(events: &mut mpsc::UnboundedReceiver<ClientEvent>) {
    tokio::time::timeout(Duration::from_secs(60), async {
        while let Some(event) = events.recv().await {
            eprintln!("probe network event: {event:?}");
            match event {
                ClientEvent::MailboxReady => return,
                ClientEvent::MailboxUnavailable { detail } => {
                    panic!("mailbox reported a false startup failure: {detail}")
                }
                _ => {}
            }
        }
        panic!("network event stream ended before mailbox readiness");
    })
    .await
    .expect("mailbox readiness timed out");
}

#[tokio::test]
#[ignore = "requires CIPHRCHAT_RELAY_ADDRESS and a deployed relay"]
async fn offline_recipient_fetches_and_acknowledges_encrypted_mail() {
    let relay_address: Multiaddr = std::env::var("CIPHRCHAT_RELAY_ADDRESS")
        .expect("CIPHRCHAT_RELAY_ADDRESS is required")
        .parse()
        .expect("relay address must be a multiaddress");
    let sender_key = identity::Keypair::generate_ed25519();
    let recipient_key = identity::Keypair::generate_ed25519();
    let recipient_peer_id = PeerId::from(recipient_key.public());
    let message_id = format!(
        "live-probe-{}",
        SystemTime::now()
            .duration_since(UNIX_EPOCH)
            .unwrap()
            .as_millis()
    );
    let payload = b"opaque-e2e-ciphertext-probe".to_vec();

    let (sender_commands, sender_command_rx) = mpsc::unbounded_channel();
    let (sender_events, mut sender_event_rx) = mpsc::unbounded_channel();
    let sender_task = tokio::spawn(run_client(
        sender_key,
        relay_address.clone(),
        sender_command_rx,
        sender_events,
    ));
    wait_for_mailbox(&mut sender_event_rx).await;
    sender_commands
        .send(SwarmCommand::AddPeerAddress {
            peer_id: recipient_peer_id,
            address: relay_address.clone(),
        })
        .unwrap();
    sender_commands
        .send(SwarmCommand::Send {
            peer_id: recipient_peer_id,
            message_id: message_id.clone(),
            payload: payload.clone(),
            expires_at_epoch_ms: SystemTime::now()
                .duration_since(UNIX_EPOCH)
                .unwrap()
                .as_millis() as u64
                + 60_000,
        })
        .unwrap();

    tokio::time::timeout(Duration::from_secs(45), async {
        while let Some(event) = sender_event_rx.recv().await {
            match event {
                ClientEvent::DeliveryAccepted {
                    message_id: accepted,
                    ..
                } if accepted == message_id => return,
                ClientEvent::DeliveryFailed {
                    message_id: failed,
                    reason,
                    ..
                } if failed == message_id => panic!("mailbox rejected probe: {reason}"),
                _ => {}
            }
        }
        panic!("sender event stream ended before mailbox acceptance");
    })
    .await
    .expect("mailbox acceptance timed out");

    let (recipient_commands, recipient_command_rx) = mpsc::unbounded_channel();
    let (recipient_events, mut recipient_event_rx) = mpsc::unbounded_channel();
    let recipient_task = tokio::spawn(run_client(
        recipient_key,
        relay_address,
        recipient_command_rx,
        recipient_events,
    ));
    wait_for_mailbox(&mut recipient_event_rx).await;

    tokio::time::timeout(Duration::from_secs(30), async {
        while let Some(event) = recipient_event_rx.recv().await {
            if let ClientEvent::MailboxMessage {
                message_id: received_id,
                payload: received_payload,
                ..
            } = event
            {
                if received_id == message_id {
                    assert_eq!(received_payload, payload);
                    recipient_commands
                        .send(SwarmCommand::AcknowledgeMailbox {
                            message_id: received_id,
                        })
                        .unwrap();
                    return;
                }
            }
        }
        panic!("recipient event stream ended before mailbox delivery");
    })
    .await
    .expect("offline mailbox fetch timed out");

    sender_task.abort();
    recipient_task.abort();
}
