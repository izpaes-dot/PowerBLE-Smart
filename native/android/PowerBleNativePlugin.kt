package com.izpaes.powerblesmart

import android.Manifest
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.getcapacitor.JSObject
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.annotation.CapacitorPlugin
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

@CapacitorPlugin(name = "PowerBleNative")
class PowerBleNativePlugin : Plugin() {
    companion object {
        val CPS_UUID: UUID = UUID.fromString("00001818-0000-1000-8000-00805f9b34fb")
        val CPM_UUID: UUID = UUID.fromString("00002a63-0000-1000-8000-00805f9b34fb")
        val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }
    private var server: BluetoothGattServer? = null
    private var advertiser: BluetoothLeAdvertiser? = null
    private var characteristic: BluetoothGattCharacteristic? = null
    private val clients = mutableSetOf<BluetoothDevice>()
    private var power = 0
    private var running = false

    private fun permitted(): Boolean = Build.VERSION.SDK_INT < 31 ||
        (context.checkSelfPermission(Manifest.permission.BLUETOOTH_ADVERTISE)==PackageManager.PERMISSION_GRANTED &&
         context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)==PackageManager.PERMISSION_GRANTED)

    @com.getcapacitor.PluginMethod
    fun startPower(call: PluginCall) {
        if (!permitted()) { call.reject("Permissões Bluetooth Nearby não concedidas"); return }
        val bm = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val adapter = bm.adapter
        if (adapter == null || !adapter.isEnabled) { call.reject("Bluetooth desligado"); return }
        if (!adapter.isMultipleAdvertisementSupported) { call.reject("BLE Peripheral não suportado neste telefone"); return }
        try {
            stopInternal()
            server = bm.openGattServer(context, callback)
            val service = BluetoothGattService(CPS_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)
            characteristic = BluetoothGattCharacteristic(CPM_UUID, BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_NOTIFY, BluetoothGattCharacteristic.PERMISSION_READ)
            characteristic!!.addDescriptor(BluetoothGattDescriptor(CCCD_UUID, BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE))
            service.addCharacteristic(characteristic)
            server!!.addService(service)
            advertiser = adapter.bluetoothLeAdvertiser
            val settings = AdvertiseSettings.Builder().setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY).setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH).setConnectable(true).build()
            val data = AdvertiseData.Builder().setIncludeDeviceName(true).addServiceUuid(android.os.ParcelUuid(CPS_UUID)).build()
            advertiser!!.startAdvertising(settings, data, advertiseCallback)
            running = true
            notifyListeners("powerConnectionChanged", JSObject().put("state","advertising"))
            call.resolve(JSObject().put("active",true))
        } catch (e: Exception) { stopInternal(); call.reject(e.message ?: "Falha BLE") }
    }

    @com.getcapacitor.PluginMethod
    fun stopPower(call: PluginCall) { stopInternal(); call.resolve() }

    @com.getcapacitor.PluginMethod
    fun updatePower(call: PluginCall) {
        power = (call.getInt("power",0) ?: 0).coerceIn(-32768,32767)
        notifyPower()
        call.resolve(JSObject().put("power",power))
    }

    @com.getcapacitor.PluginMethod
    fun connectHeartRate(call: PluginCall) = call.reject("HR nativo será habilitado na V1.1")
    @com.getcapacitor.PluginMethod
    fun connectCadence(call: PluginCall) = call.reject("Cadência nativa será habilitada na V1.1")
    @com.getcapacitor.PluginMethod
    fun disconnectSensors(call: PluginCall) = call.resolve()

    private val advertiseCallback = object: AdvertiseCallback() {
        override fun onStartFailure(errorCode:Int) { notifyListeners("nativeError", JSObject().put("message","Advertising falhou: $errorCode")) }
    }
    private val callback = object: BluetoothGattServerCallback() {
        override fun onConnectionStateChange(device:BluetoothDevice,status:Int,newState:Int) {
            if(newState==BluetoothProfile.STATE_CONNECTED) notifyListeners("powerConnectionChanged",JSObject().put("state","connected").put("device",device.name ?: "BLE"))
            if(newState==BluetoothProfile.STATE_DISCONNECTED) clients.remove(device)
        }
        override fun onDescriptorWriteRequest(device:BluetoothDevice,requestId:Int,descriptor:BluetoothGattDescriptor,preparedWrite:Boolean,responseNeeded:Boolean,offset:Int,value:ByteArray) {
            if(descriptor.uuid==CCCD_UUID){ if(value.contentEquals(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)) clients.add(device) else clients.remove(device) }
            if(responseNeeded) server?.sendResponse(device,requestId,BluetoothGatt.GATT_SUCCESS,0,null)
        }
        override fun onCharacteristicReadRequest(device:BluetoothDevice,requestId:Int,offset:Int,ch:BluetoothGattCharacteristic){
            if(ch.uuid==CPM_UUID && offset==0) server?.sendResponse(device,requestId,BluetoothGatt.GATT_SUCCESS,0,measurement()) else server?.sendResponse(device,requestId,BluetoothGatt.GATT_FAILURE,0,null)
        }
    }
    private fun measurement():ByteArray = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putShort(0).putShort(power.toShort()).array()
    private fun notifyPower(){ if(!running || clients.isEmpty()) return; val c=characteristic?:return; val v=measurement(); clients.toList().forEach{ d -> try { if(Build.VERSION.SDK_INT>=33) server?.notifyCharacteristicChanged(d,c,false,v) else @Suppress("DEPRECATION") server?.notifyCharacteristicChanged(d,c,false) } catch(_:Exception){} } }
    private fun stopInternal(){ try{advertiser?.stopAdvertising(advertiseCallback)}catch(_:Exception){}; advertiser=null; try{server?.close()}catch(_:Exception){}; server=null; clients.clear(); running=false; notifyListeners("powerConnectionChanged",JSObject().put("state","stopped")) }
}
