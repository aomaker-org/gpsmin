package com.example.locationtracker

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.locationtracker.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var isServiceRunning = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toggleButton.setOnClickListener {
            if (isServiceRunning) {
                stopLocationService()
            } else {
                checkPermissionsAndStartService()
            }
        }
    }

    private fun checkPermissionsAndStartService() {
        if (hasFineLocationPermission()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                if (hasBackgroundLocationPermission()) {
                    startLocationService()
                } else {
                    requestBackgroundLocationPermission()
                }
            } else {
                startLocationService()
            }
        } else {
            requestFineLocationPermission()
        }
    }

    private fun startLocationService() {
        Log.d(TAG, "Starting service")
        val serviceIntent = Intent(this, LocationService::class.java).apply {
            action = LocationService.ACTION_START
        }
        startService(serviceIntent)
        isServiceRunning = true
        updateUI()
    }

    private fun stopLocationService() {
        Log.d(TAG, "Stopping service")
        val serviceIntent = Intent(this, LocationService::class.java).apply {
            action = LocationService.ACTION_STOP
        }
        startService(serviceIntent) // Send stop command
        isServiceRunning = false
        updateUI()
    }

    private fun updateUI() {
        if (isServiceRunning) {
            binding.toggleButton.text = "Stop Service"
            binding.statusText.text = "Service Running"
        } else {
            binding.toggleButton.text = "Start Service"
            binding.statusText.text = "Service Stopped"
        }
    }

    // --- Permission Handling ---

    private fun hasFineLocationPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun hasBackgroundLocationPermission() =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED
        } else {
            true // Not needed before Android Q
        }

    private val requestFineLocationLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                Log.d(TAG, "ACCESS_FINE_LOCATION granted")
                checkPermissionsAndStartService() // Re-check to proceed to background permission or start
            } else {
                Log.w(TAG, "ACCESS_FINE_LOCATION denied")
                // Optionally, show a rationale to the user
            }
        }

    private val requestBackgroundLocationLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                Log.d(TAG, "ACCESS_BACKGROUND_LOCATION granted")
                startLocationService()
            } else {
                Log.w(TAG, "ACCESS_BACKGROUND_LOCATION denied")
                // Optionally, show a rationale to the user
            }
        }

    private fun requestFineLocationPermission() {
        requestFineLocationLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    private fun requestBackgroundLocationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            requestBackgroundLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}
