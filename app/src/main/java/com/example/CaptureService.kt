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

class CaptureService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private var job: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") {
            stopCapture()
            return START_NOT_STICKY
        }

        val targetPackage = CaptureManager.targetPackageName
        if (targetPackage == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        startVpn(targetPackage)
        return START_STICKY
    }

    private fun startVpn(targetPackage: String) {
        if (vpnInterface != null) return

        try {
            val builder = Builder()
                .setSession("CommandSniffer")
                .addAddress("10.0.0.2", 24)
                .addRoute("0.0.0.0", 0) // Route everything through VPN
                .addAllowedApplication(targetPackage) // Only for chosen app
                .allowBypass() // Allow apps to bypass VPN for local network discovery

            // For local discovery to work inside the targeted app itself, sometimes routing 0.0.0.0/0 captures broadcast.
            // Android 11+ might drop broadcasts in VPN if not routed properly, or might require allowBypass.

            vpnInterface = builder.establish()

            if (vpnInterface != null) {
                CaptureManager.isCapturing = true
                startReading(vpnInterface!!)
            } else {
                Log.e("CaptureService", "Failed to establish VPN")
                stopCapture()
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
