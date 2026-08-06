#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash)]
pub enum TransportKind {
    InternetDirect,
    WifiLan,
    WifiAware,
    WifiDirect,
    BluetoothDirect,
    BluetoothMesh,
    Ultrasound,
    Infrared,
    InternetRelay,
}

pub const DEFAULT_PRIORITY: &[TransportKind] = &[
    TransportKind::InternetDirect,
    TransportKind::WifiLan,
    TransportKind::WifiAware,
    TransportKind::WifiDirect,
    TransportKind::BluetoothDirect,
    TransportKind::BluetoothMesh,
    TransportKind::Ultrasound,
    TransportKind::Infrared,
    TransportKind::InternetRelay,
];

pub fn allowed_for_payload(kind: TransportKind, payload_bytes: usize) -> bool {
    match kind {
        TransportKind::Ultrasound | TransportKind::Infrared => payload_bytes <= 512,
        TransportKind::BluetoothMesh => payload_bytes <= 16 * 1024,
        _ => payload_bytes <= 64 * 1024,
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn priority_has_nine_entries() {
        assert_eq!(DEFAULT_PRIORITY.len(), 9);
    }

    #[test]
    fn internet_is_first_priority() {
        assert_eq!(DEFAULT_PRIORITY[0], TransportKind::InternetDirect);
    }

    #[test]
    fn relay_is_last_priority() {
        assert_eq!(DEFAULT_PRIORITY[8], TransportKind::InternetRelay);
    }

    #[test]
    fn ultrasound_rejects_large_payload() {
        assert!(!allowed_for_payload(TransportKind::Ultrasound, 1024));
    }

    #[test]
    fn ultrasound_accepts_small_payload() {
        assert!(allowed_for_payload(TransportKind::Ultrasound, 256));
    }

    #[test]
    fn internet_accepts_large_payload() {
        assert!(allowed_for_payload(TransportKind::InternetDirect, 60 * 1024));
    }

    #[test]
    fn bluetooth_mesh_rejects_oversized() {
        assert!(!allowed_for_payload(TransportKind::BluetoothMesh, 20 * 1024));
    }
}
