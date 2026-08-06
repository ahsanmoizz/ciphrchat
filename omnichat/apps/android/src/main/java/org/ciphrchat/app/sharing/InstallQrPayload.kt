package org.ciphrchat.app.sharing

data class InstallQrPayload(
    val type: String = "ciphrchat.install",
    val url: String,
    val checksum: String? = null
)
