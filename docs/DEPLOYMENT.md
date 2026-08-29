# Deployment runbook

The relay and coturn TURN server are required infrastructure for Internet messaging, 5 GiB large-file transfer, and audio-only calling. Vercel cannot host them because they are continuously running network processes with inbound TCP/UDP listeners. Use a VPS for the relay; use Vercel only for an optional website.

## 1. Prepare the VPS

Use a Linux VPS with Docker Engine, Docker Compose, an SSH user, and firewall rules allowing:

- TCP `4001` for libp2p relay v2
- UDP `4001` for libp2p QUIC
- UDP/TCP `3478` for Coturn STUN/TURN signaling
- UDP/TCP `5349` for Coturn TLS STUN/TURN
- UDP `49152-49200` for Coturn WebRTC media relay ports
- TCP `18081` for local health/info and file streaming checks; Compose maps it to the container's port `8080` and keeps it on loopback

The SSH user must be able to run Docker and `sudo mkdir`/`sudo chown` as used by the deployment workflow. The workflow checks health/info over SSH, so port `18081` does not need to be publicly reachable.

## 2. Infrastructure Architecture & Storage Quotas

### Persistent Storage (`relay-data` volume at `/var/lib/ciphrchat`)
- `/var/lib/ciphrchat/relay-key.bin`: Ed25519 identity key for the libp2p relay.
- `/var/lib/ciphrchat/mailbox/`: 7-day encrypted message store. Opaque Signal envelopes, max 8 MiB per envelope, max 100 messages / 64 MiB per recipient. Auto-purged on recipient ACK.
- `/var/lib/ciphrchat/files/`: 5 GiB chunked streaming file storage. Max 5 GiB per file, max 50 GiB total server disk quota, 7-day retention. Chunks streamed directly to disk without RAM buffering. Auto-purged on download completion or TTL expiration.

### Coturn TURN Server
- Realm: `ciphrchat.internal`
- Authentication: Long-term credential mechanism (`user=ciphrchat:ciphrchat_turn_secret`).
- Privacy: Enables WebRTC relay-only mode (`IceTransportsType.RELAY`) when "Hide my IP" privacy mode is enabled. Strips host/srflx candidates so caller/callee IP addresses are never exposed to each other.

## 3. Configure GitHub deployment secrets

In the repository, open **Settings → Secrets and variables → Actions → New repository secret** and add:

- `CIPHRCHAT_VPS_HOST` — VPS IP address or DNS name
- `CIPHRCHAT_VPS_USER` — deployment SSH user
- `CIPHRCHAT_VPS_SSH_KEY` — the private key for that user; never commit or paste it into source
- `CIPHRCHAT_VPS_PORT` — optional, defaults to `22`

Open **Actions → Deploy relay to VPS → Run workflow**. The workflow uploads the pinned relay source, builds it with Docker Compose, checks `/health`, and prints `/info` with the persistent relay peer ID.

## 4. Configure the Android Internet address

Construct the relay multiaddress from the workflow summary:

```text
/ip4/<VPS-IP>/tcp/4001/p2p/<RELAY-PEER-ID>
```

If using DNS, use `/dns4/<hostname>/tcp/4001/p2p/<RELAY-PEER-ID>` instead. Add this exact value as the repository **Actions variable** `CIPHRCHAT_RELAY_ADDRESS` under **Settings → Secrets and variables → Actions → Variables**. Do not put it in a user password or in an APK source file.

## 5. Rollback Procedure

If a deployed relay or coturn container fails:
1. SSH to VPS: `ssh -i <key> <user>@<host>`
2. Inspect logs: `cd /opt/ciphrchat-relay && docker compose logs --tail=100`
3. Roll back to previous image or restart container: `docker compose up -d --force-recreate`
4. The persistent volume `relay-data` retains all relay keys and pending mailboxes. Never run `docker compose down -v` or `docker system prune` on production VPS.

## 6. Public release gate

Before distributing a stable release, configure the Android signing secrets documented in the root README, run the tag-triggered Android Release workflow, verify the APK signature and checksum, and complete the restart, recovery, offline queue, duplicate delivery, and key-change test matrix. An independent security review is still required before making audited-security claims.
