package com.izpaes.powerblesmart

import android.Manifest
import android.bluetooth.*
import android.bluetooth.le.*
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
        private val CPS_UUID = UUID.fromString("00001818-0000-1000-8000-00805f9b34fb")
        private val CPM_UUID = UUID.fromString("00002a63-0000-1000-8000-00805f9b34fb")
        private val HR_SERVICE = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")
        private val HR_MEASUREMENT = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")
        private val CSC_SERVICE = UUID.fromString("00001816-0000-1000-8000-00805f9b34fb")
        private val CSC_MEASUREMENT = UUID.fromString("00002a5b-0000-1000-8000-00805f9b34fb")
        private val FTMS_SERVICE = UUID.fromString("00001826-0000-1000-8000-00805f9b34fb")
        private val FTMS_FEATURE = UUID.fromString("00002acc-0000-1000-8000-00805f9b34fb")
        private val FTMS_INDOOR_BIKE = UUID.fromString("00002ad2-0000-1000-8000-00805f9b34fb")
        private val FTMS_CONTROL_POINT = UUID.fromString("00002ad9-0000-1000-8000-00805f9b34fb")
        private val FTMS_STATUS = UUID.fromString("00002ada-0000-1000-8000-00805f9b34fb")
        private val CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }

    private var server: BluetoothGattServer? = null
    private var advertiser: BluetoothLeAdvertiser? = null
    private var characteristic: BluetoothGattCharacteristic? = null
    private val clients = mutableSetOf<BluetoothDevice>()
    private var power = 0
    private var running = false
    private var scanner: BluetoothLeScanner? = null
    private var scanning = false
    private var gatt: BluetoothGatt? = null
    private var connectedDevice: BluetoothDevice? = null

    private fun has(permission: String): Boolean = context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
    private fun blePermissionsOk(): Boolean = Build.VERSION.SDK_INT < 31 || (has(Manifest.permission.BLUETOOTH_SCAN) && has(Manifest.permission.BLUETOOTH_CONNECT))
    private fun log(event: String, data: JSObject = JSObject()) { notifyListeners("diagnostic", data.put("event", event).put("ts", System.currentTimeMillis())) }

    @com.getcapacitor.PluginMethod
    fun getDiagnostics(call: PluginCall) {
        val bm = context.getSystemService(android.content.Context.BLUETOOTH_SERVICE) as BluetoothManager
        val a = bm.adapter
        val o = JSObject()
            .put("android", Build.VERSION.RELEASE ?: "")
            .put("sdk", Build.VERSION.SDK_INT)
            .put("manufacturer", Build.MANUFACTURER ?: "")
            .put("model", Build.MODEL ?: "")
            .put("bluetoothAvailable", a != null)
            .put("bluetoothEnabled", a?.isEnabled == true)
            .put("blePeripheral", a?.isMultipleAdvertisementSupported == true)
            .put("scanPermission", Build.VERSION.SDK_INT < 31 || has(Manifest.permission.BLUETOOTH_SCAN))
            .put("connectPermission", Build.VERSION.SDK_INT < 31 || has(Manifest.permission.BLUETOOTH_CONNECT))
            .put("advertisePermission", Build.VERSION.SDK_INT < 31 || has(Manifest.permission.BLUETOOTH_ADVERTISE))
            .put("locationPermission", has(Manifest.permission.ACCESS_FINE_LOCATION))
            .put("scanning", scanning)
            .put("connected", gatt != null)
            .put("connectedDevice", connectedDevice?.name ?: "")
        call.resolve(o)
        log("diagnostics", o)
    }

    @com.getcapacitor.PluginMethod
    fun startScan(call: PluginCall) {
        if (!blePermissionsOk()) { log("scan.permissionDenied"); call.reject("Permissões BLUETOOTH_SCAN/CONNECT não concedidas"); return }
        val bm = context.getSystemService(android.content.Context.BLUETOOTH_SERVICE) as BluetoothManager
        val adapter = bm.adapter
        if (adapter == null || !adapter.isEnabled) { log("scan.bluetoothOff"); call.reject("Bluetooth desligado"); return }
        scanner = adapter.bluetoothLeScanner
        if (scanner == null) { log("scan.noScanner"); call.reject("BLE scanner indisponível"); return }
        stopScanInternal()
        scanning = true
        log("scan.start")
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        scanner!!.startScan(null, settings, scanCallback)
        call.resolve(JSObject().put("scanning", true))
    }

    @com.getcapacitor.PluginMethod
    fun stopScan(call: PluginCall) { stopScanInternal(); call.resolve(JSObject().put("scanning", false)) }

    @com.getcapacitor.PluginMethod
    fun connectDevice(call: PluginCall) {
        val address = call.getString("address") ?: run { call.reject("address obrigatório"); return }
        if (!blePermissionsOk()) { call.reject("Permissões BLE não concedidas"); return }
        stopScanInternal()
        try {
            val bm = context.getSystemService(android.content.Context.BLUETOOTH_SERVICE) as BluetoothManager
            val device = bm.adapter.getRemoteDevice(address)
            gatt?.close(); gatt = null
            connectedDevice = device
            log("gatt.connect.start", JSObject().put("address", address).put("name", device.name ?: ""))
            gatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
            call.resolve(JSObject().put("connecting", true).put("address", address))
        } catch (e: Exception) { log("gatt.connect.error", JSObject().put("message", e.message ?: "")); call.reject(e.message ?: "Falha GATT") }
    }

    @com.getcapacitor.PluginMethod
    fun disconnectDevice(call: PluginCall) { gatt?.disconnect(); gatt?.close(); gatt=null; connectedDevice=null; log("gatt.disconnect"); call.resolve() }

    @com.getcapacitor.PluginMethod
    fun startPower(call: PluginCall) {
        if (!blePermissionsOk() || (Build.VERSION.SDK_INT >= 31 && !has(Manifest.permission.BLUETOOTH_ADVERTISE))) { call.reject("Permissões Bluetooth Nearby não concedidas"); return }
        val bm = context.getSystemService(android.content.Context.BLUETOOTH_SERVICE) as BluetoothManager
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
            log("broadcast.start", JSObject().put("service", CPS_UUID.toString()))
            notifyListeners("powerConnectionChanged", JSObject().put("state","advertising"))
            call.resolve(JSObject().put("active",true))
        } catch (e: Exception) { stopInternal(); call.reject(e.message ?: "Falha BLE") }
    }

    @com.getcapacitor.PluginMethod fun stopPower(call: PluginCall) { stopInternal(); call.resolve() }
    @com.getcapacitor.PluginMethod fun updatePower(call: PluginCall) { power=(call.getInt("power",0)?:0).coerceIn(-32768,32767); notifyPower(); call.resolve(JSObject().put("power",power)) }
    @com.getcapacitor.PluginMethod fun connectHeartRate(call: PluginCall) { call.reject("Use startScan + connectDevice para HR") }
    @com.getcapacitor.PluginMethod fun connectCadence(call: PluginCall) { call.reject("Use startScan + connectDevice para CSC") }
    @com.getcapacitor.PluginMethod fun disconnectSensors(call: PluginCall) { gatt?.disconnect(); call.resolve() }

    private val scanCallback = object: ScanCallback() {
        override fun onScanResult(type:Int, result:ScanResult) {
            val d=result.device
            val o=JSObject().put("name", d.name ?: "(sem nome)").put("address", d.address ?: "").put("rssi", result.rssi)
            val uuids=result.scanRecord?.serviceUuids?.map { it.uuid.toString() } ?: emptyList()
            o.put("services", uuids.joinToString(","))
            notifyListeners("deviceFound", o); log("scan.deviceFound", o)
        }
        override fun onScanFailed(errorCode:Int){ scanning=false; val o=JSObject().put("code",errorCode).put("message","Scan falhou"); notifyListeners("nativeError",o); log("scan.error",o) }
    }

    private val gattCallback = object: BluetoothGattCallback() {
        override fun onConnectionStateChange(g:BluetoothGatt,status:Int,newState:Int){
            val o=JSObject().put("status",status).put("state",if(newState==BluetoothProfile.STATE_CONNECTED)"connected" else "disconnected").put("name",g.device.name ?: "").put("address",g.device.address)
            notifyListeners("gattState",o); log("gatt.state",o)
            if(newState==BluetoothProfile.STATE_CONNECTED){ gatt=g; g.discoverServices() } else { if(gatt==g)gatt=null; connectedDevice=null }
        }
        override fun onServicesDiscovered(g:BluetoothGatt,status:Int){
            val uuids=g.services.map{it.uuid.toString()}; val o=JSObject().put("status",status).put("services",uuids.joinToString(",")); notifyListeners("servicesDiscovered",o); log("gatt.services",o)
            for(s in g.services){
                val type=when(s.uuid){HR_SERVICE->"HR"; CSC_SERVICE->"CSC"; FTMS_SERVICE->"FTMS"; else->"OTHER"}
                if(type!="OTHER"){ notifyListeners("serviceDetected",JSObject().put("type",type).put("uuid",s.uuid.toString())); log("service.$type.detected",JSObject().put("uuid",s.uuid.toString())) }
                for(c in s.characteristics){
                    if(c.uuid==HR_MEASUREMENT || c.uuid==CSC_MEASUREMENT || c.uuid==FTMS_INDOOR_BIKE || c.uuid==FTMS_STATUS){
                        try { g.setCharacteristicNotification(c,true); val d=c.getDescriptor(CCCD_UUID); if(d!=null){d.value=BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE; g.writeDescriptor(d)}; log("notify.enabled",JSObject().put("uuid",c.uuid.toString())) } catch(e:Exception){log("notify.error",JSObject().put("message",e.message?:""))}
                    }
                }
            }
        }
        override fun onCharacteristicChanged(g:BluetoothGatt,c:BluetoothGattCharacteristic,value:ByteArray){ handleMeasurement(c.uuid,value) }
        @Deprecated("Android API") override fun onCharacteristicChanged(g:BluetoothGatt,c:BluetoothGattCharacteristic){ handleMeasurement(c.uuid,c.value ?: ByteArray(0)) }
        override fun onCharacteristicWrite(g:BluetoothGatt,c:BluetoothGattCharacteristic,status:Int){ log("gatt.characteristicWrite",JSObject().put("uuid",c.uuid.toString()).put("status",status)) }
    }

    private fun handleMeasurement(uuid:UUID,b:ByteArray){
        when(uuid){
            HR_MEASUREMENT -> { val flags=b.getOrNull(0)?.toInt()?:0; val bpm=if(flags and 1==0) b.getOrNull(1)?.toInt()?.and(255)?:0 else if(b.size>=3) ByteBuffer.wrap(b,1,2).order(ByteOrder.LITTLE_ENDIAN).short.toInt() else 0; val o=JSObject().put("bpm",bpm).put("raw",b.joinToString(",")); notifyListeners("hrChanged",o); log("hr.measurement",o) }
            CSC_MEASUREMENT -> { val flags=b.getOrNull(0)?.toInt()?.and(255)?:0; var i=1; var cr=0; var ct=0; if(flags and 1 !=0 && b.size>=5){cr=ByteBuffer.wrap(b,i,4).order(ByteOrder.LITTLE_ENDIAN).int; i+=4; if(b.size>=i+2){ct=ByteBuffer.wrap(b,i,2).order(ByteOrder.LITTLE_ENDIAN).short.toInt() and 65535}}; val o=JSObject().put("crankRevolutions",cr).put("crankEventTime",ct).put("raw",b.joinToString(",")); notifyListeners("cadenceChanged",o); log("csc.measurement",o) }
            FTMS_INDOOR_BIKE -> { val o=JSObject().put("raw",b.joinToString(",")).put("length",b.size); notifyListeners("ftmsData",o); log("ftms.indoorBikeData",o) }
            FTMS_STATUS -> { val o=JSObject().put("raw",b.joinToString(",")); notifyListeners("ftmsStatus",o); log("ftms.status",o) }
        }
    }

    private val advertiseCallback = object: AdvertiseCallback(){ override fun onStartFailure(errorCode:Int){val o=JSObject().put("message","Advertising falhou: $errorCode"); notifyListeners("nativeError",o); log("broadcast.error",o)} }
    private val callback = object: BluetoothGattServerCallback(){
        override fun onConnectionStateChange(device:BluetoothDevice,status:Int,newState:Int){ if(newState==BluetoothProfile.STATE_CONNECTED)notifyListeners("powerConnectionChanged",JSObject().put("state","connected").put("device",device.name?:"BLE")); if(newState==BluetoothProfile.STATE_DISCONNECTED)clients.remove(device) }
        override fun onDescriptorWriteRequest(device:BluetoothDevice,requestId:Int,descriptor:BluetoothGattDescriptor,preparedWrite:Boolean,responseNeeded:Boolean,offset:Int,value:ByteArray){if(descriptor.uuid==CCCD_UUID){if(value.contentEquals(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE))clients.add(device)else clients.remove(device)};if(responseNeeded)server?.sendResponse(device,requestId,BluetoothGatt.GATT_SUCCESS,0,null)}
        override fun onCharacteristicReadRequest(device:BluetoothDevice,requestId:Int,offset:Int,ch:BluetoothGattCharacteristic){if(ch.uuid==CPM_UUID&&offset==0)server?.sendResponse(device,requestId,BluetoothGatt.GATT_SUCCESS,0,measurement())else server?.sendResponse(device,requestId,BluetoothGatt.GATT_FAILURE,0,null)}
    }
    private fun measurement():ByteArray=ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putShort(0).putShort(power.toShort()).array()
    private fun notifyPower(){if(!running||clients.isEmpty())return;val c=characteristic?:return;val v=measurement();clients.toList().forEach{d->try{if(Build.VERSION.SDK_INT>=33)server?.notifyCharacteristicChanged(d,c,false,v)else @Suppress("DEPRECATION") server?.notifyCharacteristicChanged(d,c,false)}catch(_:Exception){}}}
    private fun stopScanInternal(){if(scanning){try{scanner?.stopScan(scanCallback)}catch(_:Exception){};scanning=false;log("scan.stop")}}
    private fun stopInternal(){try{advertiser?.stopAdvertising(advertiseCallback)}catch(_:Exception){};advertiser=null;try{server?.close()}catch(_:Exception){};server=null;clients.clear();running=false;notifyListeners("powerConnectionChanged",JSObject().put("state","stopped"))}
}
