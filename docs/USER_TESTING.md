# CiphrChat user test manual

This is the shortest repeatable test for two Android phones. It separates what
the app can detect automatically from what requires two compatible devices.

## Prepare both phones

1. Install the latest APK on both phones from the download button in the root
   README. Update over the existing installation when an identity already
   exists.
2. Keep both phones charged, unlocked, and within reach. For Internet tests,
   put them on different networks if possible.
3. Open CiphrChat on each phone, create or restore one identity per phone, and
   grant the nearby permissions requested during setup. A denied permission is
   not fatal: the affected method will be marked unavailable and other routes
   can continue.
4. On either phone open **Connect → Show my QR**. On the other phone open
   **Connect → Scan QR**, scan it, and complete pairing. Confirm that the
   contact appears in **Chats**.

## Read the automatic capability scan

Open **Connect** and scroll to **Connection methods**. CiphrChat starts its
transport listeners when the identity is ready and again after a normal app
restart. Each row reports the current device state:

- Green means the method is running and available.
- Amber means a permission is needed or the listener is still starting.
- Gray means the phone does not expose that hardware or the method is not
  connected to a peer.
- Red means the method reported an error; the detail text is the useful
  diagnostic.

The scan is device-specific. One phone can report its own support, but no
nearby method can prove delivery without a second compatible phone.

## Core Internet test

1. With both phones paired, send `internet test 001` in both directions.
2. Confirm the message arrives on the other phone and its status changes from
   queued/routing to sent or delivered.
3. Disable Wi-Fi on one phone and repeat over mobile data.
4. Force-stop and reopen CiphrChat, then send another message. Transport
   listeners should restart automatically.

If Internet says **relay address is not configured**, the APK was built without
the GitHub Actions variable `CIPHRCHAT_RELAY_ADDRESS`; this is a deployment
configuration issue, not a phone permission issue.

## Nearby transport tests

Test only the methods shown as available on both phones. For each test, send a
unique message such as `route-bluetooth-001` and confirm it arrives exactly
once:

| Method | Test setup | Important condition |
| --- | --- | --- |
| Wi-Fi LAN | Same Wi-Fi network | Keep local network discovery allowed |
| Wi-Fi Direct | Phones nearby; Wi-Fi enabled | Android/OEM support varies |
| Wi-Fi Aware | Phones nearby; Wi-Fi enabled | Both phones must support Wi-Fi Aware |
| Bluetooth direct | Bluetooth enabled on both | Nearby Bluetooth permissions granted |
| Bluetooth mesh | Add a third paired phone between the endpoints | Small offline messages; hop limits apply |
| Nearby audio | Phones face each other in a quiet room | Microphone permission; slow and short-message oriented |
| NFC | Touch the NFC areas together | NFC enabled; explicit tap session |
| UWB | Phones close together | UWB verifies proximity; BLE carries the message |
| Infrared | Aim the IR emitter at the other phone's camera | Both phones need the required hardware, camera permission, and line of sight |

Automatic routing chooses a usable route. It does not falsely mark an
unsupported radio as available. For a focused route test, temporarily disable
the other radios and verify the row becomes unavailable or the message falls
back to the next working route.

## Media test

Use **Chats → contact → paperclip** and test these files one at a time:

- a small JPG or PNG image;
- a short audio clip;
- a short MP4 video;
- a PDF or office document;
- an arbitrary file with an uncommon extension.

Each file must be 5 MiB or smaller in this build. The app preserves the file
name and MIME type, encrypts the attachment inside the end-to-end message, and
stores the local copy encrypted. Tap **Open attachment** on the receiving
phone and confirm the correct application opens it and the file is usable.

Large attachments should use Internet, LAN, Wi-Fi Direct, or Wi-Fi Aware. Do
not use NFC, infrared, nearby audio, or mesh as a large-media benchmark; those
routes are intentionally limited by their physical link. A file above the
limit should be rejected with a clear error rather than silently truncated.

## Recovery and restart test

1. In **Settings**, scroll to **Back up identity** and create a password of at
   least 12 characters.
2. Verify that the recovery file is created.
3. Confirm messages still exist after force-stop/reopen.
4. Only test restore on a spare installation or second test phone. Never erase
   the working identity before confirming that the backup password is known.

Record the phone model, Android version, route row text, and exact failure
message for any failed hardware test. This makes an OEM-specific issue
reproducible without repeating the entire matrix.
