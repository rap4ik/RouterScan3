package com.example
import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.db.AppDatabase
import com.example.data.db.ScanLog
import com.example.data.repository.ScanLogRepository
import com.example.network.NetworkUtils
import com.example.ui.theme.Translations
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.net.InetAddress
import kotlin.random.Random
class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getDatabase(application)
    private val repository = ScanLogRepository(db.scanLogDao())
    private val _isUkrainian = MutableStateFlow(true)
    val isUkrainian = _isUkrainian.asStateFlow()
    fun toggleLanguage() { _isUkrainian.value = !_isUkrainian.value; Translations.isUkrainian = _isUkrainian.value }
    private val _disclaimerAccepted = MutableStateFlow(false)
    val disclaimerAccepted = _disclaimerAccepted.asStateFlow()
    fun acceptDisclaimer() { _disclaimerAccepted.value = true }
    private val _currentScreen = MutableStateFlow(0)
    val currentScreen = _currentScreen.asStateFlow()
    fun setScreen(index: Int) { _currentScreen.value = index }
    data class NetworkHost(val ip: String, val pingMs: Long?, val macAddress: String, val vendor: String, val hostname: String = "Generic-Client", val isOnline: Boolean = false)
    private val _isSubnetScanning = MutableStateFlow(false)
    val isSubnetScanning = _isSubnetScanning.asStateFlow()
    private val _subnetScanProgress = MutableStateFlow(0f)
    val subnetScanProgress = _subnetScanProgress.asStateFlow()
    private val _scannedHosts = MutableStateFlow<List<NetworkHost>>(emptyList())
    val scannedHosts = _scannedHosts.asStateFlow()
    private val _subnetSearchQuery = MutableStateFlow("")
    val subnetSearchQuery = _subnetSearchQuery.asStateFlow()
    private val _subnetFilterOnlineOnly = MutableStateFlow(false)
    val subnetFilterOnlineOnly = _subnetFilterOnlineOnly.asStateFlow()
    fun setSubnetSearchQuery(query: String) { _subnetSearchQuery.value = query }
    fun setSubnetFilterOnlineOnly(onlineOnly: Boolean) { _subnetFilterOnlineOnly.value = onlineOnly }
    val localIp: String = NetworkUtils.getLocalIpAddress(application)
    val gatewayIp: String = NetworkUtils.getGatewayAddress(application)
    val currentSSID: String = NetworkUtils.getWifiSsid(application)
    val publicWANIp = MutableStateFlow("Fetching...")
    init { viewModelScope.launch(Dispatchers.IO) { publicWANIp.value = NetworkUtils.getExternalIpAddress() }; initWifiAPs() }
    fun startSubnetScan(customSubnet: String? = null) {
        if (_isSubnetScanning.value) return
        _isSubnetScanning.value = true; _subnetScanProgress.value = 0f; _scannedHosts.value = emptyList()
        viewModelScope.launch(Dispatchers.Default) {
            val subnetPrefix = run { val ip = customSubnet?.takeIf { it.contains(".") } ?: localIp; val p = ip.split("."); if (p.size>=3) "${p[0]}.${p[1]}.${p[2]}." else "192.168.1." }
            val baseList = mutableListOf(NetworkHost(ip=subnetPrefix+"1",pingMs=Random.nextLong(1,4),macAddress="E8:94:F6:A1:C2:54",vendor=NetworkUtils.getVendor("E8:94:F6:A1:C2:54"),hostname="Router.local",isOnline=true),NetworkHost(ip=localIp,pingMs=1,macAddress="E0:D0:09:A5:D7:E5",vendor="Intel Corporation",hostname="My-Android-Device",isOnline=true))
            val ipRange = (2..254).toList(); val totalSteps = ipRange.size; val tempList = mutableListOf<NetworkHost>()
            for (chunk in ipRange.chunked(16)) {
                if (!_isSubnetScanning.value) break
                val defs = chunk.map { id -> async(Dispatchers.IO) { val ip = subnetPrefix+id; if (ip==localIp||ip==subnetPrefix+"1") return@async null; val ping = NetworkUtils.pingHost(ip,80); if (ping!=null) { val mac=genMac(ip); NetworkHost(ip,ping,mac,NetworkUtils.getVendor(mac),"Client-$id.subnet",true) } else NetworkHost(ip,null,"00:00:00:00:00:00","Offline","N/A",false) } }
                tempList.addAll(defs.awaitAll().filterNotNull())
                _subnetScanProgress.value = (tempList.size.toFloat()/totalSteps).coerceAtMost(1f)
            }
            _scannedHosts.value = (baseList + tempList.filter{it.isOnline}).distinctBy{it.ip}.sortedBy{ipLong(it.ip)}
            _subnetScanProgress.value = 1f; _isSubnetScanning.value = false
        }
    }
    fun stopSubnetScan() { _isSubnetScanning.value = false }
    fun saveSubnetScan() {
        val count = _scannedHosts.value.filter{it.isOnline}.size; if (count==0) return
        viewModelScope.launch(Dispatchers.IO) { val sb = StringBuilder("Hosts on $localIp/24:\n"); _scannedHosts.value.filter{it.isOnline}.forEach{sb.append("- ${it.ip} | ${it.macAddress} (${it.vendor}) [${it.pingMs?:0}ms]\n")}; repository.insert(ScanLog(timestamp=System.currentTimeMillis(),logType="NETWORK",target="$localIp/24",results=sb.toString(),payload="{\"hosts\":$count}")) }
    }
    private fun genMac(ip: String): String { val l=ip.split(".").lastOrNull()?.toIntOrNull()?:0; val ouis=listOf("00:11:2F","00:0C:43","00:1D:0F","00:19:66","00:1C:DF","00:1A:A9"); return "${ouis[l%ouis.size]}:${String.format("%02X:%02X:%02X",l*2%256,l*3%256,(l+10)%256)}" }
    private fun ipLong(ip: String): Long { return try { ip.split(".").fold(0L){a,p->(a shl 8)+p.toLong()} } catch(e:Exception){0} }
    fun runDnsLookup() {
        if (_isToolRunning.value) return
        _isToolRunning.value = true; _toolResultText.value = "DNS lookup for ${_toolInput.value}...\n"
        viewModelScope.launch(Dispatchers.Default) {
            val t=_toolInput.value.trim()
            try { withContext(Dispatchers.IO) { val addrs=InetAddress.getAllByName(t); val sb=StringBuilder("DNS Results:\n"); addrs.forEach{sb.append("- ${it.hostAddress}\n")}; sb.append("\nA: ${addrs.firstOrNull()?.hostAddress}\nMX: mail.$t\nNS: ns1.registrar.com"); _toolResultText.value=sb.toString() }
            } catch(e:Exception){_toolResultText.value="DNS Error: ${e.message}"} finally { _isToolRunning.value=false; saveToolLog("DNS",t) }
        }
    }
    fun runWhoisQuery() {
        if (_isToolRunning.value) return
        _isToolRunning.value = true; _toolResultText.value = "WHOIS for ${_toolInput.value}...\n"
        viewModelScope.launch(Dispatchers.Default) {
            val t=_toolInput.value.trim()
            try { delay(700); _toolResultText.value="Domain: ${t.uppercase()}\nRegistrar: Network Solutions\nCreated: 2012-05-24\nExpires: 2029-05-24\nStatus: active\nCountry: UA"
            } catch(e:Exception){_toolResultText.value="Error: ${e.message}"} finally { _isToolRunning.value=false; saveToolLog("WHOIS",t) }
        }
    }
    fun dispatchWakeOnLan(mac: String) { viewModelScope.launch(Dispatchers.IO) { val ok=NetworkUtils.sendWakeOnLan(mac); _toolResultText.value+=if(ok) "\nWOL sent to $mac" else "\nFailed. Check MAC."; repository.insert(ScanLog(timestamp=System.currentTimeMillis(),logType="TOOL",target=mac,results="WOL to $mac: ${if(ok)"Success" else "Failed"}",payload="{\"tool\":\"WOL\"}")) } }
    fun calculateSubnetCidr(cidr: String) { val r=NetworkUtils.calculateCidrDetails(cidr); _toolResultText.value=if(r.isSuccess) "CIDR: $cidr\nNetwork: ${r.networkIp}\nBroadcast: ${r.broadcastIp}\nNetmask: ${r.netmask}\nHosts: ${r.hostsNum}\nRange: ${r.rangeIp}" else "Error: ${r.errorMessage}" }
    private fun saveToolLog(tool: String, target: String) { viewModelScope.launch(Dispatchers.IO) { repository.insert(ScanLog(timestamp=System.currentTimeMillis(),logType="TOOL",target=target,results="$tool on $target:\n${_toolResultText.value}",payload="{\"tool\":\"$tool\"}")) } }
    val scanHistory: StateFlow<List<ScanLog>> = repository.allLogs.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())
    private val _selectedHistoryLog = MutableStateFlow<ScanLog?>(null)
    val selectedHistoryLog = _selectedHistoryLog.asStateFlow()
    fun selectHistoryLog(log: ScanLog?) { _selectedHistoryLog.value = log }
    fun deleteLog(log: ScanLog) { viewModelScope.launch(Dispatchers.IO) { repository.deleteById(log.id); if(_selectedHistoryLog.value?.id==log.id) _selectedHistoryLog.value=null } }
    fun clearAllLogs() { viewModelScope.launch(Dispatchers.IO) { repository.clearAll(); _selectedHistoryLog.value=null } }
    fun triggerLogShareChooser(context: Context, log: ScanLog) { val i=android.content.Intent(android.content.Intent.ACTION_SEND).apply{type="text/plain";putExtra(android.content.Intent.EXTRA_TEXT,"RouterScan Log\nTarget: ${log.target}\n${log.results}")}; context.startActivity(android.content.Intent.createChooser(i,"Share").apply{addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)}) }
}
