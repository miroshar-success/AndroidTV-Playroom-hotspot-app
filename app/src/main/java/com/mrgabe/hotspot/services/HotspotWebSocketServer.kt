package com.mrgabe.hotspot.services

import android.util.Log
import com.google.gson.Gson
import org.java_websocket.server.WebSocketServer
import org.java_websocket.WebSocket
import org.java_websocket.handshake.ClientHandshake
import java.net.InetSocketAddress

class HotspotWebSocketServer(address: InetSocketAddress, private val service: HotspotService) : WebSocketServer(address) {

    private val gson = Gson()

    override fun onOpen(conn: WebSocket, handshake: ClientHandshake) {
        Log.d("WebSocketServer", "New connection opened: ${conn.remoteSocketAddress}")
    }

    override fun onClose(conn: WebSocket, code: Int, reason: String, remote: Boolean) {
        Log.d("WebSocketServer", "Connection closed: ${conn.remoteSocketAddress}")
    }

    override fun onMessage(conn: WebSocket, message: String) {
        Log.d("WebSocketServer", "Message received: $message")
        val request = gson.fromJson(message, RequestPayload::class.java)
        service.handleWebSocketMessage(request, conn)
    }

    override fun onError(conn: WebSocket?, ex: Exception) {
        Log.e("WebSocketServer", "Error: ${ex.message}")
    }

    override fun onStart() {
        Log.d("WebSocketServer", "Server started successfully")
    }
}