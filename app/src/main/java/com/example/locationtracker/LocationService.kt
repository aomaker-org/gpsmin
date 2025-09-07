package com.example.locationtracker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.*
import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.Yaml
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.math.*

class LocationService : Service() {

    // WGS84 Ellipsoid constants
    private val a = 6378137.0 // Semi-major axis
    private val f = 1 / 298.257223563 // Flattening
    private val e2 = f * (2 - f) // Eccentricity squared

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private lateinit var locationRequest: LocationRequest

    private var currentBestLocation: android.location.Location? = null

    // Handler for the one-minute logging task
    private val handler = android.os.Handler(Looper.getMainLooper())
    private val loggingRunnable = object : Runnable {
        override fun run() {
            logCurrentLocation()
            handler.postDelayed(this, TimeUnit.MINUTES.toMillis(1))
        }
    }

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        createLocationRequest()
        setupLocationCallback()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> start()
            ACTION_STOP -> stop()
        }
        return START_NOT_STICKY
    }

    private fun start() {
        Log.d(TAG, "Starting LocationService")
        createNotificationChannel()
        val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Location Tracker")
            .setContentText("Tracking location in the background...")
            .setSmallIcon(R.drawable.ic_launcher_foreground) // Placeholder icon
            .build()
        startForeground(NOTIFICATION_ID, notification)

        startLocationUpdates()
        handler.post(loggingRunnable)
    }

    private fun stop() {
        Log.d(TAG, "Stopping LocationService")
        fusedLocationClient.removeLocationUpdates(locationCallback)
        handler.removeCallbacks(loggingRunnable)
        stopForeground(true)
        stopSelf()
    }

    private fun createLocationRequest() {
        locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, TimeUnit.SECONDS.toMillis(10)).build()
    }

    private fun setupLocationCallback() {
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let {
                    Log.d(TAG, "New location received: ${it.latitude}, ${it.longitude}")
                    currentBestLocation = it
                }
            }
        }
    }

    private fun startLocationUpdates() {
        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
        } catch (e: SecurityException) {
            // This would happen if permissions are not granted.
            // The MainActivity is responsible for ensuring permissions before starting the service.
            Log.e(TAG, "Location permission not granted.", e)
            stop()
        }
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

    private fun logCurrentLocation() {
        val location = currentBestLocation ?: run {
            Log.w(TAG, "Cannot log location, it is null.")
            return
        }

        // 1. Convert GPS to ECEF
        val (ecefX, ecefY, ecefZ) = convertGpsToEcef(location.latitude, location.longitude, location.altitude)

        // 2. Create the cubic meter key
        val key = "${floor(ecefX).toLong()}:${floor(ecefY).toLong()}:${floor(ecefZ).toLong()}"

        // 3. Assemble the data payload
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

        // 4. Implement YAML File I/O
        try {
            val file = File(getExternalFilesDir(null), "location_log.yaml")
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

            // Get existing list of observations for this key, or create a new one
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

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "LocationService destroyed")
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Location Service Channel",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    companion object {
        private const val TAG = "LocationService"
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        private const val NOTIFICATION_CHANNEL_ID = "location_service_channel"
        private const val NOTIFICATION_ID = 1
    }
}
