package com.krang.ecutool

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val Bg = Color(0xFF101820)
private val Panel = Color(0xFF1D2B36)
private val Mint = Color(0xFF65D6A6)
private val Muted = Color(0xFFB7C5D1)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(16, 24, 32)
        window.navigationBarColor = android.graphics.Color.rgb(16, 24, 32)
        setContent { KrangEcuApp() }
    }
}

private data class MenuItemData(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val destination: String
)

@Composable
private fun KrangEcuApp() {
    var page by remember { mutableStateOf("home") }
    var selectedFileUri by remember { mutableStateOf<Uri?>(null) }
    var fileLabel by remember { mutableStateOf("ยังไม่ได้เลือกไฟล์") }
    var importedReport by remember { mutableStateOf<TuneFileReport?>(null) }
    var fileBusy by remember { mutableStateOf(false) }
    var fileMessage by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            selectedFileUri = uri
            fileLabel = uri.lastPathSegment?.substringAfterLast('/') ?: "เลือกไฟล์แล้ว"
            importedReport = null
            fileMessage = null
            page = "files"
        }
    }

    val backupExportPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { destinationUri ->
        val report = importedReport
        if (destinationUri != null && report != null) {
            scope.launch {
                fileBusy = true
                fileMessage = "กำลังส่งออกสำเนาสำรองและตรวจสอบ SHA-256…"
                try {
                    withContext(Dispatchers.IO) {
                        TuneFileRepository.exportBackup(context, report, destinationUri)
                    }
                    fileMessage = "ส่งออกสำเนาสำรองและตรวจ SHA-256 ผ่านแล้ว"
                } catch (error: Exception) {
                    fileMessage = "ส่งออกไม่สำเร็จ: ${error.message ?: "เกิดข้อผิดพลาด"}"
                } finally {
                    fileBusy = false
                }
            }
        }
    }

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Mint,
            background = Bg,
            surface = Panel,
            onPrimary = Bg,
            onBackground = Color.White,
            onSurface = Color.White
        )
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = Bg) {
            when (page) {
                "home" -> HomeScreen(onNavigate = { destination ->
                    if (destination == "files") filePicker.launch(arrayOf("*/*"))
                    else page = destination
                })
                "connect" -> UsbConnectScreen(onBack = { page = "home" })
                "live" -> PlaceholderScreen(
                    title = "อ่านค่ารถ",
                    subtitle = "ยังไม่มีข้อมูล ECU จริง — ต้องเพิ่มโปรโตคอลที่ตรงกับกล่องก่อน",
                    icon = Icons.Default.Speed,
                    onBack = { page = "home" }
                )
                "dtc" -> PlaceholderScreen(
                    title = "วิเคราะห์รหัสเสีย",
                    subtitle = "การอ่าน/ลบรหัสเสียจะเปิดใช้เมื่อรองรับคำสั่งของ ECU รุ่นนั้น",
                    icon = Icons.Default.ManageSearch,
                    onBack = { page = "home" }
                )
                "files" -> FileScreen(
                    fileLabel = fileLabel,
                    selectedUri = selectedFileUri,
                    importedReport = importedReport,
                    isBusy = fileBusy,
                    statusMessage = fileMessage,
                    onPickFile = { filePicker.launch(arrayOf("*/*")) },
                    onImportBackup = { uri ->
                        scope.launch {
                            fileBusy = true
                            fileMessage = "กำลังนำเข้าและสำรองไฟล์ต้นฉบับ…"
                            importedReport = null
                            try {
                                val report = withContext(Dispatchers.IO) {
                                    TuneFileRepository.importAndBackup(context, uri)
                                }
                                importedReport = report
                                fileMessage = "สำรองไฟล์แล้ว และตรวจ SHA-256 ของสำเนาผ่าน"
                            } catch (error: Exception) {
                                fileMessage = "นำเข้าไม่สำเร็จ: ${error.message ?: "เกิดข้อผิดพลาด"}"
                            } finally {
                                fileBusy = false
                            }
                        }
                    },
                    onExportBackup = { report -> backupExportPicker.launch(report.backupFileName) },
                    onBack = { page = "home" }
                )
                "editor" -> PlaceholderScreen(
                    title = "แก้ไขตารางจูน",
                    subtitle = "ต้องตรวจสอบ BIN และ XDF ที่ตรงรุ่นก่อนเปิดให้แก้ไขค่า",
                    icon = Icons.Default.Tune,
                    onBack = { page = "home" }
                )
                else -> HomeScreen(onNavigate = { page = it })
            }
        }
    }
}

