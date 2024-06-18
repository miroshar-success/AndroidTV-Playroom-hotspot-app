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
        // Initialize the service and configure communication
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
                handleWebSocketMessage(text)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e("WebSocket", "Error: ${t.message}")
            }
        }

        client.newWebSocket(request, webSocketListener)
        client.dispatcher.executorService.shutdown()
    }

    private fun handleWebSocketMessage(message: String) {
        val request = gson.fromJson(message, RequestPayload::class.java)

        when (request.controller) {
            "hotspot" -> handleHotspotCommand(request)
            else -> Log.e("WebSocket", "Unknown controller: ${request.controller}")
        }
    }

    private fun handleHotspotCommand(request: RequestPayload) {
        when (request.command) {
            "enable" -> enableHotspot(request.payload.ssid, request.payload.password)
            "disable" -> disableHotspot()
            else -> Log.e("HotspotService", "Unknown command: ${request.command}")
        }
    }

    private fun enableHotspot(ssid: String, password: String) {
        try {
            val wifiConfig = WifiConfiguration().apply {
                SSID = ssid
                preSharedKey = password
                allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK)
            }

            val method: Method = wifiManager.javaClass.getDeclaredMethod("setWifiApEnabled", WifiConfiguration::class.java, Boolean::class.javaPrimitiveType)
            method.isAccessible = true
            val result = method.invoke(wifiManager, wifiConfig, true) as Boolean
            Log.d("HotspotService", "Hotspot enabled: $result")
        } catch (e: Exception) {
            Log.e("HotspotService", "Error enabling hotspot: ${e.message}")
        }
    }

    private fun disableHotspot() {
        try {
            val method: Method = wifiManager.javaClass.getDeclaredMethod("setWifiApEnabled", WifiConfiguration::class.java, Boolean::class.javaPrimitiveType)
            method.isAccessible = true
            val result = method.invoke(wifiManager, null, false) as Boolean
            Log.d("HotspotService", "Hotspot disabled: $result")
        } catch (e: Exception) {
            Log.e("HotspotService", "Error disabling hotspot: ${e.message}")
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