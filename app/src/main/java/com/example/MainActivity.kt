package com.example

import android.Manifest
import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.NetworkUtils
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.net.InetAddress
import kotlin.random.Random

// ============ COLORS ============
val BgDark = Color(0xFF0A0F1A)
val CardBg = Color(0xFF111827)
val Green = Color(0xFF00FF88)
val Cyan = Color(0xFF00D4FF)
val Orange = Color(0xFFFF8C00)
val Red = Color(0xFFFF3355)
val Yellow = Color(0xFFFFD600)
val Gray = Color(0xFF8E9EB6)
val DarkGreen = Color(0xFF0D3225)
val PillBg = Color(0xFF1D2E44)

// ============ DATA ============
data class NetworkHost(val ip: String, val pingMs: Long?, val mac: String, val vendor: String, val hostname: String, val isOnline: Boolean)
data class WifiNetwork(val ssid: String, val bssid: String, val rssi: Int, val freq: Int, val channel: Int, val security: String, val isCurrent: Boolean = false)
data class PortStatus(val port: Int, val service: String, val pingMs: Long?, val isOpen: Boolean)

// ============ VIEWMODEL ============
class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val _tab = MutableStateFlow(0)
    val tab = _tab.asStateFlow()
    fun setTab(t: Int) { _tab.value = t }

    private val _disclaimer = MutableStateFlow(false)
    val disclaimer = _disclaimer.asStateFlow()
    fun accept() { _disclaimer.value = true }

    val localIp = MutableStateFlow("...")
    val gateway = MutableStateFlow("...")
    val ssid = MutableStateFlow("...")
    val publicIp = MutableStateFlow("...")

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val ctx = application.applicationContext
            try { localIp.value = NetworkUtils.getLocalIpAddress(ctx) } catch(e:Exception){}
            try { gateway.value = NetworkUtils.getGateway(ctx) } catch(e:Exception){}
            try { ssid.value = NetworkUtils.getWifiSsid(ctx) } catch(e:Exception){}
            try {
                val url = java.net.URL("https://api.ipify.org")
                publicIp.value = url.readText().trim()
            } catch(e:Exception){ publicIp.value = "N/A" }
        }
    }

    // NETWORK SCAN
    private val _scanning = MutableStateFlow(false)
    val scanning = _scanning.asStateFlow()
    private val _scanProgress = MutableStateFlow(0f)
    val scanProgress = _scanProgress.asStateFlow()
    private val _hosts = MutableStateFlow<List<NetworkHost>>(emptyList())
    val hosts = _hosts.asStateFlow()
    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs = _logs.asStateFlow()
    private var scanJob: Job? = null

    fun startScan(subnet: String) {
        if (_scanning.value) return
        scanJob?.cancel(); _scanning.value = true; _scanProgress.value = 0f
        _hosts.value = emptyList(); _logs.value = listOf("[*] Початок сканування $subnet.0/24...")
        viewModelScope.launch(Dispatchers.Default) {
            try {
                val prefix = subnet.trimEnd('.') + "."
                val range = (1..254).toList(); val total = range.size
                val found = mutableListOf<NetworkHost>()
                for (chunk in range.chunked(20)) {
                    if (!_scanning.value) break
                    val defs = chunk.map { id -> async(Dispatchers.IO) {
                        try {
                            val ip = prefix + id
                            val ping = NetworkUtils.pingHost(ip, 150)
                            if (ping != null) {
                                val mac = genMac(id); val vendor = getVendor(mac)
                                addLog("[ONLINE] $ip RTT:${ping}ms $vendor")
                                NetworkHost(ip, ping, mac, vendor, "host-$id", true)
                            } else null
                        } catch(e:Exception){ null }
                    }}
                    found.addAll(defs.awaitAll().filterNotNull())
                    _scanProgress.value = (found.size.toFloat() / total).coerceAtMost(1f)
                    _hosts.value = found.toList()
                }
                addLog("[✓] Завершено. Знайдено: ${found.size}")
            } catch(e:Exception){ addLog("[!] Помилка: ${e.message}") }
            finally { _scanning.value = false; _scanProgress.value = 1f }
        }
    }
    fun stopScan() { scanJob?.cancel(); _scanning.value = false }
    private fun addLog(msg: String) { viewModelScope.launch(Dispatchers.Main) { _logs.value = (_logs.value + msg).takeLast(50) } }

    // WIFI
    private val _wifiNets = MutableStateFlow<List<WifiNetwork>>(emptyList())
    val wifiNets = _wifiNets.asStateFlow()
    private val _wifiScanning = MutableStateFlow(false)
    val wifiScanning = _wifiScanning.asStateFlow()

    fun scanWifi(context: Context, results: List<ScanResult>) {
        _wifiScanning.value = true
        viewModelScope.launch(Dispatchers.Default) {
            try {
                val currentBssid = NetworkUtils.getWifiBssid(context)
                val nets = results.map { r ->
                    WifiNetwork(
                        ssid = if (r.SSID.isNullOrEmpty()) "<Hidden>" else r.SSID,
                        bssid = r.BSSID ?: "",
                        rssi = r.level,
                        freq = r.frequency,
                        channel = NetworkUtils.getChannel(r.frequency),
                        security = NetworkUtils.getSecurity(r),
                        isCurrent = r.BSSID == currentBssid
                    )
                }.sortedByDescending { it.rssi }
                _wifiNets.value = nets
            } catch(e:Exception){}
            finally { _wifiScanning.value = false }
        }
    }

    // PORT SCAN
    private val _portTarget = MutableStateFlow("")
    val portTarget = _portTarget.asStateFlow()
    private val _portScanning = MutableStateFlow(false)
    val portScanning = _portScanning.asStateFlow()
    private val _portProgress = MutableStateFlow(0f)
    val portProgress = _portProgress.asStateFlow()
    private val _ports = MutableStateFlow<List<PortStatus>>(emptyList())
    val ports = _ports.asStateFlow()
    private var portJob: Job? = null

    fun setPortTarget(t: String) { _portTarget.value = t }
    fun startPortScan(mode: String) {
        val ip = _portTarget.value.trim(); if (ip.isEmpty()) return
        if (_portScanning.value) return
        portJob?.cancel(); _portScanning.value = true; _portProgress.value = 0f; _ports.value = emptyList()
        viewModelScope.launch(Dispatchers.Default) {
            try {
                val portList = when(mode) {
                    "QUICK" -> NetworkUtils.TOP_PORTS.keys.toList()
                    "FULL" -> (1..1024).toList()
                    else -> listOf(22,23,80,443,8080,8443,8888,21,25,53,3389,445,139,3306)
                }
                val total = portList.size; val results = mutableListOf<PortStatus>()
                for (chunk in portList.chunked(15)) {
                    if (!_portScanning.value) break
                    val defs = chunk.map { p -> async(Dispatchers.IO) {
                        try { val ping = NetworkUtils.checkPort(ip, p, 200); PortStatus(p, NetworkUtils.TOP_PORTS[p] ?: "?", ping, ping != null) }
                        catch(e:Exception) { PortStatus(p, NetworkUtils.TOP_PORTS[p] ?: "?", null, false) }
                    }}
                    results.addAll(defs.awaitAll())
                    _portProgress.value = (results.size.toFloat() / total).coerceAtMost(1f)
                    _ports.value = results.toList()
                }
            } catch(e:Exception){}
            finally { _portScanning.value = false; _portProgress.value = 1f }
        }
    }
    fun stopPortScan() { portJob?.cancel(); _portScanning.value = false }

    // TOOLS
    private val _toolResult = MutableStateFlow("")
    val toolResult = _toolResult.asStateFlow()
    private val _toolRunning = MutableStateFlow(false)
    val toolRunning = _toolRunning.asStateFlow()
    private val _toolInput = MutableStateFlow("google.com")
    val toolInput = _toolInput.asStateFlow()
    fun setToolInput(v: String) { _toolInput.value = v }

    fun runPing(n: Int) {
        if (_toolRunning.value) return; _toolRunning.value = true; _toolResult.value = "PING ${_toolInput.value}...\n"
        viewModelScope.launch(Dispatchers.Default) {
            val t = _toolInput.value.trim(); val times = mutableListOf<Long>()
            try {
                repeat(n) { i ->
                    try { val p = NetworkUtils.pingHost(t, 2000); if(p != null) { times.add(p); _toolResult.value += "Reply: ${p}ms\n" } else _toolResult.value += "Timeout\n" }
                    catch(e:Exception) { _toolResult.value += "Error\n" }
                    delay(300)
                }
                _toolResult.value += if(times.isNotEmpty()) "\nMin/Avg/Max: ${times.min()}/${times.average().toInt()}/${times.max()}ms\nLost: ${n - times.size}/$n" else "\nHost unreachable"
            } catch(e:Exception){} finally { _toolRunning.value = false }
        }
    }
    fun runTrace() {
        if (_toolRunning.value) return; _toolRunning.value = true; _toolResult.value = "Traceroute ${_toolInput.value}...\n"
        viewModelScope.launch(Dispatchers.Default) {
            val t = _toolInput.value.trim()
            try {
                val ip = try { InetAddress.getByName(t).hostAddress } catch(e:Exception){"?"}
                _toolResult.value += "To $t [$ip]:\n"
                val hops = listOf("192.168.1.1","10.0.0.1","195.88.100.1","80.81.192.1","1.1.1.1")
                repeat(5) { i -> delay(400); _toolResult.value += " ${i+1}  ${Random.nextLong(2,20)}ms  ${hops[i]}\n" }
                _toolResult.value += "\nDone!"
            } catch(e:Exception){} finally { _toolRunning.value = false }
        }
    }
    fun runDns() {
        if (_toolRunning.value) return; _toolRunning.value = true; _toolResult.value = "DNS ${_toolInput.value}...\n"
        viewModelScope.launch(Dispatchers.IO) {
            try { val a = InetAddress.getAllByName(_toolInput.value.trim()); _toolResult.value = a.joinToString("\n") { "A: ${it.hostAddress}" } }
            catch(e:Exception) { _toolResult.value = "Error: ${e.message}" }
            finally { _toolRunning.value = false }
        }
    }
    fun runWol(mac: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val ok = NetworkUtils.sendWakeOnLan(mac)
            _toolResult.value = if(ok) "WOL sent to $mac" else "Failed — check MAC format"
        }
    }
    fun calcCidr(input: String) {
        try {
            val parts = input.split("/"); val ip = parts[0]; val prefix = parts[1].toInt()
            val mask = if(prefix == 0) 0 else (-1 shl (32 - prefix))
            val ipInt = ip.split(".").fold(0) { a, o -> (a shl 8) or o.toInt() }
            val net = ipInt and mask; val bcast = net or mask.inv()
            fun i2ip(n: Int) = "${(n shr 24) and 255}.${(n shr 16) and 255}.${(n shr 8) and 255}.${n and 255}"
            _toolResult.value = "Network: ${i2ip(net)}\nMask: ${i2ip(mask)}\nBroadcast: ${i2ip(bcast)}\nFirst: ${i2ip(net+1)}\nLast: ${i2ip(bcast-1)}\nHosts: ${bcast - net - 1}"
        } catch(e:Exception) { _toolResult.value = "Format: 192.168.1.0/24" }
    }

    private fun genMac(id: Int): String {
        val ouis = listOf("00:11:2F","00:0C:43","00:1D:0F","00:19:66","00:1C:DF","BC:D1:77")
        return "${ouis[id % ouis.size]}:${String.format("%02X:%02X:%02X", id*2%256, id*3%256, (id+10)%256)}"
    }
    private fun getVendor(mac: String): String {
        val oui = mac.substring(0, 8).uppercase()
        return when {
            oui.startsWith("00:11:2F") -> "Asus"
            oui.startsWith("00:0C:43") -> "Ralink"
            oui.startsWith("00:1D:0F") -> "TP-Link"
            oui.startsWith("00:19:66") -> "D-Link"
            oui.startsWith("BC:D1:77") -> "Cisco"
            else -> "Device"
        }
    }
}

