package com.izpaes.powerblesmart

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager as AndroidLocationManager
import android.os.Bundle
import androidx.core.app.ActivityCompat
import com.getcapacitor.Plugin
import org.json.JSONObject

class LocationManager(private val context:Context, private val plugin:Plugin): LocationListener {
    private val lm=context.getSystemService(Context.LOCATION_SERVICE) as AndroidLocationManager

    @SuppressLint("MissingPermission")
    fun start() {
        if(ActivityCompat.checkSelfPermission(context,Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED &&
           ActivityCompat.checkSelfPermission(context,Manifest.permission.ACCESS_COARSE_LOCATION)!=PackageManager.PERMISSION_GRANTED) {
            plugin.notifyListeners("error", JSONObject().put("message","Permissão de localização não concedida"))
            return
        }
        lm.requestLocationUpdates(AndroidLocationManager.GPS_PROVIDER,1000L,1f,this)
    }

    fun stop(){ lm.removeUpdates(this) }

    override fun onLocationChanged(l:Location) {
        plugin.notifyListeners("gps", JSONObject()
            .put("latitude",l.latitude)
            .put("longitude",l.longitude)
            .put("altitude",l.altitude)
            .put("speedMps",if(l.hasSpeed()) l.speed else 0)
            .put("accuracy",if(l.hasAccuracy()) l.accuracy else JSONObject.NULL)
            .put("timestamp",l.time))
    }
    override fun onProviderEnabled(provider:String){}
    override fun onProviderDisabled(provider:String){}
    override fun onStatusChanged(provider:String,status:Int,extras:Bundle){}
}
