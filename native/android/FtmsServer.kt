package com.izpaes.powerblesmart

import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.os.ParcelUuid
import com.getcapacitor.Plugin
import org.json.JSONObject
import java.util.UUID

class FtmsServer(private val context:Context, private val plugin:Plugin) {
    companion object {
        val FTMS=UUID.fromString("00001826-0000-1000-8000-00805f9b34fb")
        val FEATURE=UUID.fromString("00002acc-0000-1000-8000-00805f9b34fb")
        val STATUS=UUID.fromString("00002ada-0000-1000-8000-00805f9b34fb")
        val INDOOR=UUID.fromString("00002ad2-0000-1000-8000-00805f9b34fb")
        val CONTROL=UUID.fromString("00002ad9-0000-1000-8000-00805f9b34fb")
    }

    private val manager=context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private var server:BluetoothGattServer?=null
    private var advertiser:BluetoothLeAdvertiser?=null
    private val clients=mutableSetOf<BluetoothDevice>()
    private var power=0.0
    private var cadence=0.0
    private var speed=0.0

    fun start() {
        val adapter=manager.adapter
        server=manager.openGattServer(context,callback)
        val s=BluetoothGattService(FTMS,BluetoothGattService.SERVICE_TYPE_PRIMARY)
        s.addCharacteristic(BluetoothGattCharacteristic(
            FEATURE,BluetoothGattCharacteristic.PROPERTY_READ,
            BluetoothGattCharacteristic.PERMISSION_READ
        ))
        s.addCharacteristic(BluetoothGattCharacteristic(
            STATUS,BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_READ
        ))
        s.addCharacteristic(BluetoothGattCharacteristic(
            INDOOR,BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_READ
        ))
        s.addCharacteristic(BluetoothGattCharacteristic(
            CONTROL,
            BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_INDICATE,
            BluetoothGattCharacteristic.PERMISSION_WRITE
        ))
        server?.addService(s)

        advertiser=adapter.bluetoothLeAdvertiser
        val settings=AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
            .setConnectable(true).build()
        val data=AdvertiseData.Builder()
            .setIncludeDeviceName(true)
            .addServiceUuid(ParcelUuid(FTMS)).build()
        advertiser?.startAdvertising(settings,data,advCallback)
        plugin.notifyListeners("ftmsStatus",JSONObject().put("status","advertising"))
    }

    fun update(p:Double,c:Double,s:Double) {
        power=p; cadence=c; speed=s
        notifyIndoor()
    }

    private fun notifyIndoor() {
        val service=server?.getService(FTMS) ?: return
        val ch=service.getCharacteristic(INDOOR) ?: return
        // FTMS Indoor Bike Data: flags + speed (0.01 km/h uint16) +
        // cadence (0.5 rpm uint16) + power (1 W sint16).
        val sp=(speed*100).toInt().coerceIn(0,65535)
        val ca=(cadence*2).toInt().coerceIn(0,65535)
        val pw=power.toInt().coerceIn(-32768,32767)
        val bytes=byteArrayOf(
            0x00,0x00,
            (sp and 255).toByte(),(sp shr 8).toByte(),
            (ca and 255).toByte(),(ca shr 8).toByte(),
            (pw and 255).toByte(),(pw shr 8).toByte()
        )
        ch.value=bytes
        clients.forEach { d -> server?.notifyCharacteristicChanged(d,ch,false,bytes) }
    }

    private val callback=object:BluetoothGattServerCallback() {
        override fun onConnectionStateChange(d:BluetoothDevice,status:Int,newState:Int) {
            if(newState==BluetoothProfile.STATE_CONNECTED) clients.add(d)
            else clients.remove(d)
        }
        override fun onCharacteristicReadRequest(d:BluetoothDevice,requestId:Int,offset:Int,c:BluetoothGattCharacteristic) {
            server?.sendResponse(d,requestId,BluetoothGatt.GATT_SUCCESS,offset,c.value)
        }
    }

    private val advCallback=object:AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect:AdvertiseSettings) {}
        override fun onStartFailure(errorCode:Int) {
            plugin.notifyListeners("error",JSONObject().put("message","FTMS advertise falhou: $errorCode"))
        }
    }

    fun stop(){ advertiser?.stopAdvertising(advCallback); server?.close(); clients.clear() }
}
