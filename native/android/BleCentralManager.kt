package com.izpaes.powerblesmart

import android.Manifest
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.content.pm.PackageManager
import android.os.ParcelUuid
import androidx.core.app.ActivityCompat
import com.getcapacitor.Plugin
import org.json.JSONObject
import java.util.UUID

class BleCentralManager(
    private val context: Context,
    private val plugin: Plugin
) {
    companion object {
        val CSC_SERVICE = UUID.fromString("00001816-0000-1000-8000-00805f9b34fb")
        val HR_SERVICE = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")
        val POWER_SERVICE = UUID.fromString("00001818-0000-1000-8000-00805f9b34fb")
        val CSC_MEASUREMENT = UUID.fromString("00002a5b-0000-1000-8000-00805f9b34fb")
        val HR_MEASUREMENT = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")
        val POWER_MEASUREMENT = UUID.fromString("00002a63-0000-1000-8000-00805f9b34fb")
    }

    private val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
    private var scanner: BluetoothLeScanner? = null
    private var scanning = false
    private var current: BluetoothGatt? = null

    private fun allowed(p:String) =
        android.os.Build.VERSION.SDK_INT < 31 ||
        ActivityCompat.checkSelfPermission(context,p) == PackageManager.PERMISSION_GRANTED

    fun scan() {
        if (android.os.Build.VERSION.SDK_INT >= 31 && !allowed(Manifest.permission.BLUETOOTH_SCAN)) {
            plugin.notifyListeners("error", JSONObject().put("message","Permissão BLUETOOTH_SCAN não concedida"))
            return
        }
        scanner = adapter.bluetoothLeScanner
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        scanner?.startScan(null, settings, callback)
        scanning = true
    }

    private val callback = object: ScanCallback() {
        override fun onScanResult(type:Int, result:ScanResult) {
            val d=JSONObject()
                .put("name", result.device.name ?: "")
                .put("address", result.device.address)
                .put("rssi", result.rssi)
            plugin.notifyListeners("bleDeviceFound", d)
        }
        override fun onScanFailed(errorCode:Int) {
            plugin.notifyListeners("error", JSONObject().put("message","BLE scan falhou: $errorCode"))
        }
    }

    fun connect(id:String) {
        val d=adapter.getRemoteDevice(id)
        current?.close()
        current=d.connectGatt(context,false,gattCallback)
    }

    private val gattCallback=object: BluetoothGattCallback() {
        override fun onConnectionStateChange(g:BluetoothGatt,status:Int,newState:Int) {
            if(newState==BluetoothProfile.STATE_CONNECTED) {
                g.discoverServices()
            }
        }
        override fun onServicesDiscovered(g:BluetoothGatt,status:Int) {
            subscribe(g, CSC_SERVICE, CSC_MEASUREMENT)
            subscribe(g, HR_SERVICE, HR_MEASUREMENT)
            subscribe(g, POWER_SERVICE, POWER_MEASUREMENT)
        }
        override fun onCharacteristicChanged(g:BluetoothGatt,c:BluetoothGattCharacteristic,value:ByteArray) {
            when(c.uuid) {
                CSC_MEASUREMENT -> CscParser.parse(value)?.let {
                    plugin.notifyListeners("cadence", JSONObject().put("cadence",it))
                }
                HR_MEASUREMENT -> HeartRateParser.parse(value)?.let {
                    plugin.notifyListeners("heartRate", JSONObject().put("heartRate",it))
                }
                POWER_MEASUREMENT -> CyclingPowerParser.parse(value)?.let {
                    plugin.notifyListeners("cyclingPower", JSONObject().put("powerWatts",it))
                }
            }
        }
    }

    private fun subscribe(g:BluetoothGatt, service:UUID, characteristic:UUID) {
        val s=g.getService(service) ?: return
        val c=s.getCharacteristic(characteristic) ?: return
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED &&
            android.os.Build.VERSION.SDK_INT >= 31) return
        g.setCharacteristicNotification(c,true)
        c.descriptors.forEach { d ->
            if(d.uuid.toString().equals("00002902-0000-1000-8000-00805f9b34fb",true)) {
                d.value=BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                g.writeDescriptor(d)
            }
        }
    }

    fun stop() {
        scanner?.stopScan(callback)
        current?.close()
        scanning=false
    }
}
