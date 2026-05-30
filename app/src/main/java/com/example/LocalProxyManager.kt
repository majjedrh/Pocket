package com.example

import android.util.Log
import kotlinx.coroutines.*
import java.net.ServerSocket
import java.net.Socket

object LocalProxyManager {
    var proxyJob: Job? = null
    var isRunning = false
    var localPort = 0
    var targetIp = ""
    var targetPort = 0

    fun startProxy(tIp: String, tPort: Int) {
        if (isRunning) return
        localPort = tPort // Bind locally to the same port
        targetIp = tIp
        targetPort = tPort
        isRunning = true
        
        proxyJob = GlobalScope.launch(Dispatchers.IO) {
            var serverSocket: ServerSocket? = null
            try {
                serverSocket = ServerSocket(localPort)
                CaptureManager.emitPacket("System", "Proxy", "Started", "بدأ الوكيل المحلي على 127.0.0.1:$localPort\nالرجاء إدخال 127.0.0.1 في تطبيق الرسيفر للاتصال.")
                while (isActive) {
                    val clientSocket = serverSocket.accept()
                    handleClient(clientSocket, targetIp, targetPort)
                }
            } catch (e: Exception) {
                CaptureManager.emitPacket("System", "Proxy Error", "", e.message ?: "خطأ غير معروف")
                Log.e("LocalProxy", "Server Error", e)
            } finally {
                serverSocket?.close()
                isRunning = false
            }
        }
    }

    private fun handleClient(clientSocket: Socket, tIp: String, tPort: Int) {
        GlobalScope.launch(Dispatchers.IO) {
            var targetSocket: Socket? = null
            val clientIp = clientSocket.inetAddress.hostAddress ?: "Client"
            try {
                targetSocket = Socket(tIp, tPort)
                val clientIn = clientSocket.getInputStream()
                val clientOut = clientSocket.getOutputStream()
                val targetIn = targetSocket.getInputStream()
                val targetOut = targetSocket.getOutputStream()
                CaptureManager.emitPacket("Connection", clientIp, "$tIp:$tPort", "تم الاتصال بالرسيفر بنجاح وجاري تمرير البيانات.")

                val job1 = launch {
                    try {
                        val buffer = ByteArray(8192)
                        var read: Int
                        while (clientIn.read(buffer).also { read = it } != -1) {
                            val payload = buffer.copyOf(read)
                            val decoded = sanitizePayload(payload)
                            CaptureManager.emitPacket("TCP (→)", "[هاتفك]", "[الرسيفر]", decoded)
                            targetOut.write(buffer, 0, read)
                            targetOut.flush()
                        }
                    } catch (e: Exception) {}
                }

                val job2 = launch {
                    try {
                        val buffer = ByteArray(8192)
                        var read: Int
                        while (targetIn.read(buffer).also { read = it } != -1) {
                            val payload = buffer.copyOf(read)
                            val decoded = sanitizePayload(payload)
                            CaptureManager.emitPacket("TCP (←)", "[الرسيفر]", "[هاتفك]", decoded)
                            clientOut.write(buffer, 0, read)
                            clientOut.flush()
                        }
                    } catch (e: Exception) {}
                }

                joinAll(job1, job2)
            } catch (e: Exception) {
                CaptureManager.emitPacket("TCP Error", clientIp, tIp, "فشل الاتصال بالهدف: ${e.message}")
            } finally {
                try { clientSocket.close() } catch(e:Exception){}
                try { targetSocket?.close() } catch(e:Exception){}
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

    fun stopProxy() {
        isRunning = false
        proxyJob?.cancel()
        proxyJob = null
        GlobalScope.launch(Dispatchers.IO) {
            CaptureManager.emitPacket("System", "Proxy", "Stopped", "تم إيقاف الوكيل المحلي.")
        }
    }
}
