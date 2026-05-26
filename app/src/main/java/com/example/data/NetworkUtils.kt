package com.example.data

import android.content.Context
import android.net.wifi.WifiManager
import android.net.wifi.ScanResult
import android.text.format.Formatter
import java.net.InetAddress
import java.net.Socket
import java.net.InetSocketAddress

object NetworkUtils {
    fun getLocalIpAddress(context: Context): String {
        val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        return Formatter.formatIpAddress(wm?.connectionInfo?.ipAddress ?: 0)
    }
    fun getGateway(context: Context): String {
        val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        return Formatter.formatIpAddress(wm?.dhcpInfo?.gateway ?: 0)
    }
    fun getWifiSsid(context: Context): String {
        val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val s = wm?.connectionInfo?.ssid ?: "N/A"
        return if (s.startsWith("\"") && s.endsWith("\"")) s.substring(1, s.length - 1) else s
    }
    fun getWifiBssid(context: Context): String {
        val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        return wm?.connectionInfo?.bssid ?: "00:00:00:00:00:00"
    }
    fun getWifiRssi(context: Context): Int {
        val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        return wm?.connectionInfo?.rssi ?: -100
    }
    fun getWifiSpeed(context: Context): Int {
        val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        return wm?.connectionInfo?.linkSpeed ?: 0
    }
    fun getWifiFreq(context: Context): Int {
        val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        return wm?.connectionInfo?.frequency ?: 2412
    }
    fun startWifiScan(context: Context): Boolean {
        val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        return try { wm?.startScan() ?: false } catch(e: Exception) { false }
    }
    fun getWifiScanResults(context: Context): List<ScanResult> {
        val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        return try { wm?.scanResults ?: emptyList() } catch(e: Exception) { emptyList() }
    }
    fun getChannel(freq: Int): Int = when {
        freq == 2412 -> 1; freq == 2417 -> 2; freq == 2422 -> 3
        freq == 2427 -> 4; freq == 2432 -> 5; freq == 2437 -> 6
        freq == 2442 -> 7; freq == 2447 -> 8; freq == 2452 -> 9
        freq == 2457 -> 10; freq == 2462 -> 11; freq == 2467 -> 12
        freq == 2472 -> 13; freq >= 5180 && freq <= 5240 -> (freq - 5180) / 5 + 36
        freq >= 5260 && freq <= 5320 -> (freq - 5260) / 5 + 52
        freq >= 5500 && freq <= 5700 -> (freq - 5500) / 5 + 100
        freq >= 5745 && freq <= 5825 -> (freq - 5745) / 5 + 149
        else -> 0
    }
    fun getSecurity(result: ScanResult): String {
        val cap = result.capabilities
        return when {
            cap.contains("WPA3") -> "WPA3"
            cap.contains("WPA2") -> "WPA2"
            cap.contains("WPA") -> "WPA"
            cap.contains("WEP") -> "WEP"
            else -> "OPEN"
        }
    }
    fun pingHost(ip: String, timeout: Int = 150): Long? {
        return try {
            val t = System.currentTimeMillis()
            val s = Socket()
            s.connect(InetSocketAddress(ip, 80), timeout)
            val rtt = System.currentTimeMillis() - t
            s.close(); rtt
        } catch(e: Exception) {
            try {
                val t = System.currentTimeMillis()
                if (InetAddress.getByName(ip).isReachable(timeout)) System.currentTimeMillis() - t else null
            } catch(e2: Exception) { null }
        }
    }
    fun checkPort(ip: String, port: Int, timeout: Int = 150): Long? {
        return try {
            val t = System.currentTimeMillis()
            val s = Socket()
            s.connect(InetSocketAddress(ip, port), timeout)
            val rtt = System.currentTimeMillis() - t
            s.close(); rtt
        } catch(e: Exception) { null }
    }
    fun sendWakeOnLan(mac: String, bcast: String = "255.255.255.255"): Boolean {
        return try {
            val mb = mac.replace(":", "").replace("-", "").chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            if (mb.size != 6) return false
            val buf = ByteArray(6 + 16 * 6)
            for (i in 0..5) buf[i] = 0xff.toByte()
            for (i in 1..16) System.arraycopy(mb, 0, buf, i * 6, 6)
            val s = java.net.DatagramSocket()
            s.broadcast = true
            s.send(java.net.DatagramPacket(buf, buf.size, InetAddress.getByName(bcast), 9))
            s.close(); true
        } catch(e: Exception) { false }
    }
    val TOP_PORTS = mapOf(21 to "FTP", 22 to "SSH", 23 to "Telnet", 25 to "SMTP", 53 to "DNS", 80 to "HTTP", 110 to "POP3", 135 to "RPC", 139 to "NetBIOS", 143 to "IMAP", 443 to "HTTPS", 445 to "SMB", 554 to "RTSP", 1433 to "MSSQL", 3306 to "MySQL", 3389 to "RDP", 5900 to "VNC", 8080 to "HTTP-alt", 8443 to "HTTPS-alt", 9000 to "PHP")
}
