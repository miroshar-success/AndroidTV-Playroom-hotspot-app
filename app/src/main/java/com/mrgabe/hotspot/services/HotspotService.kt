@file:Suppress("DEPRECATION")

package com.mrgabe.hotspot.services

import android.app.Service
import android.content.Intent
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager
import android.os.IBinder
import android.util.Log
import com.google.gson.Gson
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.lang.reflect.Method

class HotspotService : Service() {

    private val wifiManager: WifiManager by lazy {
        applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
    }

    private val gson = Gson()
    private val client = OkHttpClient()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let {
            val controller = it.getStringExtra("controller")
            val command = it.getStringExtra("command")
            val ssid = it.getStringExtra("ssid")
            val password = it.getStringExtra("password")

            if (controller != null && command != null) {
                val request = RequestPayload(0, controller, command, HotspotPayload(ssid ?: "", password ?: ""))
                handleHotspotCommand(request)
            }
        }

        startWebSocket()
        return START_STICKY
    }

    private fun startWebSocket() {
        val request = Request.Builder().url("ws://yourserveraddress").build()
        val webSocketListener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d("WebSocket", "Connection opened")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleWebSocketMessage(text, webSocket)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e("WebSocket", "Error: ${t.message}")
            }
        }

        client.newWebSocket(request, webSocketListener)
        client.dispatcher.executorService.shutdown()
    }

    private fun handleWebSocketMessage(message: String, webSocket: WebSocket) {
        val request = gson.fromJson(message, RequestPayload::class.java)

        when (request.controller) {
            "hotspot" -> handleHotspotCommand(request, webSocket)
            else -> Log.e("WebSocket", "Unknown controller: ${request.controller}")
        }
    }

    private fun handleHotspotCommand(request: RequestPayload, webSocket: WebSocket? = null) {
        val responsePayload = when (request.command) {
            "enable" -> {
                val result = enableHotspot(request.payload.ssid, request.payload.password)
                ResponsePayload(request.cookie, Response(result, !result, if (result) null else "Failed to enable Hotspot"))
            }
            "disable" -> {
                val result = disableHotspot()
                ResponsePayload(request.cookie, Response(result, !result, if (result) null else "Failed to disable Hotspot"))
            }
            "status" -> {
                val status = getHotspotStatus()
                ResponsePayload(request.cookie, Response(true, false, null, status))
            }
            else -> {
                Log.e("HotspotService", "Unknown command: ${request.command}")
                ResponsePayload(request.cookie, Response(false, true, "Unknown command"))
            }
        }
        sendBroadcast(Intent("com.mrgabe.hotspot.HOTSPOT_STATUS").apply {
            putExtra("response", gson.toJson(responsePayload))
        })
        webSocket?.send(gson.toJson(responsePayload))
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
    val response: com.mrgabe.hotspot.services.Response
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
