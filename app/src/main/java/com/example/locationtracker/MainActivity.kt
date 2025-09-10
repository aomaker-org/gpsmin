package com.example.locationtracker

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer
import androidx.work.*
import com.example.locationtracker.databinding.ActivityMainBinding
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var workManager: WorkManager
    private var isWorkScheduled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        workManager = WorkManager.getInstance(applicationContext)

        binding.toggleButton.setOnClickListener {
            if (isWorkScheduled) {
                cancelLocationWork()
            } else {
                checkPermissionsAndStartWork()
            }
        }
        observeWorkStatus()
    }

    private fun checkPermissionsAndStartWork() {
        if (hasFineLocationPermission()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                if (hasBackgroundLocationPermission()) {
                    scheduleLocationWork()
                } else {
                    requestBackgroundLocationPermission()
                }
            } else {
                scheduleLocationWork()
            }
        } else {
            requestFineLocationPermission()
        }
    }

    private fun scheduleLocationWork() {
        Log.d(TAG, "Scheduling work")
        try {
            val config = ConfigLoader(this).loadConfig()
            val periodicWorkRequest = PeriodicWorkRequestBuilder<LocationWorker>(
                config.loggingIntervalMinutes, TimeUnit.MINUTES
            )
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .addTag(LOCATION_WORK_TAG)
                .build()

            workManager.enqueueUniquePeriodicWork(
                LOCATION_WORK_TAG,
                ExistingPeriodicWorkPolicy.KEEP,
                periodicWorkRequest
            )
            Toast.makeText(this, "Location tracking scheduled.", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load config or schedule work", e)
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun cancelLocationWork() {
        Log.d(TAG, "Cancelling work")
        workManager.cancelUniqueWork(LOCATION_WORK_TAG)
        Toast.makeText(this, "Location tracking cancelled.", Toast.LENGTH_SHORT).show()
    }

    private fun observeWorkStatus() {
        workManager.getWorkInfosForUniqueWorkLiveData(LOCATION_WORK_TAG).observe(this, Observer { workInfos ->
            val workInfo = workInfos.firstOrNull()
            isWorkScheduled = workInfo != null && !workInfo.state.isFinished
            updateUI()
            workInfo?.let {
                Log.d(TAG, "Work status changed: ${it.state}")
            }
        })
    }

    private fun updateUI() {
        if (isWorkScheduled) {
            binding.toggleButton.text = "Stop Tracking"
            binding.statusText.text = "Tracking Scheduled"
        } else {
            binding.toggleButton.text = "Start Tracking"
            binding.statusText.text = "Tracking Stopped"
        }
    }

    // --- Permission Handling ---
    private fun hasFineLocationPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun hasBackgroundLocationPermission() =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED
        } else { true }

    private val requestFineLocationLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                checkPermissionsAndStartWork()
            } else {
                Toast.makeText(this, "Fine location permission is required.", Toast.LENGTH_SHORT).show()
            }
        }

    private val requestBackgroundLocationLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                scheduleLocationWork()
            } else {
                Toast.makeText(this, "Background location permission is required for tracking.", Toast.LENGTH_SHORT).show()
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
        private const val LOCATION_WORK_TAG = "LocationTrackerWork"
    }
}
