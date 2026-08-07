#[tokio::main]
async fn main() -> Result<(), Box<dyn std::error::Error>> {
    println!("Starting CiphrChat Relay Server...");
    ciphrchat_routing::start_swarm().await?;
    Ok(())
}
