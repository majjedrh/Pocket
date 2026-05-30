package com.example

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    MainApp()
                }
            }
        }
    }
}

sealed class Screen {
    object TargetInput : Screen()
    object CaptureScreen : Screen()
}

@Composable
fun MainApp() {
    val context = LocalContext.current
    var currentScreen by remember { mutableStateOf<Screen>(if (CaptureManager.isCapturing) Screen.CaptureScreen else Screen.TargetInput) }
    
    val vpnLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val intent = Intent(context, CaptureService::class.java)
            context.startService(intent)
            currentScreen = Screen.CaptureScreen
        } else {
            Toast.makeText(context, "لم يتم منح إذن الشبكة (VPN)", Toast.LENGTH_SHORT).show()
        }
    }

    when (currentScreen) {
        is Screen.TargetInput -> {
            TargetInputScreen(onStart = { input ->
                CaptureManager.targetInput = input
                val vpnIntent = VpnService.prepare(context)
                if (vpnIntent != null) {
                    vpnLauncher.launch(vpnIntent)
                } else {
                    val intent = Intent(context, CaptureService::class.java)
                    context.startService(intent)
                    currentScreen = Screen.CaptureScreen
                }
            })
        }
        is Screen.CaptureScreen -> {
            CaptureScreen(
                onStop = {
                    val intent = Intent(context, CaptureService::class.java).apply { action = "STOP" }
                    context.startService(intent)
                    currentScreen = Screen.TargetInput
                    CaptureManager.clearPackets()
                }
            )
        }
    }
}

