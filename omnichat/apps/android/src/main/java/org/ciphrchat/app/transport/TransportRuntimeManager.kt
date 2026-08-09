package org.ciphrchat.app.transport

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/** Keeps every supported transport alive while the application process is alive. */
@Singleton
class TransportRuntimeManager @Inject constructor(
    private val registry: TransportRegistry,
    private val capabilityDetector: AndroidCapabilityDetector
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val startMutex = Mutex()

    fun startAll() {
        scope.launch {
            startMutex.withLock {
                val snapshot = capabilityDetector.refresh()
                registry.all().forEach { adapter ->
                    if (snapshot.assessment(adapter.kind).canStart) {
                        runCatching { adapter.start() }
                    } else {
                        runCatching { adapter.stop() }
                    }
                }
            }
        }
    }
}