@Composable
private fun HomeScreen(onNavigate: (String) -> Unit) {
    val items = listOf(
        MenuItemData("เชื่อมต่อ ECU", "ตรวจหา USB OTG", Icons.Default.Usb, "connect"),
        MenuItemData("อ่านค่ารถ", "RPM / Sensor / Live Data", Icons.Default.MonitorHeart, "live"),
        MenuItemData("วิเคราะห์รหัสเสีย", "อ่าน DTC ตามรุ่นที่รองรับ", Icons.Default.ManageSearch, "dtc"),
        MenuItemData("จัดการไฟล์จูน", "นำเข้า / สำรอง / ส่งออก", Icons.Default.FolderOpen, "files"),
        MenuItemData("แก้ไขตารางจูน", "BIN + XDF ที่ตรงรุ่น", Icons.Default.Tune, "editor")
    )

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                modifier = Modifier.size(54.dp).background(Panel, RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Speed, contentDescription = null, tint = Mint, modifier = Modifier.size(32.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("KRANG ECU TOOL", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text("Motorcycle Diagnostic & Tuning", fontSize = 12.sp, color = Muted)
            }
            Surface(color = Color(0xFF174C3A), shape = RoundedCornerShape(20.dp)) {
                Text("OFFLINE", color = Mint, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
            }
        }

        Text("เมนูหลัก", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(items) { item -> MenuCard(item = item, onClick = { onNavigate(item.destination) }) }
        }
        Text(
            "การตรวจพบอะแดปเตอร์ยังไม่เท่ากับการสื่อสาร ECU — ต้องพัฒนาไดรเวอร์และโปรโตคอลแยกตามรุ่น",
            fontSize = 11.sp,
            color = Muted,
            lineHeight = 16.sp
        )
    }
}

@Composable
private fun MenuCard(item: MenuItemData, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().height(148.dp).clickable(onClick = onClick),
        color = Panel,
        shape = RoundedCornerShape(18.dp),
        tonalElevation = 2.dp
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(item.icon, contentDescription = null, tint = Mint, modifier = Modifier.size(28.dp))
            Text(item.title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Color.White)
            Text(item.subtitle, fontSize = 11.sp, color = Muted, lineHeight = 15.sp)
        }
    }
}

