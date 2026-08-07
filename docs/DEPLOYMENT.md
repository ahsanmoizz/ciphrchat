# Deployment runbook

The relay is a required part of Internet messaging. Vercel cannot host it because the relay is a continuously running libp2p process with inbound TCP/UDP listeners. Use a VPS for the relay; use Vercel only for an optional website.

## 1. Prepare the VPS

Use a Linux VPS with Docker Engine, Docker Compose, an SSH user, and firewall rules allowing:

- TCP `4001` for libp2p
- UDP `4001` for QUIC
- TCP `18081` for local health/info checks; Compose maps it to the container's port `8080` and keeps it on loopback

The SSH user must be able to run Docker and `sudo mkdir`/`sudo chown` as used by the deployment workflow. The workflow checks health/info over SSH, so port `18081` does not need to be publicly reachable.

## 2. Configure GitHub deployment secrets

In the repository, open **Settings → Secrets and variables → Actions → New repository secret** and add:

- `CIPHRCHAT_VPS_HOST` — VPS IP address or DNS name
- `CIPHRCHAT_VPS_USER` — deployment SSH user
- `CIPHRCHAT_VPS_SSH_KEY` — the private key for that user; never commit or paste it into source
- `CIPHRCHAT_VPS_PORT` — optional, defaults to `22`

Open **Actions → Deploy relay to VPS → Run workflow**. The workflow uploads the pinned relay source, builds it with Docker Compose, checks `/health`, and prints `/info` with the persistent relay peer ID.

## 3. Configure the Android Internet address

Construct the relay multiaddress from the workflow summary:

```text
/ip4/<VPS-IP>/tcp/4001/p2p/<RELAY-PEER-ID>
```

If using DNS, use `/dns4/<hostname>/tcp/4001/p2p/<RELAY-PEER-ID>` instead. Add this exact value as the repository **Actions variable** `CIPHRCHAT_RELAY_ADDRESS` under **Settings → Secrets and variables → Actions → Variables**. Do not put it in a user password or in an APK source file.

After the variable is set, push a change or run Android CI manually. The resulting APK will advertise Internet transport as configured; without this variable it correctly remains unavailable.

## 4. User installation and first use

Download `android-debug.apk` from the latest green Android CI run or from the `latest` release link in the root README. Install it on two Android devices, create one identity on each, grant nearby-device permissions when requested, then exchange invitations through **Connect → Show my QR / Scan QR**. Internet delivery is not release-proven until this two-device test succeeds across separate networks.

## 5. Public release gate

Before distributing a stable release, configure the Android signing secrets documented in the root README, run the tag-triggered Android Release workflow, verify the APK signature and checksum, and complete the restart, recovery, offline queue, duplicate delivery, and key-change test matrix. An independent security review is still required before making audited-security claims.
