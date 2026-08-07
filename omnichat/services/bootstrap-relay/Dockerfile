FROM rust:1.97-bookworm AS builder
WORKDIR /app
COPY . .
RUN cargo build --release --locked

FROM debian:bookworm-slim
RUN useradd --system --uid 10001 --create-home ciphrchat \
    && mkdir -p /var/lib/ciphrchat \
    && chown -R ciphrchat:ciphrchat /var/lib/ciphrchat
COPY --from=builder /app/target/release/ciphrchat-bootstrap-relay /usr/local/bin/
EXPOSE 4001/tcp
EXPOSE 4001/udp
EXPOSE 8080/tcp
VOLUME ["/var/lib/ciphrchat"]
USER ciphrchat
CMD ["ciphrchat-bootstrap-relay"]