@Suppress("DEPRECATION")
@Composable
private fun UsbConnectScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val usbManager = remember(context) { context.getSystemService(Context.USB_SERVICE) as UsbManager }
    val permissionAction = remember(context) { "${context.packageName}.USB_PERMISSION" }
    var devices by remember { mutableStateOf<List<UsbDevice>>(emptyList()) }
    var status by remember { mutableStateOf("กำลังตรวจหาอุปกรณ์ USB…") }

    val refreshDevices: () -> Unit = {
        devices = usbManager.deviceList.values.toList()
        status = if (devices.isEmpty()) {
            "ยังไม่พบอุปกรณ์ USB — ตรวจสาย OTG แล้วกดตรวจหาอีกครั้ง"
        } else {
            "พบอุปกรณ์ USB ${devices.size} รายการ"
        }
    }

    LaunchedEffect(usbManager) { refreshDevices() }

    DisposableEffect(context, permissionAction, usbManager) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receiveContext: Context?, intent: Intent?) {
                if (intent?.action != permissionAction) return
                val device = getUsbDeviceFromIntent(intent)
                val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                status = when {
                    device == null -> "ได้รับผลการขอสิทธิ์ แต่ไม่พบข้อมูลอุปกรณ์"
                    granted -> "อนุญาตสิทธิ์ USB แล้วสำหรับ VID ${device.vendorId.toString(16).uppercase()} / PID ${device.productId.toString(16).uppercase()} — ยังไม่ได้เปิดการสื่อสาร ECU"
                    else -> "ไม่ได้รับอนุญาตให้เข้าถึงอุปกรณ์ USB"
                }
                refreshDevices()
            }
        }
        val filter = IntentFilter(permissionAction)
        if (Build.VERSION.SDK_INT >= 33) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }
        onDispose { context.unregisterReceiver(receiver) }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "กลับ", tint = Color.White) }
            Text("เชื่อมต่อ USB OTG", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }

        Surface(color = Panel, shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Default.Usb, contentDescription = null, tint = Mint, modifier = Modifier.size(28.dp))
                    Text("ตรวจหาอุปกรณ์ USB", color = Color.White, fontWeight = FontWeight.Bold)
                }
                Text(status, color = Muted, fontSize = 13.sp, lineHeight = 19.sp)
                Button(onClick = refreshDevices, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("ตรวจหาอีกครั้ง")
                }
            }
        }

        if (devices.isEmpty()) {
            Text("ยังไม่มีอุปกรณ์ที่ระบบ Android มองเห็น", color = Muted, fontSize = 13.sp)
        } else {
            Text("อุปกรณ์ที่พบ (${devices.size})", color = Color.White, fontWeight = FontWeight.SemiBold)
            Column(
                modifier = Modifier.weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                devices.forEach { device ->
                    Surface(color = Panel, shape = RoundedCornerShape(14.dp)) {
                        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(device.productName ?: "USB device", color = Color.White, fontWeight = FontWeight.Bold)
                            Text(device.manufacturerName ?: "ไม่ทราบผู้ผลิต", color = Muted, fontSize = 12.sp)
                            Text(
                                "VID ${device.vendorId.toString(16).uppercase().padStart(4, '0')} · PID ${device.productId.toString(16).uppercase().padStart(4, '0')} · Interfaces ${device.interfaceCount}",
                                color = Muted,
                                fontSize = 11.sp
                            )
                            val hasPermission = usbManager.hasPermission(device)
                            Text(
                                if (hasPermission) "สถานะสิทธิ์: อนุญาตแล้ว" else "สถานะสิทธิ์: ยังไม่ได้อนุญาต",
                                color = if (hasPermission) Mint else Muted,
                                fontSize = 12.sp
                            )
                            OutlinedButton(
                                onClick = {
                                    if (usbManager.hasPermission(device)) {
                                        status = "อุปกรณ์นี้ได้รับสิทธิ์แล้ว แต่ยังไม่มีไดรเวอร์ Serial/ECU ในรุ่นต้นแบบนี้"
                                    } else {
                                        val permissionIntent = PendingIntent.getBroadcast(
                                            context,
                                            device.deviceId,
                                            Intent(permissionAction).setPackage(context.packageName),
                                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                                        )
                                        usbManager.requestPermission(device, permissionIntent)
                                        status = "กำลังขอสิทธิ์เข้าถึงอุปกรณ์ USB…"
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(if (hasPermission) "ตรวจสถานะอุปกรณ์" else "ขอสิทธิ์ USB")
                            }
                        }
                    }
                }
            }
        }

        Text(
            "ข้อจำกัด: หน้านี้ตรวจพบอุปกรณ์และขอสิทธิ์ USB เท่านั้น ยังไม่ส่งคำสั่งไปยัง ECU และยังไม่มีการอ่าน/เขียนกล่อง",
            color = Muted,
            fontSize = 11.sp,
            lineHeight = 16.sp
        )
    }
}

@Suppress("DEPRECATION")
private fun getUsbDeviceFromIntent(intent: Intent): UsbDevice? {
    return if (Build.VERSION.SDK_INT >= 33) {
        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
    } else {
        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE) as? UsbDevice
    }
}

@Composable
private fun PlaceholderScreen(title: String, subtitle: String, icon: ImageVector, onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "กลับ", tint = Color.White) }
            Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
        Surface(color = Panel, shape = RoundedCornerShape(18.dp)) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Icon(icon, contentDescription = null, tint = Mint, modifier = Modifier.size(48.dp))
                Text(title, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color.White)
                Text(subtitle, color = Muted, fontSize = 14.sp, lineHeight = 20.sp)
                Button(onClick = onBack) { Text("กลับหน้าเมนู") }
            }
        }
    }
}

