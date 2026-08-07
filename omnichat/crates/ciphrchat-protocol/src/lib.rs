use serde::{Deserialize, Serialize};
use uuid::Uuid;

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
pub enum PayloadType {
    Text,
    Invite,
    Acknowledgement,
    Control,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Envelope {
    pub protocol_version: u16,
    pub message_id: Uuid,
    pub recipient_tag: Vec<u8>,
    pub created_at_ms: u64,
    pub expires_at_ms: u64,
    pub hop_limit: u8,
    pub payload_type: PayloadType,
    pub encrypted_payload: Vec<u8>,
    pub signature: Vec<u8>,
    pub padding: Vec<u8>,
}

impl Envelope {
    pub fn validate_structure(&self, now_ms: u64) -> Result<(), ProtocolError> {
        if self.protocol_version != 1 {
            return Err(ProtocolError::UnsupportedVersion(self.protocol_version));
        }
        if self.expires_at_ms <= now_ms {
            return Err(ProtocolError::Expired);
        }
        if self.hop_limit > 7 {
            return Err(ProtocolError::InvalidHopLimit);
        }
        if self.encrypted_payload.len() > 64 * 1024 {
            return Err(ProtocolError::PayloadTooLarge);
        }
        Ok(())
    }
}

#[derive(Debug, thiserror::Error)]
pub enum ProtocolError {
    #[error("unsupported protocol version: {0}")]
    UnsupportedVersion(u16),
    #[error("envelope expired")]
    Expired,
    #[error("invalid hop limit")]
    InvalidHopLimit,
    #[error("payload too large")]
    PayloadTooLarge,
}

#[cfg(test)]
mod tests {
    use super::*;

    fn test_envelope(version: u16, expires: u64, hop: u8, payload_len: usize) -> Envelope {
        Envelope {
            protocol_version: version,
            message_id: Uuid::new_v4(),
            recipient_tag: vec![1, 2, 3],
            created_at_ms: 1000,
            expires_at_ms: expires,
            hop_limit: hop,
            payload_type: PayloadType::Text,
            encrypted_payload: vec![0u8; payload_len],
            signature: vec![],
            padding: vec![],
        }
    }

    #[test]
    fn valid_envelope_passes() {
        let e = test_envelope(1, 5000, 3, 100);
        assert!(e.validate_structure(1000).is_ok());
    }

    #[test]
    fn unsupported_version_fails() {
        let e = test_envelope(2, 5000, 3, 100);
        assert!(matches!(
            e.validate_structure(1000),
            Err(ProtocolError::UnsupportedVersion(2))
        ));
    }

    #[test]
    fn expired_envelope_fails() {
        let e = test_envelope(1, 500, 3, 100);
        assert!(matches!(
            e.validate_structure(1000),
            Err(ProtocolError::Expired)
        ));
    }

    #[test]
    fn invalid_hop_limit_fails() {
        let e = test_envelope(1, 5000, 8, 100);
        assert!(matches!(
            e.validate_structure(1000),
            Err(ProtocolError::InvalidHopLimit)
        ));
    }

    #[test]
    fn oversized_payload_fails() {
        let e = test_envelope(1, 5000, 3, 65 * 1024);
        assert!(matches!(
            e.validate_structure(1000),
            Err(ProtocolError::PayloadTooLarge)
        ));
    }
}
