package com.izpaes.powerblesmart

import android.Manifest
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.ParcelUuid
import androidx.core.app.ActivityCompat
import com.getcapacitor.*
import java.util.UUID

@CapacitorPlugin(
    name = "PowerBleNativePlugin",
    permissions = [
        Permission(alias = "bluetoothScan", strings = [Manifest.permission.BLUETOOTH_SCAN]),
        Permission(alias = "bluetoothConnect", strings = [Manifest.permission.BLUETOOTH_CONNECT]),
        Permission(alias = "bluetoothAdvertise", strings = [Manifest.permission.BLUETOOTH_ADVERTISE]),
        Permission(alias = "location", strings = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    ]
)
class PowerBleNativePlugin : Plugin() {

    private lateinit var ble: BleCentralManager
    private lateinit var location: LocationManager
    private lateinit var ftms: FtmsServer

    override fun load() {
        ble = BleCentralManager(activity, this)
        location = LocationManager(activity, this)
        ftms = FtmsServer(activity, this)
    }

    @PluginMethod
    fun scan(call: PluginCall) {
        ble.scan()
        call.resolve()
    }

    @PluginMethod
    fun connect(call: PluginCall) {
        val id = call.getString("deviceId")
        if (id == null) {
            call.reject("deviceId obrigatório")
            return
        }
        ble.connect(id)
        call.resolve()
    }

    @PluginMethod
    fun startGps(call: PluginCall) {
        location.start()
        call.resolve()
    }

    @PluginMethod
    fun stopGps(call: PluginCall) {
        location.stop()
        call.resolve()
    }

    @PluginMethod
    fun startFtms(call: PluginCall) {
        ftms.start()
        call.resolve()
    }

    @PluginMethod
    fun stopFtms(call: PluginCall) {
        ftms.stop()
        call.resolve()
    }

    @PluginMethod
    fun updateFtms(call: PluginCall) {
        ftms.update(
            call.getDouble("powerWatts", 0.0),
            call.getDouble("cadence", 0.0),
            call.getDouble("speedKmh", 0.0)
        )
        call.resolve()
    }

    @PluginMethod
    fun stopAll(call: PluginCall) {
        ble.stop()
        location.stop()
        ftms.stop()
        call.resolve()
    }
}