@Composable
private fun FileScreen(
    fileLabel: String,
    selectedUri: Uri?,
    importedReport: TuneFileReport?,
    isBusy: Boolean,
    statusMessage: String?,
    onPickFile: () -> Unit,
    onImportBackup: (Uri) -> Unit,
    onExportBackup: (TuneFileReport) -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "กลับ", tint = Color.White) }
            Text("จัดการไฟล์จูน", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
        Surface(color = Panel, shape = RoundedCornerShape(18.dp)) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(11.dp)
            ) {
                Icon(Icons.Default.FolderOpen, contentDescription = null, tint = Mint, modifier = Modifier.size(38.dp))
                Text(if (selectedUri == null) "ยังไม่ได้เลือกไฟล์" else "ไฟล์ที่เลือก", color = Color.White, fontWeight = FontWeight.Bold)
                Text(fileLabel, color = Muted, fontSize = 12.sp)
                OutlinedButton(onClick = onPickFile, modifier = Modifier.fillMaxWidth(), enabled = !isBusy) {
                    Icon(Icons.Default.FileOpen, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("เลือกไฟล์ BIN หรือ XDF")
                }
                Button(
                    onClick = { selectedUri?.let(onImportBackup) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = selectedUri != null && !isBusy
                ) {
                    Icon(Icons.Default.Backup, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (isBusy) "กำลังดำเนินการ…" else "นำเข้าและสำรองต้นฉบับ")
                }
                if (statusMessage != null) {
                    Text(statusMessage, color = if (statusMessage.contains("ผ่านแล้ว") || statusMessage.contains("สำเร็จ")) Mint else Muted, fontSize = 12.sp, lineHeight = 17.sp)
                }
            }
        }

        if (importedReport != null) {
            Surface(color = Panel, shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("ผลตรวจไฟล์แบบอ่านอย่างเดียว", color = Color.White, fontWeight = FontWeight.Bold)
                    Text("ไฟล์: ${importedReport.originalName}", color = Color.White, fontSize = 12.sp)
                    Text("ชนิดไฟล์: ${importedReport.fileType}", color = Mint, fontSize = 13.sp)
                    Text("ขนาด: ${formatFileSize(importedReport.sizeBytes)}", color = Muted, fontSize = 12.sp)
                    Text("SHA-256:", color = Muted, fontSize = 11.sp)
                    Text(importedReport.sha256, color = Color.White, fontSize = 10.sp, lineHeight = 14.sp)
                    if (importedReport.binHeaderHex != null) {
                        Text("ข้อมูล 16 ไบต์แรก: ${importedReport.binHeaderHex}", color = Muted, fontSize = 11.sp, lineHeight = 15.sp)
                    }
                    if (importedReport.fileType == "XDF") {
                        Text("ชื่อ XDF: ${importedReport.xdfTitle ?: "ไม่พบ title"}", color = Muted, fontSize = 12.sp)
                        Text("จำนวนตาราง XDFTABLE: ${importedReport.xdfTableCount ?: 0}", color = Muted, fontSize = 12.sp)
                        Text("ยังไม่ได้ยืนยันว่า XDF ตรงกับ BIN หรือ ECU รุ่นใด", color = Color(0xFFFFC66D), fontSize = 11.sp)
                        if (importedReport.xdfParseWarning != null) {
                            Text("คำเตือนการอ่าน XDF: ${importedReport.xdfParseWarning}", color = Color(0xFFFFC66D), fontSize = 11.sp)
                        }
                    }
                    Text("สำเนาในพื้นที่แอป: ${importedReport.backupFileName}", color = Muted, fontSize = 10.sp, lineHeight = 14.sp)
                    Button(
                        onClick = { onExportBackup(importedReport) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isBusy
                    ) {
                        Icon(Icons.Default.SaveAlt, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("ส่งออกสำเนาสำรองไปยังมือถือ")
                    }
                }
            }
        }

        Text(
            "ความปลอดภัย: แอปไม่แก้ไขไฟล์ต้นฉบับและไม่เขียน ECU สำเนาในพื้นที่แอปจะถูกลบเมื่อถอนการติดตั้ง จึงควรส่งออกสำเนาไปเก็บไว้อีกแห่งก่อนทำงานต่อ",
            color = Muted,
            fontSize = 11.sp,
            lineHeight = 16.sp
        )
    }
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes >= 1024L * 1024L -> "%.2f MB".format(bytes / (1024.0 * 1024.0))
        bytes >= 1024L -> "%.1f KB".format(bytes / 1024.0)
        else -> "$bytes bytes"
    }
}
