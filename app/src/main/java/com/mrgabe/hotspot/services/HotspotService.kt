@file:Suppress("DEPRECATION")

package com.mrgabe.hotspot.services

import android.app.Service
import android.content.Intent
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager
import android.os.IBinder
import android.util.Log
import com.google.gson.Gson
import java.lang.reflect.Method
import java.net.InetSocketAddress
import org.java_websocket.WebSocket

class HotspotService : Service() {

    private val wifiManager: WifiManager by lazy {
        applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
    }

    private val gson = Gson()
    private lateinit var webSocketServer: HotspotWebSocketServer

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let {
            val command = it.getStringExtra("command")
            val ssid = it.getStringExtra("ssid")
            val password = it.getStringExtra("password")

            if (command != null) {
                when (command) {
                    "enable" -> enableHotspot(ssid ?: "", password ?: "")
                    "disable" -> disableHotspot()
                }
            }
        }

        startWebSocketServer()
        return START_STICKY
    }

    private fun startWebSocketServer() {
        val address = InetSocketAddress(8080)
        webSocketServer = HotspotWebSocketServer(address, this)
        webSocketServer.start()
        Log.d("WebSocketServer", "Server started on port 8080")
    }

    fun handleWebSocketMessage(request: RequestPayload, webSocket: WebSocket) {
        when (request.controller) {
            "hotspot" -> handleHotspotCommand(request, webSocket)
            else -> Log.e("WebSocketServer", "Unknown controller: ${request.controller}")
        }
    }

    private fun handleHotspotCommand(request: RequestPayload, webSocket: WebSocket) {
        val responsePayload = when (request.command) {
            "status" -> {
                val status = getHotspotStatus()
                ResponsePayload(request.cookie, Response(true, false, null, status))
            }
            else -> {
                Log.e("HotspotService", "Unknown command: ${request.command}")
                ResponsePayload(request.cookie, Response(false, true, "Unknown command"))
            }
        }
        webSocket.send(gson.toJson(responsePayload))
    }

    private fun enableHotspot(ssid: String, password: String): Boolean {
        return try {
            val wifiConfig = WifiConfiguration().apply {
                SSID = ssid
                preSharedKey = password
                allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK)
            }

            val method: Method = wifiManager.javaClass.getDeclaredMethod("setWifiApEnabled", WifiConfiguration::class.java, Boolean::class.javaPrimitiveType)
            method.isAccessible = true
            method.invoke(wifiManager, wifiConfig, true) as Boolean
        } catch (e: Exception) {
            Log.e("HotspotService", "Error enabling hotspot: ${e.message}")
            false
        }
    }

    private fun disableHotspot(): Boolean {
        return try {
            val method: Method = wifiManager.javaClass.getDeclaredMethod("setWifiApEnabled", WifiConfiguration::class.java, Boolean::class.javaPrimitiveType)
            method.isAccessible = true
            method.invoke(wifiManager, null, false) as Boolean
        } catch (e: Exception) {
            Log.e("HotspotService", "Error disabling hotspot: ${e.message}")
            false
        }
    }

    private fun getHotspotStatus(): HotspotStatus {
        return try {
            val method: Method = wifiManager.javaClass.getDeclaredMethod("getWifiApConfiguration")
            method.isAccessible = true
            val wifiConfig = method.invoke(wifiManager) as WifiConfiguration

            val methodStatus: Method = wifiManager.javaClass.getDeclaredMethod("isWifiApEnabled")
            methodStatus.isAccessible = true
            val isEnabled = methodStatus.invoke(wifiManager) as Boolean

            HotspotStatus(isEnabled, wifiConfig.SSID, wifiConfig.preSharedKey)
        } catch (e: Exception) {
            Log.e("HotspotService", "Error getting hotspot status: ${e.message}")
            HotspotStatus(false, "", "")
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}

data class RequestPayload(
    val cookie: Int,
    val controller: String,
    val command: String,
    val payload: HotspotPayload
)

data class HotspotPayload(
    val ssid: String,
    val password: String
)

data class ResponsePayload(
    val cookie: Int,
    val response: Response
)

data class Response(
    val result: Boolean,
    val error: Boolean,
    val message: String?,
    val status: HotspotStatus? = null
)

data class HotspotStatus(
    val active: Boolean,
    val ssid: String,
    val password: String
)