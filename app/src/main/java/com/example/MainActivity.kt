package com.example

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(),
                    containerColor = CyberSlateBg
                ) { innerPadding ->
                    RouterScanApp(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouterScanApp(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val app = context.applicationContext as Application
    val viewModel: MainViewModel = viewModel(factory = androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory(app))
    val currentScreen by viewModel.currentScreen.collectAsState()
    val isUkrainian by viewModel.isUkrainian.collectAsState()
    val disclaimerAccepted by viewModel.disclaimerAccepted.collectAsState()

    if (!disclaimerAccepted) {
        AlertDialog(
            onDismissRequest = {},
            properties = androidx.compose.ui.window.DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
            containerColor = CyberCardBg,
            shape = RoundedCornerShape(16.dp),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Warning, contentDescription = null, tint = CyberAmber, modifier = Modifier.size(28.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(Translations.disclaimerTitle, color = CustomWhite, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
            },
            text = {
                val hasPermissionState = remember { mutableStateOf(false) }
                Column {
                    Text(Translations.disclaimerWarning, color = PremiumGray, fontSize = 14.sp, lineHeight = 20.sp)
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { hasPermissionState.value = !hasPermissionState.value }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = hasPermissionState.value,
                            onCheckedChange = { hasPermissionState.value = it },
                            colors = CheckboxDefaults.colors(checkedColor = NeonGreen, uncheckedColor = PremiumGray, checkmarkColor = CyberSlateBg)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(Translations.disclaimerCheckbox, color = CustomWhite, fontSize = 13.sp)
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        enabled = hasPermissionState.value,
                        onClick = { viewModel.acceptDisclaimer() },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = NeonGreen, contentColor = CyberSlateBg, disabledContainerColor = ActivePillBg, disabledContentColor = PremiumGray)
                    ) {
                        Text(Translations.disclaimerBtn, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }
            },
            confirmButton = {}
        )
    }

    Column(modifier = modifier.fillMaxSize().background(CyberSlateBg)) {
        TopAppBar(
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Security, contentDescription = null, tint = NeonGreen, modifier = Modifier.size(30.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text("RouterScan", color = CustomWhite, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
                        Text("LAN Cybersecurity Suite", color = PremiumGray, fontSize = 11.sp)
                    }
                }
            },
            actions = {
                IconButton(onClick = { viewModel.toggleLanguage() }) {
                    Text(if (isUkrainian) "UA" else "EN", color = NeonGreen, fontWeight = FontWeight.Bold, fontSize = 15.sp,
                        modifier = Modifier.background(ActivePillBg, RoundedCornerShape(8.dp)).padding(6.dp))
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = CyberCardBg, titleContentColor = CustomWhite)
        )

        Box(modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp)) {
            when (currentScreen) {
                0 -> SubnetScanScreen(viewModel)
                1 -> WifiAnalyzerScreen(viewModel)
                2 -> PortScanScreen(viewModel)
                3 -> UtilitiesScreen(viewModel)
                4 -> ReportsHistoryScreen(viewModel)
            }
        }

        NavigationBar(containerColor = CyberCardBg, tonalElevation = 8.dp, modifier = Modifier.height(72.dp)) {
            val items = listOf(
                Triple(0, Icons.Filled.CompassCalibration, Translations.titleNetworkScan),
                Triple(1, Icons.Filled.Wifi, Translations.titleWifiAnalyzer),
                Triple(2, Icons.Filled.Adjust, Translations.titlePortScan),
                Triple(3, Icons.Filled.Build, Translations.titleUtilities),
                Triple(4, Icons.Filled.History, Translations.titleReports)
            )
            items.forEach { (index, icon, label) ->
                NavigationBarItem(
                    selected = currentScreen == index,
                    onClick = { viewModel.setScreen(index) },
                    icon = { Icon(icon, contentDescription = label, tint = if (currentScreen == index) NeonGreen else PremiumGray) },
                    label = { Text(label, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = if (currentScreen == index) NeonGreen else PremiumGray) },
                    colors = NavigationBarItemDefaults.colors(indicatorColor = ActivePillBg)
                )
            }
        }
    }
}

@Composable
fun SubnetScanScreen(viewModel: MainViewModel) {
    val isScanning by viewModel.isSubnetScanning.collectAsState()
    val progress by viewModel.subnetScanProgress.collectAsState()
    val hosts by viewModel.scannedHosts.collectAsState()
    val searchQuery by viewModel.subnetSearchQuery.collectAsState()
    val filterOnlineOnly by viewModel.subnetFilterOnlineOnly.collectAsState()
    val publicWan by viewModel.publicWANIp.collectAsState()
    var customSubnetInput by remember { mutableStateOf(viewModel.localIp) }

    Column(modifier = Modifier.fillMaxSize()) {
        Spacer(modifier = Modifier.height(12.dp))
        Card(colors = CardDefaults.cardColors(containerColor = CyberCardBg), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text(Translations.internalIp, color = PremiumGray, fontSize = 11.sp)
                        Text(viewModel.localIp, color = CustomWhite, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(Translations.mySubnetLabel, color = PremiumGray, fontSize = 11.sp)
                        Text("${viewModel.gatewayIp}/24", color = NeonGreen, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = ActivePillBg)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text(Translations.ssid, color = PremiumGray, fontSize = 11.sp)
                        Text(viewModel.currentSSID, color = CyberTeal, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(Translations.externalIpLabel, color = PremiumGray, fontSize = 11.sp)
                        Text(publicWan, color = CyberAmber, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = customSubnetInput,
                onValueChange = { customSubnetInput = it },
                label = { Text(Translations.scanRangesLabel, color = PremiumGray, fontSize = 11.sp) },
                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = CustomWhite, unfocusedTextColor = CustomWhite, focusedBorderColor = NeonGreen, unfocusedBorderColor = ActivePillBg),
                shape = RoundedCornerShape(8.dp),
                textStyle = TextStyle(fontSize = 13.sp),
                modifier = Modifier.weight(1f),
                singleLine = true
            )
            Button(
                onClick = { if (isScanning) viewModel.stopSubnetScan() else viewModel.startSubnetScan(customSubnetInput) },
                colors = ButtonDefaults.buttonColors(containerColor = if (isScanning) CyberRed else NeonGreen, contentColor = CyberSlateBg),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.height(52.dp).width(105.dp)
            ) {
                Text(if (isScanning) Translations.stop else Translations.start, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.setSubnetSearchQuery(it) },
                placeholder = { Text(Translations.placeholderSearch, color = PremiumGray, fontSize = 11.sp) },
                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = CustomWhite, unfocusedTextColor = CustomWhite, focusedBorderColor = CyberTeal, unfocusedBorderColor = ActivePillBg),
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = PremiumGray) },
                shape = RoundedCornerShape(8.dp),
                textStyle = TextStyle(fontSize = 12.sp),
                modifier = Modifier.weight(1f).height(50.dp),
                singleLine = true
            )
            IconButton(
                onClick = { viewModel.setSubnetFilterOnlineOnly(!filterOnlineOnly) },
                modifier = Modifier.background(if (filterOnlineOnly) DarkCyberGreen else ActivePillBg, RoundedCornerShape(8.dp)).size(50.dp)
            ) {
                Icon(if (filterOnlineOnly) Icons.Filled.FilterAlt else Icons.Filled.FilterAltOff, contentDescription = null, tint = if (filterOnlineOnly) NeonGreen else PremiumGray)
            }
        }
        if (isScanning || progress > 0f) {
            Spacer(modifier = Modifier.height(10.dp))
            Column {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("${Translations.scanProgress}: ${(progress * 100).toInt()}%", color = NeonGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    CircularProgressIndicator(progress = { progress }, modifier = Modifier.size(16.dp), color = NeonGreen, strokeWidth = 2.dp)
                }
                Spacer(modifier = Modifier.height(4.dp))
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(4.dp), color = NeonGreen, trackColor = ActivePillBg)
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(String.format(Translations.infoSummary, hosts.filter { it.isOnline }.size), color = CustomWhite, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Button(onClick = { viewModel.saveSubnetScan() }, colors = ButtonDefaults.buttonColors(containerColor = ActivePillBg, contentColor = NeonGreen), shape = RoundedCornerShape(8.dp)) {
                Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(Translations.save, fontSize = 12.sp)
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        val filteredHosts = hosts.filter { host ->
            val matchesSearch = host.ip.contains(searchQuery) || host.vendor.contains(searchQuery, ignoreCase = true) || host.hostname.contains(searchQuery, ignoreCase = true)
            val matchesOnline = if (filterOnlineOnly) host.isOnline else true
            matchesSearch && matchesOnline
        }
        LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(filteredHosts) { host ->
                Card(colors = CardDefaults.cardColors(containerColor = if (host.isOnline) CyberCardBg else CyberCardBg.copy(alpha = 0.5f)), shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(10.dp).background(if (host.isOnline) NeonGreen else CyberRed, shape = RoundedCornerShape(5.dp)))
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(host.ip, color = if (host.isOnline) CustomWhite else PremiumGray, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("MAC: ${host.macAddress}", color = PremiumGray, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                                Text("Vendor: ${host.vendor}", color = CyberTeal, fontSize = 11.sp)
                            }
                        }
                        if (host.isOnline) {
                            Column(horizontalAlignment = Alignment.End) {
                                Text("${host.pingMs ?: 0} ms", color = NeonGreen, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text(Translations.hostOnline, color = NeonGreen, fontSize = 10.sp)
                            }
                        } else {
                            Text(Translations.hostOffline, color = PremiumGray, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun WifiAnalyzerScreen(viewModel: MainViewModel) {
    val apList by viewModel.scannedAccessPoints.collectAsState()
    val selectedAp by viewModel.selectedApForWps.collectAsState()
    val isSimulating by viewModel.isWpsSimulating.collectAsState()
    val wpsLogs by viewModel.wpsLogs.collectAsState()
    val context = LocalContext.current

    LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Spacer(modifier = Modifier.height(10.dp))
            Card(colors = CardDefaults.cardColors(containerColor = CyberCardBg), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.NetworkWifi, contentDescription = null, tint = NeonGreen)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(Translations.panelConnection, color = CustomWhite, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = ActivePillBg)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(Translations.ssid + ":", color = PremiumGray, fontSize = 12.sp)
                        Text(com.example.network.NetworkUtils.getWifiSsid(context), color = CustomWhite, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(Translations.bssid + ":", color = PremiumGray, fontSize = 12.sp)
                        Text(com.example.network.NetworkUtils.getWifiBssid(context), color = CustomWhite, fontSize = 12.sp)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(Translations.signalStrength + ":", color = PremiumGray, fontSize = 12.sp)
                        val dbm = com.example.network.NetworkUtils.getWifiRssiDbm(context)
                        Text("$dbm dBm", color = if (dbm > -65) NeonGreen else CyberAmber, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(Translations.linkSpeed + ":", color = PremiumGray, fontSize = 12.sp)
                        Text("${com.example.network.NetworkUtils.getWifiLinkSpeed(context)} Mbps", color = CyberTeal, fontSize = 12.sp)
                    }
                }
            }
        }

        items(apList) { item ->
            val isSelectedStatus = selectedAp?.bssid == item.bssid
            Card(
                colors = CardDefaults.cardColors(containerColor = if (isSelectedStatus) ActivePillBg else CyberCardBg),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth().clickable { viewModel.selectApForWps(item) }
            ) {
                Row(modifier = Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text(item.ssid, color = CustomWhite, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text("BSSID: ${item.bssid}", color = PremiumGray, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Ch: ${item.channel}", color = CyberTeal, fontSize = 11.sp)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Signal: ${item.rssi} dBm", color = if (item.rssi > -60) NeonGreen else CyberAmber, fontSize = 11.sp)
                        }
                    }
                    Box(modifier = Modifier.background(if (item.riskRating == "HIGH") CyberRed else NeonGreen, RoundedCornerShape(4.dp)).padding(6.dp)) {
                        Text(if (item.riskRating == "HIGH") "VULN" else "SAFE", color = CyberSlateBg, fontWeight = FontWeight.ExtraBold, fontSize = 9.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun PortScanScreen(viewModel: MainViewModel) {
    val targetIp by viewModel.portScanTarget.collectAsState()
    val isScanning by viewModel.isPortScanning.collectAsState()
    val progress by viewModel.portProgress.collectAsState()
    val portList by viewModel.portScanList.collectAsState()
    var hostIpInput by remember { mutableStateOf(targetIp) }

    Column(modifier = Modifier.fillMaxSize()) {
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = hostIpInput,
            onValueChange = { hostIpInput = it; viewModel.setPortScanTarget(it) },
            label = { Text(Translations.targetIpLabel, color = PremiumGray, fontSize = 11.sp) },
            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = CustomWhite, unfocusedTextColor = CustomWhite, focusedBorderColor = NeonGreen, unfocusedBorderColor = ActivePillBg),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(modifier = Modifier.height(10.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { viewModel.runPortScan("QUICK") }, enabled = !isScanning, modifier = Modifier.weight(1f).height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = CyberTeal, contentColor = Color.White), shape = RoundedCornerShape(8.dp)) {
                Text(Translations.modeQuick, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            Button(onClick = { viewModel.runPortScan("FULL") }, enabled = !isScanning, modifier = Modifier.weight(1f).height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ActivePillBg, contentColor = NeonGreen), shape = RoundedCornerShape(8.dp)) {
                Text(Translations.modeFull, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            if (isScanning) {
                Button(onClick = { viewModel.stopPortScan() }, modifier = Modifier.height(48.dp).width(80.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = CyberRed), shape = RoundedCornerShape(8.dp)) {
                    Text(Translations.stop, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        if (isScanning || (progress > 0f && progress < 1f)) {
            Spacer(modifier = Modifier.height(12.dp))
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(4.dp), color = NeonGreen, trackColor = ActivePillBg)
        }
        Spacer(modifier = Modifier.height(14.dp))
        val openPorts = portList.filter { it.isOpen }
        Text(String.format(Translations.openPortsDetected, openPorts.size), color = CustomWhite, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        Spacer(modifier = Modifier.height(8.dp))
        LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(portList) { status ->
                Card(colors = CardDefaults.cardColors(containerColor = if (status.isOpen) ActivePillBg else CyberCardBg.copy(alpha = 0.4f)),
                    shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(8.dp).background(if (status.isOpen) NeonGreen else PremiumGray.copy(alpha = 0.5f), shape = RoundedCornerShape(4.dp)))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Port: ${status.port}", color = CustomWhite, fontWeight = FontWeight.Bold, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(status.service, color = CyberTeal, fontSize = 11.sp)
                        }
                        if (status.isOpen) Text("OPEN (${status.pingMs ?: 0}ms)", color = NeonGreen, fontWeight = FontWeight.Bold, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                        else Text("CLOSED", color = PremiumGray, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }
    }
}

@Composable
fun UtilitiesScreen(viewModel: MainViewModel) {
    val toolInput by viewModel.toolInput.collectAsState()
    val terminalResult by viewModel.toolResultText.collectAsState()
    val isRunning by viewModel.isToolRunning.collectAsState()
    var innerInput by remember { mutableStateOf(toolInput) }
    var wolMacInput by remember { mutableStateOf("00:11:2F:A9:E1:CB") }
    var cidrInput by remember { mutableStateOf("192.168.1.0/24") }

    LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedTextField(
                value = innerInput,
                onValueChange = { innerInput = it; viewModel.setToolInput(it) },
                label = { Text(Translations.address, color = PremiumGray, fontSize = 11.sp) },
                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = CustomWhite, unfocusedTextColor = CustomWhite, focusedBorderColor = CyberTeal, unfocusedBorderColor = ActivePillBg),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }
        item {
            Column {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Button(onClick = { viewModel.runPacketPing(5) }, enabled = !isRunning, modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = CyberCardBg, contentColor = NeonGreen), shape = RoundedCornerShape(6.dp)) {
                        Text("Ping (5 pkts)", fontSize = 11.sp)
                    }
                    Button(onClick = { viewModel.runVisualTraceroute() }, enabled = !isRunning, modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = CyberCardBg, contentColor = CyberTeal), shape = RoundedCornerShape(6.dp)) {
                        Text("Traceroute", fontSize = 11.sp)
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Button(onClick = { viewModel.runDnsLookup() }, enabled = !isRunning, modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = CyberCardBg, contentColor = CyberAmber), shape = RoundedCornerShape(6.dp)) {
                        Text("DNS Lookup", fontSize = 11.sp)
                    }
                    Button(onClick = { viewModel.runWhoisQuery() }, enabled = !isRunning, modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = CyberCardBg, contentColor = CustomWhite), shape = RoundedCornerShape(6.dp)) {
                        Text("Whois", fontSize = 11.sp)
                    }
                }
            }
        }
        item {
            Box(modifier = Modifier.fillMaxWidth().height(180.dp).background(Color.Black, RoundedCornerShape(8.dp)).padding(10.dp)) {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    item {
                        Text(terminalResult.ifEmpty { "Ready. Choose a tool above." }, color = TerminalText, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                    }
                }
                if (isRunning) CircularProgressIndicator(color = NeonGreen, modifier = Modifier.size(20.dp).align(Alignment.TopEnd))
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = CyberCardBg), shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("IP Subnet Calculator (CIDR)", color = CustomWhite, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = cidrInput, onValueChange = { cidrInput = it }, modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(6.dp), colors = OutlinedTextFieldDefaults.colors(focusedTextColor = CustomWhite, unfocusedTextColor = CustomWhite, focusedBorderColor = NeonGreen, unfocusedBorderColor = ActivePillBg),
                        textStyle = TextStyle(fontSize = 12.sp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = { viewModel.calculateSubnetCidr(cidrInput) }, modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(6.dp), colors = ButtonDefaults.buttonColors(containerColor = NeonGreen, contentColor = CyberSlateBg)) {
                        Text("Calculate Subnet Info", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = CyberCardBg), shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Wake-on-LAN (WOL)", color = CustomWhite, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = wolMacInput, onValueChange = { wolMacInput = it }, modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(6.dp), colors = OutlinedTextFieldDefaults.colors(focusedTextColor = CustomWhite, unfocusedTextColor = CustomWhite, focusedBorderColor = NeonGreen, unfocusedBorderColor = ActivePillBg),
                        textStyle = TextStyle(fontSize = 12.sp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = { viewModel.dispatchWakeOnLan(wolMacInput) }, modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(6.dp), colors = ButtonDefaults.buttonColors(containerColor = CyberTeal, contentColor = Color.White)) {
                        Text(Translations.wolBtn, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun ReportsHistoryScreen(viewModel: MainViewModel) {
    val historyLogs by viewModel.scanHistory.collectAsState()
    val selectedLog by viewModel.selectedHistoryLog.collectAsState()
    val context = LocalContext.current
    val simpleDateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()) }

    Column(modifier = Modifier.fillMaxSize()) {
        Spacer(modifier = Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Audit Logs:", color = CustomWhite, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Button(onClick = { viewModel.clearAllLogs() }, colors = ButtonDefaults.buttonColors(containerColor = CyberRed.copy(alpha = 0.2f), contentColor = CyberRed), shape = RoundedCornerShape(8.dp)) {
                Text(Translations.clear, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        if (historyLogs.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(Translations.noHistoryText, color = PremiumGray, textAlign = TextAlign.Center, modifier = Modifier.padding(20.dp), fontSize = 13.sp)
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(historyLogs) { itemLog ->
                    Card(colors = CardDefaults.cardColors(containerColor = CyberCardBg), shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().clickable { viewModel.selectHistoryLog(itemLog) }) {
                        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(itemLog.logType + " scan", color = CustomWhite, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                Text("Target: ${itemLog.target}", color = PremiumGray, fontSize = 11.sp)
                                Text(simpleDateFormat.format(Date(itemLog.timestamp)), color = PremiumGray, fontSize = 9.sp)
                            }
                            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = PremiumGray)
                        }
                    }
                }
            }
        }
    }
}