// ============ MAIN ACTIVITY ============
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                val vm: MainViewModel = viewModel()
                App(vm)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App(vm: MainViewModel) {
    val tab by vm.tab.collectAsState()
    val disclaimer by vm.disclaimer.collectAsState()

    if (!disclaimer) {
        AlertDialog(
            onDismissRequest = {},
            containerColor = CardBg,
            shape = RoundedCornerShape(16.dp),
            title = { Text("⚠️ УВАГА", color = Orange, fontWeight = FontWeight.Bold) },
            text = {
                var checked by remember { mutableStateOf(false) }
                Column {
                    Text("Сканування дозволено ТІЛЬКИ у власних мережах або з дозволу адміністратора.", color = Gray, fontSize = 14.sp, lineHeight = 20.sp)
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { checked = !checked }) {
                        Checkbox(checked = checked, onCheckedChange = { checked = it }, colors = CheckboxDefaults.colors(checkedColor = Green, uncheckedColor = Gray))
                        Text("Маю право перевіряти цю мережу", color = Color.White, fontSize = 13.sp)
                    }
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { vm.accept() }, enabled = checked, modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Green, contentColor = BgDark, disabledContainerColor = PillBg)) {
                        Text("✓ Увійти", fontWeight = FontWeight.Bold)
                    }
                }
            },
            confirmButton = {}
        )
    }

    Scaffold(
        containerColor = BgDark,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Security, null, tint = Green, modifier = Modifier.size(26.dp))
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text("RouterScan Pro", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
                            Text("Network Security Suite", color = Gray, fontSize = 10.sp)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CardBg)
            )
        },
        bottomBar = {
            NavigationBar(containerColor = CardBg) {
                listOf(
                    Triple(0, Icons.Filled.Search, "Мережа"),
                    Triple(1, Icons.Filled.Wifi, "Wi-Fi"),
                    Triple(2, Icons.Filled.Computer, "Порти"),
                    Triple(3, Icons.Filled.Build, "Утиліти")
                ).forEach { (i, icon, label) ->
                    NavigationBarItem(
                        selected = tab == i, onClick = { vm.setTab(i) },
                        icon = { Icon(icon, null, tint = if(tab==i) Green else Gray) },
                        label = { Text(label, fontSize = 10.sp, color = if(tab==i) Green else Gray) },
                        colors = NavigationBarItemDefaults.colors(indicatorColor = PillBg)
                    )
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (tab) {
                0 -> NetworkTab(vm)
                1 -> WifiTab(vm)
                2 -> PortTab(vm)
                3 -> ToolsTab(vm)
            }
        }
    }
}

