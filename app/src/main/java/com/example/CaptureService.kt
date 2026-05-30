package com.example

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.FileInputStream
import java.net.InetAddress
import java.net.URL

class CaptureService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private var job: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") {
            stopCapture()
            return START_NOT_STICKY
        }

        startVpn()
        return START_STICKY
    }

    private fun startVpn() {
        if (vpnInterface != null) return

        try {
            val builder = Builder()
                .setSession("CommandSniffer")
                .addAddress("10.0.0.2", 24)
                .allowBypass()

            scope.launch {
                var resolvedIp = ""
                val input = CaptureManager.targetInput.trim()
                
                var hostPart = input
                var portPart: Int? = null
                
                if (input.startsWith("http://") || input.startsWith("https://")) {
                    try {
                        val url = URL(input)
                        hostPart = url.host
                        if (url.port != -1) {
                            portPart = url.port
                        }
                    } catch (e: Exception) {}
                } else if (input.contains(":")) {
                    val parts = input.split(":")
                    if (parts.size == 2) {
                        hostPart = parts[0]
                        portPart = parts[1].toIntOrNull()
                    }
                }

                if (hostPart.isNotEmpty()) {
                    try {
                        resolvedIp = InetAddress.getByName(hostPart).hostAddress ?: ""
                    } catch (e: Exception) {
                        Log.e("CaptureService", "Could not resolve: $hostPart")
                    }
                }

                CaptureManager.targetIp = resolvedIp
                CaptureManager.targetPort = portPart

                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    try {
                        if (resolvedIp.isNotEmpty()) {
                            Log.d("CaptureService", "Routing IP: $resolvedIp")
                            builder.addRoute(resolvedIp, 32)
                        } else {
                            builder.addRoute("0.0.0.0", 0)
                        }
                        
                        vpnInterface = builder.establish()
            
                        if (vpnInterface != null) {
                            CaptureManager.isCapturing = true
                            startReading(vpnInterface!!)
                        } else {
                            Log.e("CaptureService", "Failed to establish VPN")
                            stopCapture()
                        }
                    } catch (e: Exception) {
                        Log.e("CaptureService", "VPN Build Error: ${e.message}")
                        stopCapture()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("CaptureService", "VPN Error: ${e.message}")
            stopCapture()
        }
    }

    private fun startReading(vpnInterface: ParcelFileDescriptor) {
        job = scope.launch {
            val inputStream = FileInputStream(vpnInterface.fileDescriptor)
            val buffer = ByteArray(32767)

            try {
                while (isActive) {
                    val length = inputStream.read(buffer)
                    if (length > 0) {
                        PacketParser.parseAndEmit(buffer, length)
                    }
                }
            } catch (e: Exception) {
                Log.e("CaptureService", "Read error: ${e.message}")
            } finally {
                stopCapture()
            }
        }
    }

    private fun stopCapture() {
        CaptureManager.isCapturing = false
        job?.cancel()
        job = null
        try {
            vpnInterface?.close()
        } catch (e: Exception) {}
        vpnInterface = null
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopCapture()
    }
}
