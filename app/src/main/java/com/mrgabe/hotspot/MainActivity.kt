package com.mrgabe.hotspot

import android.content.pm.PackageManager
import com.mrgabe.hotspot.services.HotspotService

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Button
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : Activity() {

    private val PERMISSIONS_REQUEST_CODE_ACCESS_FINE_LOCATION = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

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

        findViewById<Button>(R.id.enableHotspotButton).setOnClickListener {
            sendCommandToService("enable", "MyHotspot", "12345678")
        }

        findViewById<Button>(R.id.disableHotspotButton).setOnClickListener {
            sendCommandToService("disable", "", "")
        }
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