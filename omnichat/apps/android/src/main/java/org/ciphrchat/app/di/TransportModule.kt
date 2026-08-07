package org.ciphrchat.app.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import org.ciphrchat.app.transport.TransportAdapter
import org.ciphrchat.app.transport.adapters.*
import org.ciphrchat.app.transport.lan.LanTransportAdapter
import org.ciphrchat.app.transport.wifi.WifiDirectTransportAdapter
import org.ciphrchat.app.transport.wifi.WifiAwareTransportAdapter
import org.ciphrchat.app.transport.bluetooth.BluetoothTransportAdapter
import org.ciphrchat.app.transport.bluetooth.BluetoothMeshTransportAdapter
import org.ciphrchat.app.transport.internet.InternetTransportAdapter

@Module
@InstallIn(SingletonComponent::class)
object TransportModule {

    @Provides @IntoSet
    fun internet(adapter: InternetTransportAdapter): TransportAdapter = adapter

    @Provides @IntoSet
    fun wifiLan(adapter: LanTransportAdapter): TransportAdapter = adapter

    @Provides @IntoSet
    fun wifiDirect(adapter: WifiDirectTransportAdapter): TransportAdapter = adapter

    @Provides @IntoSet
    fun wifiAware(adapter: WifiAwareTransportAdapter): TransportAdapter = adapter

    @Provides @IntoSet
    fun bluetooth(adapter: BluetoothTransportAdapter): TransportAdapter = adapter

    @Provides @IntoSet
    fun bluetoothMesh(adapter: BluetoothMeshTransportAdapter): TransportAdapter = adapter

    @Provides @IntoSet
    fun ultrasound(adapter: UltrasoundTransportAdapter): TransportAdapter = adapter

    @Provides @IntoSet
    fun infrared(adapter: InfraredTransportAdapter): TransportAdapter = adapter

    @Provides @IntoSet
    fun nfc(adapter: NfcTransportAdapter): TransportAdapter = adapter

    @Provides @IntoSet
    fun uwb(adapter: UwbTransportAdapter): TransportAdapter = adapter

    @Provides @IntoSet
    fun external(adapter: ExternalTransportAdapter): TransportAdapter = adapter
}
