package org.ciphrchat.app.transport.nfc

import android.nfc.cardemulation.HostApduService
import android.os.Bundle
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class CiphrChatHostApduService : HostApduService() {
    @Inject lateinit var coordinator: NfcTransportCoordinator

    override fun processCommandApdu(commandApdu: ByteArray, extras: Bundle?): ByteArray =
        if (commandApdu.size >= 5 && commandApdu[1] == 0xA4.toByte()) {
            val aidLength = commandApdu[4].toInt() and 0xFF
            val aid = commandApdu.copyOfRange(5, minOf(5 + aidLength, commandApdu.size))
            if (aid.contentEquals(NfcTransportCoordinator.APPLICATION_ID)) byteArrayOf(0x90.toByte(), 0x00)
            else byteArrayOf(0x6A.toByte(), 0x82.toByte())
        } else {
            coordinator.handleApdu(commandApdu)
        }

    override fun onDeactivated(reason: Int) = Unit
}
