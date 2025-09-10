package com.example.locationtracker

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.android.gms.location.*
import kotlinx.coroutines.suspendCancellableCoroutine
import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.Yaml
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.*

class LocationWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(applicationContext)

    // WGS84 Ellipsoid constants
    private val a = 6378137.0 // Semi-major axis
    private val f = 1 / 298.257223563 // Flattening
    private val e2 = f * (2 - f) // Eccentricity squared

    override suspend fun doWork(): Result {
        Log.d(TAG, "LocationWorker starting work.")

        if (ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Location permission not granted to worker.")
            return Result.failure()
        }

        return try {
            val location = getCurrentLocation()
            logLocationToFile(location)
            Log.d(TAG, "LocationWorker work finished successfully.")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error in LocationWorker", e)
            Result.failure()
        }
    }

    @Suppress("MissingPermission")
    private suspend fun getCurrentLocation(): Location = suspendCancellableCoroutine { continuation ->
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000).build()
        val locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let {
                    if (continuation.isActive) {
                        continuation.resume(it)
                    }
                    // Important: remove the updates after the first successful result
                    fusedLocationClient.removeLocationUpdates(this)
                }
            }
        }

        continuation.invokeOnCancellation {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        }

        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
    }

    private fun convertGpsToEcef(lat: Double, lon: Double, alt: Double): Triple<Double, Double, Double> {
        val latRad = Math.toRadians(lat)
        val lonRad = Math.toRadians(lon)
        val n = a / sqrt(1 - e2 * sin(latRad).pow(2))
        val x = (n + alt) * cos(latRad) * cos(lonRad)
        val y = (n + alt) * cos(latRad) * sin(lonRad)
        val z = ((1 - e2) * n + alt) * sin(latRad)
        return Triple(x, y, z)
    }

    private fun logLocationToFile(location: Location) {
        val (ecefX, ecefY, ecefZ) = convertGpsToEcef(location.latitude, location.longitude, location.altitude)
        val key = "${floor(ecefX).toLong()}:${floor(ecefY).toLong()}:${floor(ecefZ).toLong()}"
        val timestamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date())

        val dataPayload = linkedMapOf(
            "timestamp" to timestamp,
            "gps_latitude" to location.latitude,
            "gps_longitude" to location.longitude,
            "gps_altitude" to location.altitude,
            "gps_accuracy_meters" to location.accuracy,
            "gps_provider" to location.provider,
            "gps_speed_mps" to location.speed,
            "gps_bearing_degrees" to location.bearing,
            "ecef_x" to ecefX,
            "ecef_y" to ecefY,
            "ecef_z" to ecefZ
        )

        Log.i(TAG, "Logging to key: $key")

        try {
            val file = File(applicationContext.getExternalFilesDir(null), "location_log.yaml")
            val dumperOptions = DumperOptions().apply {
                defaultFlowStyle = DumperOptions.FlowStyle.BLOCK
                isPrettyFlow = true
            }
            val yaml = Yaml(dumperOptions)

            val yamlData: MutableMap<String, Any> = if (file.exists()) {
                file.inputStream().use { yaml.load(it) ?: mutableMapOf<String, Any>() }
            } else {
                mutableMapOf()
            }

            @Suppress("UNCHECKED_CAST")
            val observations = (yamlData[key] as? MutableList<Map<String, Any>>) ?: mutableListOf()
            observations.add(dataPayload)
            yamlData[key] = observations

            FileWriter(file).use { writer ->
                yaml.dump(yamlData, writer)
            }
            Log.i(TAG, "Successfully wrote location to ${file.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Error writing to YAML file", e)
        }
    }

    companion object {
        private const val TAG = "LocationWorker"
    }
}
