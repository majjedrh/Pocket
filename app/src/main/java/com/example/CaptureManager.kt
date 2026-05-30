package com.example

import kotlinx.coroutines.flow.MutableSharedFlow

data class CapturedPacket(
    val id: Long,
    val time: Long,
    val protocol: String,
    val source: String,
    val destination: String,
    val payload: String
)

object CaptureManager {
    val packetFlow = MutableSharedFlow<CapturedPacket>(replay = 100, extraBufferCapacity = 500)
    var isCapturing = false
    var isProxyMode = false
    
    var targetInput: String = ""
    var targetIp: String = ""
    var targetPort: Int? = null
    
    private var packetCounter = 0L

    suspend fun emitPacket(
        protocol: String,
        source: String,
        destination: String,
        payload: String
    ) {
        packetCounter++
        val packet = CapturedPacket(
            id = packetCounter,
            time = System.currentTimeMillis(),
            protocol = protocol,
            source = source,
            destination = destination,
            payload = payload
        )
        packetFlow.emit(packet)
    }

    fun clearPackets() {
        packetFlow.resetReplayCache()
        packetCounter = 0
    }
}
