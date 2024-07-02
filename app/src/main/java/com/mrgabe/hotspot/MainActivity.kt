package com.mrgabe.hotspot

import android.annotation.SuppressLint
import android.content.pm.PackageManager
import com.mrgabe.hotspot.services.HotspotService
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.gson.Gson
import com.mrgabe.hotspot.services.ResponsePayload

class MainActivity : Activity() {

    private val PERMISSIONS_REQUEST_CODE_ACCESS_FINE_LOCATION = 1
    private lateinit var btnEnable: Button
    private lateinit var btnDisable: Button
    private lateinit var txtStatus: TextView
    private val gson = Gson()

    private val hotspotStatusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent?.let {
                val response = it.getStringExtra("response")
                val responsePayload = gson.fromJson(response, ResponsePayload::class.java)
                updateUI(responsePayload)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btnEnable = findViewById(R.id.enableHotspotButton)
        btnDisable = findViewById(R.id.disableHotspotButton)
        txtStatus = findViewById(R.id.txtStatus)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.System.canWrite(this)) {
                val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS)
                intent.data = Uri.parse("package:$packageName")
                startActivityForResult(intent, PERMISSIONS_REQUEST_CODE_ACCESS_FINE_LOCATION)
            }
        }

        // Check and request permissions
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION),
                PERMISSIONS_REQUEST_CODE_ACCESS_FINE_LOCATION
            )
        } else {
            startHotspotService()
        }

        btnEnable.setOnClickListener {
            sendCommandToService("enable", "MyHotspot", "12345678")
        }

        btnDisable.setOnClickListener {
            sendCommandToService("disable", "", "")
        }

        registerReceiver(hotspotStatusReceiver, IntentFilter("com.mrgabe.hotspot.HOTSPOT_STATUS"))
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(hotspotStatusReceiver)
    }

    private fun startHotspotService() {
        val intent = Intent(this, HotspotService::class.java)
        ContextCompat.startForegroundService(this, intent)
    }

    private fun sendCommandToService(command: String, ssid: String, password: String) {
        val intent = Intent(this, HotspotService::class.java).apply {
            putExtra("controller", "hotspot")
            putExtra("command", command)
            putExtra("ssid", ssid)
            putExtra("password", password)
        }
        startService(intent)
    }

    @SuppressLint("SetTextI18n")
    private fun updateUI(response: ResponsePayload) {
        val status = response.response.status
        if (status != null) {
            txtStatus.text = "Hotspot is ${if (status.active) "active" else "inactive"}\nSSID: ${status.ssid}\nPassword: ${status.password}"
        } else {
            txtStatus.text = "Error: ${response.response.message}"
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            PERMISSIONS_REQUEST_CODE_ACCESS_FINE_LOCATION -> {
                if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    startHotspotService()
                } else {
                    Log.e("MainActivity", "Permission denied")
                }
                return
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PERMISSIONS_REQUEST_CODE_ACCESS_FINE_LOCATION) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (Settings.System.canWrite(this)) {
                    startHotspotService()
                } else {
                    Log.e("MainActivity", "Write settings permission denied")
                }
            }
        }
    }
}