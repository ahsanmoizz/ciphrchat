# CiphrChat transport contract

CiphrChat uses one authenticated envelope for every connection. The message
payload remains end-to-end encrypted; a transport only carries the envelope
and never decides whether a message belongs to a device.

Attachments are selected with Android's document picker, retain their MIME
type and filename, are encrypted inside the Signal session, and are encrypted
again in the app's private local attachment store. The current per-attachment
limit is 512 KiB. Large-payload routes can carry these attachments; short-range
audio, NFC, and mesh routes are deliberately rejected for oversized payloads.

| Connection | Production behavior | Scope |
| --- | --- | --- |
| Internet direct / relay | Sends and receives normal messages through the VPS relay when a direct path is unavailable. | Full message sizes supported by the wire limits |
| Wi-Fi LAN / Wi-Fi Direct | Authenticated framed envelope over a local socket. | Full message sizes supported by the wire limits |
| Wi-Fi Aware | Publisher/subscriber network path with authenticated framed envelopes. | Nearby message transport on compatible Android devices |
| Bluetooth direct | BLE GATT server/client with acknowledged, length-bounded chunks. | Nearby messages; small payloads are preferred |
| Bluetooth mesh | Bounded flood forwarding with hop limits and duplicate suppression. | Offline multi-hop small messages |
| Nearby audio | FSK carrier with synchronization, CRC, Reed–Solomon correction, and microphone demodulation. | Short encrypted messages; broadcast and slower than radio |
| NFC | Reader Mode plus Host Card Emulation APDU transfer when phones are tapped together. | Explicit tap-based store-and-forward messages |
| UWB | Secure UWB ranging session negotiated over BLE GATT; the verified proximity gate then authorizes the same authenticated BLE envelope path. | Nearby delivery on compatible UWB devices; UWB itself carries ranging, not message bytes |
| Infrared | ConsumerIrManager transmitter plus CameraX optical receiver using framed, CRC-checked OOK symbols. | Small nearby messages when both phones have an IR emitter, camera permission, and line of sight |

## Routing rules

Automatic routing uses only transports that can actually deliver an envelope.
UWB is included only after a ranging session verifies the peer; BLE carries the
encrypted message bytes. Infrared is included only when the camera receiver is
attached and the device exposes an IR emitter. NFC is an explicit tap transfer
and must not block ordinary Internet delivery while it waits for a physical tap.

The relay address is a libp2p endpoint, not an HTTPS URL. Libp2p's secure
session protects the message transport; HTTPS is appropriate for a health or
administration page, not as a proxy in front of the raw relay port.

## Hardware expectations

Nearby paths require two compatible Android devices for a real delivery test.
One phone can validate permissions and capability detection, but it cannot prove
end-to-end delivery to another identity. Wi-Fi Aware, UWB, NFC, infrared, and
microphone behavior also depends on the physical radios and Android OEM policy.
