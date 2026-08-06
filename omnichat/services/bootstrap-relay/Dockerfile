FROM rust:1.82 AS builder
WORKDIR /app
COPY . .
RUN cargo build --release

FROM debian:bookworm-slim
COPY --from=builder /app/target/release/ciphrchat-bootstrap-relay /usr/local/bin/
EXPOSE 8080
CMD ["ciphrchat-bootstrap-relay"]
