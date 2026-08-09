package org.ciphrchat.app.transport.bluetooth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BluetoothSignalPolicyTest {
    @Test
    fun classifiesRawRssiWithoutPretendingItIsDistance() {
        assertEquals("very strong", BluetoothSignalPolicy.label(-45))
        assertEquals("strong", BluetoothSignalPolicy.label(-60))
        assertEquals("good", BluetoothSignalPolicy.label(-70))
        assertEquals("weak", BluetoothSignalPolicy.label(-80))
        assertEquals("very weak", BluetoothSignalPolicy.label(-95))
        assertTrue(BluetoothSignalPolicy.detail(-62).contains("approximate"))
    }
}
