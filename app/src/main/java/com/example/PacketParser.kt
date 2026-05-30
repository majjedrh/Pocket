package com.example

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

object PacketParser {

    fun parseAndEmit(packet: ByteArray, length: Int) {
        if (length < 20) return

        val version = (packet[0].toInt() shr 4) and 0x0F
        if (version != 4) return

        val ihl = packet[0].toInt() and 0x0F
        val ipHeaderLength = ihl * 4

        val protocol = packet[9].toInt() and 0xFF
        val srcIp = "${packet[12].toUByte()}.${packet[13].toUByte()}.${packet[14].toUByte()}.${packet[15].toUByte()}"
        val destIp = "${packet[16].toUByte()}.${packet[17].toUByte()}.${packet[18].toUByte()}.${packet[19].toUByte()}"

        var payloadOffset = ipHeaderLength
        var protocolName = "Unknown"
        var flagsInfo = ""
        var sourcePort = 0
        var destPort = 0

        if (protocol == 6) { // TCP
            protocolName = "TCP"
            if (length >= ipHeaderLength + 20) {
                sourcePort = ((packet[ipHeaderLength].toInt() and 0xFF) shl 8) or (packet[ipHeaderLength + 1].toInt() and 0xFF)
                destPort = ((packet[ipHeaderLength + 2].toInt() and 0xFF) shl 8) or (packet[ipHeaderLength + 3].toInt() and 0xFF)

                val dataOffset = (packet[ipHeaderLength + 12].toInt() shr 4) and 0x0F
                payloadOffset += (dataOffset * 4)
                
                val tcpFlags = packet[ipHeaderLength + 13].toInt() and 0xFF
                if ((tcpFlags and 0x02) != 0) flagsInfo += "[SYN] "
                if ((tcpFlags and 0x10) != 0) flagsInfo += "[ACK] "
                if ((tcpFlags and 0x01) != 0) flagsInfo += "[FIN] "
                if ((tcpFlags and 0x08) != 0) flagsInfo += "[PSH] "
                if ((tcpFlags and 0x04) != 0) flagsInfo += "[RST] "
            }
        } else if (protocol == 17) { // UDP
            protocolName = "UDP"
            if (length >= ipHeaderLength + 8) {
                sourcePort = ((packet[ipHeaderLength].toInt() and 0xFF) shl 8) or (packet[ipHeaderLength + 1].toInt() and 0xFF)
                destPort = ((packet[ipHeaderLength + 2].toInt() and 0xFF) shl 8) or (packet[ipHeaderLength + 3].toInt() and 0xFF)
            }
            payloadOffset += 8
        } else {
            return 
        }

        val targetPort = CaptureManager.targetPort
        if (targetPort != null && targetPort > 0) {
            if (sourcePort != targetPort && destPort != targetPort) {
                return
            }
        }

        val payloadLength = length - payloadOffset
        val payloadBytes = if (payloadLength > 0) packet.copyOfRange(payloadOffset, length) else ByteArray(0)
        
        var textPayload = ""
        if (payloadLength > 0) {
            textPayload = sanitizePayload(payloadBytes)
        }
        
        if (textPayload.isBlank() && protocolName == "TCP") {
            textPayload = "$flagsInfo (مجرد محاولة اتصال / لا يوجد محتوى)"
        }

        val srcStr = "$srcIp:$sourcePort"
        val destStr = "$destIp:$destPort"

        if (textPayload.isNotBlank()) {
            GlobalScope.launch(Dispatchers.Default) {
                CaptureManager.emitPacket(protocolName, srcStr, destStr, textPayload)
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
        
        return if (printableCount > payload.size * 0.4) {
            String(payload, Charsets.US_ASCII).replace(Regex("[^\\x20-\\x7E\\r\\n\\t]"), "·")
        } else {
            payload.joinToString(" ") { "%02X".format(it) }
        }
    }
}
