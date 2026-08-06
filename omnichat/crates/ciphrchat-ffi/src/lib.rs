/// Returns the current CiphrChat protocol version.
pub fn protocol_version() -> u16 {
    1
}

#[cfg(test)]
mod tests {
    #[test]
    fn protocol_version_is_one() {
        assert_eq!(super::protocol_version(), 1);
    }
}