@Composable
fun TargetInputScreen(onStart: (String) -> Unit) {
    var targetInput by remember { mutableStateOf(CaptureManager.targetInput) }

    Scaffold(
        topBar = {
            HeaderSection(onActionClick = null, actionText = null)
        },
        containerColor = BgDark
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderColor)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("حدد هدف المراقبة (بدون تحديد تطبيق)", color = PrimaryAccent, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(Modifier.height(16.dp))
                    
                    OutlinedTextField(
                        value = targetInput,
                        onValueChange = { targetInput = it },
                        label = { Text("IP والرقم أو رابط (مثال: 192.168.0.121:20000)", fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = TextStyle(color = TextPrimary, fontSize = 14.sp),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = BorderColor,
                            focusedBorderColor = PrimaryAccent,
                            unfocusedLabelColor = TextSecondary,
                            focusedLabelColor = PrimaryAccent
                        ),
                        singleLine = true
                    )
                    Text(
                        "أدخل عنوان IP أو رابط لفلترة الاتصالات وتسجيل الأوامر المرسلة إلى هذا الهدف فقط عبر أي تطبيق يعمل على الجهاز.",
                        color = TextSecondary, 
                        fontSize = 12.sp, 
                        modifier = Modifier.padding(top = 8.dp), 
                        lineHeight = 18.sp
                    )
                    
                    Spacer(Modifier.height(24.dp))
                    Button(
                        onClick = { onStart(targetInput) },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryAccent),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("بدء الالتقاط", color = PrimaryAccentDark, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun CaptureScreen(onStop: () -> Unit) {
    val target = CaptureManager.targetInput.ifEmpty { "كل الاتصالات العشوائية" }
    var packets by remember { mutableStateOf<List<CapturedPacket>>(emptyList()) }
    
    LaunchedEffect(Unit) {
        CaptureManager.packetFlow.collect { newPacket ->
            packets = (packets + newPacket).takeLast(100)
        }
    }

    Scaffold(
        topBar = {
            HeaderSection(onActionClick = onStop, actionText = "إيقاف")
        },
        containerColor = BgDark,
        bottomBar = {
            FooterSection(packets = packets)
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp).padding(top = 16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().background(SurfaceDark, RoundedCornerShape(16.dp)).border(1.dp, BorderColor, RoundedCornerShape(16.dp)).padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(48.dp).background(BorderColor, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                        Box(modifier = Modifier.size(32.dp).background(Color(0xFF3B82F6), RoundedCornerShape(4.dp)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.Info, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text("الهدف المراقب", fontSize = 12.sp, color = PrimaryAccent, fontWeight = FontWeight.Medium)
                        Text(target, fontSize = 16.sp, color = TextPrimary, fontWeight = FontWeight.Medium)
                        if (CaptureManager.targetIp.isNotEmpty()) {
                            Text("IP: ${CaptureManager.targetIp}" + if(CaptureManager.targetPort != null) " Port: ${CaptureManager.targetPort}" else "", fontSize = 10.sp, color = TextSecondary, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
                IconButton(onClick = onStop, modifier = Modifier.background(BorderColor, CircleShape).size(40.dp)) {
                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "تغيير", tint = PrimaryAccent)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("الأوامر الملتقطة (${packets.size})", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextSecondary, letterSpacing = 1.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(6.dp).background(Color(0xFF22C55E), CircleShape))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("متصل بالمحول", fontSize = 10.sp, color = PrimaryAccent)
                }
            }

            Box(modifier = Modifier.fillMaxWidth().weight(1f).border(1.dp, BorderColor, RoundedCornerShape(12.dp)).clip(RoundedCornerShape(12.dp))) {
                if (packets.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("بانتظار الأوامر...", color = TextSecondary, fontSize = 14.sp)
                    }
                } else {
                    val sdf = SimpleDateFormat("HH:mm:ss.SS", Locale.US)
                    val context = LocalContext.current
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(packets.reversed(), key = { it.id }) { packet ->
                            val timeStr = sdf.format(Date(packet.time))
                            val isHTTPColor = packet.protocol.contains("TCP")
                            val badgeColor = if (isHTTPColor) ProtocolBlue else ProtocolGreen
                            
                            Column(modifier = Modifier.fillMaxWidth().border(0.5.dp, BorderColor).clickable {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("Captured Command", packet.payload))
                                Toast.makeText(context, "تم تنسخ الأمر", Toast.LENGTH_SHORT).show()
                            }.padding(12.dp)) {
                                Row(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                                    Text(timeStr, fontSize = 10.sp, color = WarningText)
                                    Box(modifier = Modifier.border(1.dp, badgeColor, RoundedCornerShape(4.dp)).padding(horizontal = 4.dp, vertical = 2.dp)) {
                                        Text(packet.protocol, fontSize = 10.sp, color = badgeColor)
                                    }
                                }
                                Text("${packet.source} -> ${packet.destination}", fontSize = 10.sp, color = TextSecondary, modifier = Modifier.padding(bottom = 4.dp))
                                Text(packet.payload, fontFamily = FontFamily.Monospace, fontSize = 14.sp, color = TextPrimary, modifier = Modifier.fillMaxWidth())
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HeaderSection(onActionClick: (() -> Unit)?, actionText: String?) {
    Row(
        modifier = Modifier.fillMaxWidth().background(BgDark).border(0.5.dp, BorderColor).padding(horizontal = 16.dp, vertical = 24.dp).padding(bottom = 0.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(40.dp).background(PrimaryAccent, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Settings, contentDescription = null, tint = PrimaryAccentDark)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text("ملتقط الأوامر", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Text("مراقبة حركة الشبكة الحية", fontSize = 12.sp, color = TextSecondary)
            }
        }
        if (onActionClick != null && actionText != null) {
            Button(
                onClick = onActionClick,
                colors = ButtonDefaults.buttonColors(containerColor = ButtonBg, contentColor = ButtonText),
                shape = CircleShape,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(actionText, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
fun FooterSection(packets: List<CapturedPacket>) {
    val context = LocalContext.current
    Row(modifier = Modifier.fillMaxWidth().background(BgDark).padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(
            onClick = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val allText = packets.joinToString("\n\n") { "Protocol: ${it.protocol}\nPayload:\n${it.payload}" }
                clipboard.setPrimaryClip(ClipData.newPlainText("Captured Commands", allText))
                Toast.makeText(context, "تم النسخ", Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier.weight(1f).height(48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = SecondaryButtonBg, contentColor = PrimaryAccent),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text("نسخ الكل", fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
        OutlinedButton(
            onClick = {
                CaptureManager.clearPackets()
            },
            modifier = Modifier.weight(1f).height(48.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
            border = androidx.compose.foundation.BorderStroke(1.dp, BorderColor),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text("مسح السجل", fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
    }
}
