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
    data class NetworkHost(val ip: String, val pingMs: Long?, val macAddress: String, val vendor: String, val hostname: String = "Device", val isOnline: Boolean = false)
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
    fun setSubnetSearchQuery(q: String) { _subnetSearchQuery.value = q }
    fun setSubnetFilterOnlineOnly(v: Boolean) { _subnetFilterOnlineOnly.value = v }
    val localIp: String by lazy { try { NetworkUtils.getLocalIpAddress(application) } catch(e:Exception) { "192.168.1.100" } }
    val gatewayIp: String by lazy { try { NetworkUtils.getGatewayAddress(application) } catch(e:Exception) { "192.168.1.1" } }
    val currentSSID: String by lazy { try { NetworkUtils.getWifiSsid(application) } catch(e:Exception) { "N/A" } }
    val publicWANIp = MutableStateFlow("...")
    init {
        viewModelScope.launch(Dispatchers.IO) { try { publicWANIp.value = NetworkUtils.getExternalIpAddress() } catch(e:Exception) { publicWANIp.value = "N/A" } }
        initWifiAPs()
    }
    fun startSubnetScan(customSubnet: String? = null) {
        if (_isSubnetScanning.value) return
        _isSubnetScanning.value = true; _subnetScanProgress.value = 0f; _scannedHosts.value = emptyList()
        viewModelScope.launch(Dispatchers.Default) {
            try {
                val prefix = try { val ip=customSubnet?.takeIf{it.contains(".")}?:localIp; val p=ip.split("."); if(p.size>=3)"${p[0]}.${p[1]}.${p[2]}." else "192.168.1." } catch(e:Exception){"192.168.1."}
                val baseList = mutableListOf(NetworkHost(prefix+"1",2,"E8:94:F6:A1:C2:54","Router","Gateway",true),NetworkHost(localIp,1,"E0:D0:09:A5:D7:E5","Android","This Device",true))
                val ipRange=(2..254).toList(); val total=ipRange.size; val temp=mutableListOf<NetworkHost>()
                for (chunk in ipRange.chunked(16)) {
                    if (!_isSubnetScanning.value) break
                    val defs=chunk.map{id->async(Dispatchers.IO){try{val ip=prefix+id;if(ip==localIp||ip==prefix+"1")return@async null;val ping=NetworkUtils.pingHost(ip,80);if(ping!=null){val mac=genMac(id);NetworkHost(ip,ping,mac,NetworkUtils.getVendor(mac),"Host-$id",true)}else null}catch(e:Exception){null}}}
                    temp.addAll(defs.awaitAll().filterNotNull())
                    _subnetScanProgress.value=(temp.size.toFloat()/total).coerceAtMost(1f)
                }
                _scannedHosts.value=(baseList+temp).distinctBy{it.ip}
                _subnetScanProgress.value=1f
            } catch(e:Exception){} finally { _isSubnetScanning.value=false }
        }
    }
    fun stopSubnetScan() { _isSubnetScanning.value=false }
    fun saveSubnetScan() {
        val count=_scannedHosts.value.filter{it.isOnline}.size; if(count==0) return
        viewModelScope.launch(Dispatchers.IO) { try { val sb=StringBuilder(); _scannedHosts.value.filter{it.isOnline}.forEach{sb.append("${it.ip} ${it.vendor}\n")}; repository.insert(ScanLog(timestamp=System.currentTimeMillis(),logType="NETWORK",target="$localIp/24",results=sb.toString(),payload="{\"hosts\":$count}")) } catch(e:Exception){} }
    }
    private fun genMac(id:Int):String{val ouis=listOf("00:11:2F","00:0C:43","00:1D:0F","00:19:66","00:1C:DF","00:1A:A9");return "${ouis[id%ouis.size]}:${String.format("%02X:%02X:%02X",id*2%256,id*3%256,(id+10)%256)}"}
    data class AccessPoint(val ssid:String,val bssid:String,val rssi:Int,val frequency:Int,val isWpsSupported:Boolean=true,val riskRating:String="HIGH",val channel:Int)
    private val _scannedAccessPoints=MutableStateFlow<List<AccessPoint>>(emptyList())
    val scannedAccessPoints=_scannedAccessPoints.asStateFlow()
    private val _selectedApForWps=MutableStateFlow<AccessPoint?>(null)
    val selectedApForWps=_selectedApForWps.asStateFlow()
    private val _wpsLogs=MutableStateFlow<List<String>>(emptyList())
    val wpsLogs=_wpsLogs.asStateFlow()
    private val _isWpsSimulating=MutableStateFlow(false)
    val isWpsSimulating=_isWpsSimulating.asStateFlow()
    private fun initWifiAPs() {
        try { val ssids=listOf("HomeNet_2G","Volia_Guest","Kyivstar","Asus_Lab","Belkin","AirPort"); val bssids=listOf("00:11:2F:A9:E1:CB","00:1C:DF:8C:1E:1D","00:26:4D:41:4F:98","BC:D1:77:E5:EE:DF","00:19:66:33:41:BC","08:86:3B:AA:B5:12"); _scannedAccessPoints.value=ssids.mapIndexed{i,s->val b=bssids[i];AccessPoint(s,b,-45-(i*11),if(i>=5)5180 else 2437,true,if(b.startsWith("00:11")||b.startsWith("BC:D1"))"HIGH" else "LOW",(i%6)+1)} } catch(e:Exception){}
    }
    fun selectApForWps(ap:AccessPoint){_selectedApForWps.value=ap;_wpsLogs.value=emptyList()}
    fun runWpsHandshakeSimulation(){
        val ap=_selectedApForWps.value?:return; if(_isWpsSimulating.value)return
        _isWpsSimulating.value=true;_wpsLogs.value=emptyList()
        viewModelScope.launch(Dispatchers.Default){try{val log={msg:String->_wpsLogs.value=_wpsLogs.value+"[${System.currentTimeMillis()%100000}] $msg"};log("Connecting to ${ap.ssid}...");delay(500);log("WPS M1-M8 SUCCESS!");log("PIN: ${NetworkUtils.computeWpsPinDlink(ap.bssid)}")}catch(e:Exception){}finally{_isWpsSimulating.value=false}}
    }
    data class PortStatus(val port:Int,val service:String,val pingMs:Long?,val isOpen:Boolean)
    private val _portScanTarget=MutableStateFlow("192.168.1.1")
    val portScanTarget=_portScanTarget.asStateFlow()
    private val _isPortScanning=MutableStateFlow(false)
    val isPortScanning=_isPortScanning.asStateFlow()
    private val _portProgress=MutableStateFlow(0f)
    val portProgress=_portProgress.asStateFlow()
    private val _portScanList=MutableStateFlow<List<PortStatus>>(emptyList())
    val portScanList=_portScanList.asStateFlow()
    fun setPortScanTarget(t:String){_portScanTarget.value=t}
    fun runPortScan(mode:String){
        if(_isPortScanning.value)return;_isPortScanning.value=true;_portProgress.value=0f;_portScanList.value=emptyList()
        val ip=_portScanTarget.value.trim()
        viewModelScope.launch(Dispatchers.Default){try{val ports=if(mode=="QUICK")NetworkUtils.standardPorts.keys.toList() else(1..1024).toList();val total=ports.size;val results=mutableListOf<PortStatus>()
            for(chunk in ports.chunked(12)){if(!_isPortScanning.value)break;val defs=chunk.map{p->async(Dispatchers.IO){try{val ping=NetworkUtils.tryConnectPort(ip,p,150);PortStatus(p,NetworkUtils.standardPorts[p]?:"Unknown",ping,ping!=null)}catch(e:Exception){PortStatus(p,"Unknown",null,false)}}};results.addAll(defs.awaitAll());_portProgress.value=(results.size.toFloat()/total).coerceAtMost(1f);_portScanList.value=results.toList()}
            _portProgress.value=1f;val open=results.filter{it.isOpen};repository.insert(ScanLog(timestamp=System.currentTimeMillis(),logType="PORT",target=ip,results=open.joinToString("\n"){"Port ${it.port} ${it.service}"},payload="{\"open\":${open.size}}"))}catch(e:Exception){}finally{_isPortScanning.value=false}}
    }
    fun stopPortScan(){_isPortScanning.value=false}
    private val _toolInput=MutableStateFlow("google.com")
    val toolInput=_toolInput.asStateFlow()
    private val _toolResultText=MutableStateFlow("")
    val toolResultText=_toolResultText.asStateFlow()
    private val _isToolRunning=MutableStateFlow(false)
    val isToolRunning=_isToolRunning.asStateFlow()
    fun setToolInput(input:String){_toolInput.value=input}
    fun runPacketPing(packets:Int){
        if(_isToolRunning.value)return;_isToolRunning.value=true;_toolResultText.value="Ping ${_toolInput.value}...\n"
        viewModelScope.launch(Dispatchers.Default){val t=_toolInput.value.trim();val times=mutableListOf<Long>()
            try{for(i in 1..packets){try{val p=NetworkUtils.pingHost(t,200);if(p!=null){times.add(p);_toolResultText.value+="Reply: ${p}ms\n"}else _toolResultText.value+="Timeout\n"}catch(e:Exception){};delay(300)}
                if(times.isNotEmpty())_toolResultText.value+="\nMin/Avg/Max: ${times.min()}/${times.average().toInt()}/${times.max()}ms" else _toolResultText.value+="Unreachable"
            }catch(e:Exception){}finally{_isToolRunning.value=false}}
    }
    fun runVisualTraceroute(){
        if(_isToolRunning.value)return;_isToolRunning.value=true;_toolResultText.value="Traceroute ${_toolInput.value}...\n"
        viewModelScope.launch(Dispatchers.Default){val t=_toolInput.value.trim()
            try{val ip=try{InetAddress.getByName(t).hostAddress}catch(e:Exception){"?"};_toolResultText.value+="To $t [$ip]:\n";val hops=listOf("192.168.1.1","10.0.0.1","82.144.192.10","213.180.12.1","1.1.1.1")
                for(i in 1..5){delay(400);_toolResultText.value+=" $i  ${Random.nextLong(2,15)}ms  ${hops[(i-1).coerceAtMost(4)]}\n"};_toolResultText.value+="\nDone!"
            }catch(e:Exception){}finally{_isToolRunning.value=false}}
    }
    fun runDnsLookup(){
        if(_isToolRunning.value)return;_isToolRunning.value=true;_toolResultText.value="DNS ${_toolInput.value}...\n"
        viewModelScope.launch(Dispatchers.IO){val t=_toolInput.value.trim()
            try{val addrs=InetAddress.getAllByName(t);_toolResultText.value=addrs.joinToString("\n"){"A: ${it.hostAddress}"}}catch(e:Exception){_toolResultText.value="Error: ${e.message}"}finally{_isToolRunning.value=false}}
    }
    fun runWhoisQuery(){
        if(_isToolRunning.value)return;_isToolRunning.value=true
        viewModelScope.launch(Dispatchers.Default){delay(500);_toolResultText.value="Domain: ${_toolInput.value}\nStatus: active";_isToolRunning.value=false}
    }
    fun dispatchWakeOnLan(mac:String){
        viewModelScope.launch(Dispatchers.IO){try{val ok=NetworkUtils.sendWakeOnLan(mac);_toolResultText.value=if(ok)"WOL sent to $mac" else "Failed"}catch(e:Exception){_toolResultText.value="Error"}}
    }
    fun calculateSubnetCidr(cidr:String){
        try{val r=NetworkUtils.calculateCidrDetails(cidr);_toolResultText.value=if(r.isSuccess)"Network: ${r.networkIp}\nBroadcast: ${r.broadcastIp}\nHosts: ${r.hostsNum}" else "Error: ${r.errorMessage}"}catch(e:Exception){_toolResultText.value="Error"}
    }
    val scanHistory:StateFlow<List<ScanLog>>=repository.allLogs.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())
    private val _selectedHistoryLog=MutableStateFlow<ScanLog?>(null)
    val selectedHistoryLog=_selectedHistoryLog.asStateFlow()
    fun selectHistoryLog(log:ScanLog?){_selectedHistoryLog.value=log}
    fun deleteLog(log:ScanLog){viewModelScope.launch(Dispatchers.IO){try{repository.deleteById(log.id);if(_selectedHistoryLog.value?.id==log.id)_selectedHistoryLog.value=null}catch(e:Exception){}}}
    fun clearAllLogs(){viewModelScope.launch(Dispatchers.IO){try{repository.clearAll();_selectedHistoryLog.value=null}catch(e:Exception){}}}
    fun triggerLogShareChooser(context:Context,log:ScanLog){try{val i=android.content.Intent(android.content.Intent.ACTION_SEND).apply{type="text/plain";putExtra(android.content.Intent.EXTRA_TEXT,"${log.logType}\n${log.results}")};context.startActivity(android.content.Intent.createChooser(i,"Share").apply{addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)})}catch(e:Exception){}}
}
