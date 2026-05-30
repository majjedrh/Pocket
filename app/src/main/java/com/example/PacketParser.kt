package com.example

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

object PacketParser {

    fun parseAndEmit(packet: ByteArray, length: Int) {
        if (length < 20) return // Minimum IPv4 header

        val version = (packet[0].toInt() shr 4) and 0x0F
        if (version != 4) return // Only supporting IPv4 parsing for simplicity here

        val ihl = packet[0].toInt() and 0x0F
        val ipHeaderLength = ihl * 4

        val protocol = packet[9].toInt() and 0xFF
        val srcIp = "${packet[12].toUByte()}.${packet[13].toUByte()}.${packet[14].toUByte()}.${packet[15].toUByte()}"
        val destIp = "${packet[16].toUByte()}.${packet[17].toUByte()}.${packet[18].toUByte()}.${packet[19].toUByte()}"

        var payloadOffset = ipHeaderLength
        var protocolName = "Unknown"

        if (protocol == 6) { // TCP
            protocolName = "TCP"
            if (length >= ipHeaderLength + 20) {
                val dataOffset = (packet[ipHeaderLength + 12].toInt() shr 4) and 0x0F
                payloadOffset += (dataOffset * 4)
            }
        } else if (protocol == 17) { // UDP
            protocolName = "UDP"
            payloadOffset += 8
        } else {
            // Other protocols without payload parsing
            return 
        }

        val payloadLength = length - payloadOffset
        if (payloadLength <= 0) return

        val payloadBytes = packet.copyOfRange(payloadOffset, length)
        
        // Convert the payload to an identifiable string format
        val textPayload = sanitizePayload(payloadBytes)
        
        if (textPayload.isNotBlank()) {
            // Use runBlocking or CoroutineScope to emit.
            // Better to use GlobalScope or a service scope, but since this might be called frequently:
            GlobalScope.launch(Dispatchers.Default) {
                CaptureManager.emitPacket(protocolName, srcIp, destIp, textPayload)
            }
        }
    }

    private fun sanitizePayload(payload: ByteArray): String {
        var printableCount = 0
        for (byte in payload) {
            val c = byte.toInt().toChar()
            if (c in ' '..'~' || c == '\n' || c == '\r' || c == '\t') {
                printableCount++
            }
        }
        
        // If it's mostly printable ASCII, return as string otherwise Hex
        return if (printableCount > payload.size * 0.4) {
            String(payload, Charsets.US_ASCII).replace(Regex("[^\\x20-\\x7E\\r\\n\\t]"), "·")
        } else {
            // Hex dump formatted nicely or just simple string
            payload.joinToString(" ") { "%02X".format(it) }
        }
    }
}