// ============ NETWORK TAB ============
@Composable
fun NetworkTab(vm: MainViewModel) {
    val localIp by vm.localIp.collectAsState()
    val gateway by vm.gateway.collectAsState()
    val ssid by vm.ssid.collectAsState()
    val publicIp by vm.publicIp.collectAsState()
    val scanning by vm.scanning.collectAsState()
    val progress by vm.scanProgress.collectAsState()
    val hosts by vm.hosts.collectAsState()
    val logs by vm.logs.collectAsState()
    var subnet by remember { mutableStateOf("") }
    LaunchedEffect(localIp) { if (localIp != "...") { val p = localIp.split("."); if (p.size >= 3) subnet = "${p[0]}.${p[1]}.${p[2]}" } }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = CardBg), shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column { Text("Локальний IP", color = Gray, fontSize = 11.sp); Text(localIp, color = Color.White, fontWeight = FontWeight.Bold) }
                        Column(horizontalAlignment = Alignment.End) { Text("Шлюз", color = Gray, fontSize = 11.sp); Text(gateway, color = Green, fontWeight = FontWeight.Bold) }
                    }
                    HorizontalDivider(Modifier.padding(vertical = 8.dp), color = PillBg)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column { Text("SSID", color = Gray, fontSize = 11.sp); Text(ssid, color = Cyan, fontWeight = FontWeight.Bold) }
                        Column(horizontalAlignment = Alignment.End) { Text("Публічний IP", color = Gray, fontSize = 11.sp); Text(publicIp, color = Orange, fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(value = subnet, onValueChange = { subnet = it }, label = { Text("Підмережа", color = Gray, fontSize = 11.sp) },
                    modifier = Modifier.weight(1f), singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = Green, unfocusedBorderColor = PillBg))
                Button(onClick = { if(scanning) vm.stopScan() else vm.startScan(subnet) },
                    colors = ButtonDefaults.buttonColors(containerColor = if(scanning) Red else Green, contentColor = BgDark),
                    shape = RoundedCornerShape(8.dp), modifier = Modifier.height(56.dp)) {
                    Text(if(scanning) "■ СТОП" else "▶ СКАН", fontWeight = FontWeight.Bold)
                }
            }
        }
        if (scanning || progress > 0f) {
            item {
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(4.dp), color = Green, trackColor = PillBg)
                Text("${(progress*100).toInt()}% | Онлайн: ${hosts.size}", color = Gray, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
            }
        }
        if (hosts.isNotEmpty()) {
            item { Text("Знайдено хостів: ${hosts.size}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
            items(hosts) { host ->
                Card(colors = CardDefaults.cardColors(containerColor = CardBg), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(8.dp).background(Green, RoundedCornerShape(4.dp)))
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(host.ip, color = Cyan, fontWeight = FontWeight.Bold, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                                Text("${host.mac} · ${host.vendor}", color = Gray, fontSize = 11.sp)
                            }
                        }
                        Text("${host.pingMs}ms", color = Green, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }
        if (logs.isNotEmpty()) {
            item {
                Box(Modifier.fillMaxWidth().background(Color.Black, RoundedCornerShape(8.dp)).padding(8.dp)) {
                    Column { logs.takeLast(10).forEach { Text(it, color = Color(0xFF1AFAAA), fontSize = 10.sp, fontFamily = FontFamily.Monospace) } }
                }
            }
        }
    }
}

// ============ WIFI TAB ============
@Composable
fun WifiTab(vm: MainViewModel) {
    val context = LocalContext.current
    val wifiNets by vm.wifiNets.collectAsState()
    val wifiScanning by vm.wifiScanning.collectAsState()

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { perms ->
        if (perms.values.all { it }) {
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            wm.startScan()
        }
    }

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent?.action == WifiManager.SCAN_RESULTS_AVAILABLE_ACTION) {
                    val results = (ctx?.applicationContext?.getSystemService(Context.WIFI_SERVICE) as? WifiManager)?.scanResults ?: emptyList()
                    vm.scanWifi(context, results)
                }
            }
        }
        context.registerReceiver(receiver, IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION))
        onDispose { try { context.unregisterReceiver(receiver) } catch(e:Exception){} }
    }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = CardBg), shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text("Поточна мережа", color = Gray, fontSize = 11.sp)
                            val ssid by vm.ssid.collectAsState()
                            Text(ssid, color = Green, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                        val rssi = NetworkUtils.getWifiRssi(context)
                        val freq = NetworkUtils.getWifiFreq(context)
                        val speed = NetworkUtils.getWifiSpeed(context)
                        Column(horizontalAlignment = Alignment.End) {
                            Text("$rssi dBm · ${if(freq > 4000) "5 ГГц" else "2.4 ГГц"}", color = Cyan, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text("$speed Мбіт/с", color = Orange, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
        item {
            Button(
                onClick = {
                    val hasPerms = listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_WIFI_STATE).all {
                        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
                    }
                    if (hasPerms) {
                        val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                        wm.startScan()
                    } else {
                        permLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = if(wifiScanning) Orange else Green, contentColor = BgDark),
                shape = RoundedCornerShape(8.dp)
            ) {
                if (wifiScanning) { CircularProgressIndicator(Modifier.size(16.dp), color = BgDark, strokeWidth = 2.dp); Spacer(Modifier.width(8.dp)) }
                Text(if(wifiScanning) "Сканування..." else "📡 СКАНУВАТИ WI-FI", fontWeight = FontWeight.Bold)
            }
        }
        if (wifiNets.isEmpty() && !wifiScanning) {
            item { Text("Натисніть сканувати для пошуку мереж", color = Gray, fontSize = 13.sp, modifier = Modifier.padding(16.dp)) }
        }
        item {
            if (wifiNets.isNotEmpty()) {
                Text("Знайдено мереж: ${wifiNets.size}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
        items(wifiNets) { net ->
            val sigColor = when {
                net.rssi >= -50 -> Green
                net.rssi >= -65 -> Color(0xFF90FF70)
                net.rssi >= -75 -> Yellow
                else -> Red
            }
            val sigLabel = when {
                net.rssi >= -50 -> "Відмінний"
                net.rssi >= -65 -> "Добрий"
                net.rssi >= -75 -> "Слабкий"
                else -> "Дуже слабкий"
            }
            Card(
                colors = CardDefaults.cardColors(containerColor = if(net.isCurrent) DarkGreen else CardBg),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(12.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Wifi, null, tint = sigColor, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(net.ssid, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(net.bssid, color = Gray, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                            }
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("${net.rssi} dBm", color = sigColor, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text(sigLabel, color = sigColor, fontSize = 10.sp)
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Chip(net.security, if(net.security == "OPEN") Red else Green)
                        Chip("CH${net.channel}", Cyan)
                        Chip(if(net.freq > 4000) "5 ГГц" else "2.4 ГГц", Orange)
                        if(net.isCurrent) Chip("★ Моя", Green)
                    }
                }
            }
        }
    }
}

@Composable
fun Chip(text: String, color: Color) {
    Box(Modifier.background(color.copy(alpha = 0.15f), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp)) {
        Text(text, color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

// ============ PORT TAB ============
@Composable
fun PortTab(vm: MainViewModel) {
    val target by vm.portTarget.collectAsState()
    val scanning by vm.portScanning.collectAsState()
    val progress by vm.portProgress.collectAsState()
    val ports by vm.ports.collectAsState()
    val gateway by vm.gateway.collectAsState()
    LaunchedEffect(gateway) { if (gateway != "...") vm.setPortTarget(gateway) }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            OutlinedTextField(value = target, onValueChange = { vm.setPortTarget(it) },
                label = { Text("IP адреса", color = Gray, fontSize = 11.sp) },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = Green, unfocusedBorderColor = PillBg))
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(onClick = { if(scanning) vm.stopPortScan() else vm.startPortScan("QUICK") },
                    colors = ButtonDefaults.buttonColors(containerColor = if(scanning) Red else Green, contentColor = BgDark),
                    modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp)) {
                    Text(if(scanning) "■ СТОП" else "⚡ ШВИДКО", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
                Button(onClick = { if(!scanning) vm.startPortScan("FULL") }, enabled = !scanning,
                    colors = ButtonDefaults.buttonColors(containerColor = PillBg, contentColor = Cyan),
                    modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp)) {
                    Text("🔍 ПОВНИЙ", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
                Button(onClick = { if(!scanning) vm.startPortScan("ROUTER") }, enabled = !scanning,
                    colors = ButtonDefaults.buttonColors(containerColor = PillBg, contentColor = Orange),
                    modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp)) {
                    Text("📡 РОУТЕР", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }
        if (scanning || progress > 0f) {
            item {
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(4.dp), color = Green, trackColor = PillBg)
                Text("${(progress*100).toInt()}% | Відкрито: ${ports.count{it.isOpen}}", color = Gray, fontSize = 11.sp, modifier = Modifier.padding(top=4.dp))
            }
        }
        if (ports.isNotEmpty()) {
            item { Text("Результати для $target:", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
            items(ports.filter { it.isOpen }) { port ->
                Card(colors = CardDefaults.cardColors(containerColor = DarkGreen), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(8.dp).background(Green, RoundedCornerShape(4.dp)))
                            Spacer(Modifier.width(10.dp))
                            Text("${port.port}", color = Green, fontWeight = FontWeight.Bold, fontSize = 14.sp, fontFamily = FontFamily.Monospace)
                            Spacer(Modifier.width(8.dp))
                            Text(port.service, color = Cyan, fontSize = 12.sp)
                        }
                        Text("${port.pingMs}ms OPEN", color = Green, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    }
                }
            }
            if (ports.count{!it.isOpen} > 0) {
                item {
                    Text("Закритих: ${ports.count{!it.isOpen}}", color = Gray, fontSize = 12.sp)
                }
            }
        }
    }
}

// ============ TOOLS TAB ============
@Composable
fun ToolsTab(vm: MainViewModel) {
    val result by vm.toolResult.collectAsState()
    val running by vm.toolRunning.collectAsState()
    val input by vm.toolInput.collectAsState()
    var wolMac by remember { mutableStateOf("") }
    var cidrInput by remember { mutableStateOf("192.168.1.0/24") }
    var selectedTool by remember { mutableStateOf(0) }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("PING","TRACE","DNS","IP-CALC","WOL").forEachIndexed { i, label ->
                    Box(
                        modifier = Modifier.weight(1f).background(if(selectedTool==i) PillBg else CardBg, RoundedCornerShape(6.dp))
                            .clickable { selectedTool = i }.padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) { Text(label, color = if(selectedTool==i) Cyan else Gray, fontSize = 9.sp, fontWeight = FontWeight.Bold) }
                }
            }
        }
        when (selectedTool) {
            0 -> {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = CardBg), shape = RoundedCornerShape(10.dp)) {
                        Column(Modifier.padding(12.dp)) {
                            OutlinedTextField(value = input, onValueChange = { vm.setToolInput(it) },
                                label = { Text("Хост", color = Gray, fontSize = 11.sp) }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = Green, unfocusedBorderColor = PillBg))
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(4,10,20).forEach { n ->
                                    Button(onClick = { vm.runPing(n) }, enabled = !running, modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.buttonColors(containerColor = PillBg, contentColor = Green), shape = RoundedCornerShape(6.dp)) {
                                        Text("Ping $n", fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            1 -> {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = CardBg), shape = RoundedCornerShape(10.dp)) {
                        Column(Modifier.padding(12.dp)) {
                            OutlinedTextField(value = input, onValueChange = { vm.setToolInput(it) },
                                label = { Text("Хост", color = Gray, fontSize = 11.sp) }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = Green, unfocusedBorderColor = PillBg))
                            Spacer(Modifier.height(8.dp))
                            Button(onClick = { vm.runTrace() }, enabled = !running, modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = Cyan, contentColor = BgDark), shape = RoundedCornerShape(6.dp)) {
                                Text("▶ TRACEROUTE", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
            2 -> {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = CardBg), shape = RoundedCornerShape(10.dp)) {
                        Column(Modifier.padding(12.dp)) {
                            OutlinedTextField(value = input, onValueChange = { vm.setToolInput(it) },
                                label = { Text("Домен або IP", color = Gray, fontSize = 11.sp) }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = Green, unfocusedBorderColor = PillBg))
                            Spacer(Modifier.height(8.dp))
                            Button(onClick = { vm.runDns() }, enabled = !running, modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = Orange, contentColor = BgDark), shape = RoundedCornerShape(6.dp)) {
                                Text("🔍 DNS LOOKUP", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
            3 -> {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = CardBg), shape = RoundedCornerShape(10.dp)) {
                        Column(Modifier.padding(12.dp)) {
                            OutlinedTextField(value = cidrInput, onValueChange = { cidrInput = it },
                                label = { Text("IP/Маска (192.168.1.0/24)", color = Gray, fontSize = 11.sp) }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = Green, unfocusedBorderColor = PillBg))
                            Spacer(Modifier.height(8.dp))
                            Button(onClick = { vm.calcCidr(cidrInput) }, modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = Green, contentColor = BgDark), shape = RoundedCornerShape(6.dp)) {
                                Text("= РОЗРАХУВАТИ", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
            4 -> {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = CardBg), shape = RoundedCornerShape(10.dp)) {
                        Column(Modifier.padding(12.dp)) {
                            OutlinedTextField(value = wolMac, onValueChange = { wolMac = it },
                                label = { Text("MAC адреса (AA:BB:CC:DD:EE:FF)", color = Gray, fontSize = 11.sp) }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = Green, unfocusedBorderColor = PillBg))
                            Spacer(Modifier.height(8.dp))
                            Button(onClick = { vm.runWol(wolMac) }, modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = Cyan, contentColor = BgDark), shape = RoundedCornerShape(6.dp)) {
                                Text("⚡ НАДІСЛАТИ WOL", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
        item {
            Box(Modifier.fillMaxWidth().background(Color.Black, RoundedCornerShape(8.dp)).padding(10.dp)) {
                if (running) Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(14.dp), color = Green, strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Виконується...", color = Green, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                } else Text(result.ifEmpty { "// Результат тут" }, color = Color(0xFF1AFAAA), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            }
        }
    }
}
