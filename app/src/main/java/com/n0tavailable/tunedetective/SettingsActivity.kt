package com.n0tavailable.tunedetective

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.CountDownTimer
import android.view.LayoutInflater
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import android.widget.ToggleButton
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat

class SettingsActivity : AppCompatActivity() {
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var intervalEditText: EditText
    private lateinit var saveButton: Button

    override fun onBackPressed() {
        val intent = Intent(this@SettingsActivity, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        startActivity(intent)
        finish()
    }

    @SuppressLint("UseSwitchCompatOrMaterialCode")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        intervalEditText = findViewById(R.id.intervalEditText)
        saveButton = findViewById(R.id.saveButton)
        sharedPreferences = getSharedPreferences("MyPrefs", MODE_PRIVATE)

        // Add click listener to the saveButton
        saveButton.setOnClickListener {
            saveIntervalTime()
        }

        val savedInterval = sharedPreferences.getLong("intervalTime", 60)
        intervalEditText.setText(savedInterval.toString())

        val saveButton = findViewById<Button>(R.id.saveButton)
        saveButton.setOnClickListener {
            val interval = intervalEditText.text.toString().toLongOrNull() ?: 60
            saveIntervalTime(interval)
            Toast.makeText(this, "Interval time saved", Toast.LENGTH_SHORT).show()
        }

        val welcomeMessageToggleButton = findViewById<Switch>(R.id.welcomeMessageToggleButton)

        welcomeMessageToggleButton.isChecked =
            sharedPreferences.getBoolean("welcomeMessageVisible", true)
        welcomeMessageToggleButton.setOnCheckedChangeListener { _, isChecked ->
            sharedPreferences.edit().putBoolean("welcomeMessageVisible", isChecked).apply()
        }

        val aboutButton = findViewById<ImageButton>(R.id.infoButton)
        val homeButton = findViewById<ImageButton>(R.id.homeButton)
        val releasesButton = findViewById<ImageButton>(R.id.releasesButton)
        val settingsButton = findViewById<ImageButton>(R.id.settingsButton)
        val notificationsButton = findViewById<Button>(R.id.notificationsButton)


        homeButton.setOnClickListener {
            val intent = Intent(this@SettingsActivity, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(intent)
            finish()
        }

        aboutButton.setOnClickListener {
            val intent = Intent(this@SettingsActivity, AboutActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(intent)
            finish()
        }

        releasesButton.setOnClickListener {
            val intent = Intent(this@SettingsActivity, ReleasesActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(intent)
            finish()
        }

        settingsButton.setOnClickListener {
            val intent = Intent(this@SettingsActivity, SettingsActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(intent)
            finish()
        }

        notificationsButton.setOnClickListener {
            // Launch the app notifications configuration activity
            val intent = Intent(this@SettingsActivity, NotificationsActivity::class.java)
            startActivity(intent)
        }
    }

    private fun saveIntervalTime() {
        val intervalString = intervalEditText.text.toString()
        val intervalMinutes = intervalString.toIntOrNull()

        if (intervalMinutes != null && intervalMinutes > 0) {
            // Save the interval time to SharedPreferences or any other storage mechanism
            // You can use SharedPreferences to store the interval time
            val sharedPreferences = getSharedPreferences("MyPrefs", Context.MODE_PRIVATE)
            val editor = sharedPreferences.edit()
            editor.putInt("intervalTime", intervalMinutes)
            editor.apply()

            Toast.makeText(this, "Interval time saved", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Invalid interval time", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showDialogAndRestart() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_restart_app, null)
        val messageTextView = dialogView.findViewById<TextView>(R.id.dialogMessageTextView)

        val alertDialogBuilder = AlertDialog.Builder(this)
            .setTitle("Restart App")
            .setView(dialogView)
            .setCancelable(false)

        val dialog = alertDialogBuilder.create()
        dialog.show()

        object : CountDownTimer(5000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                messageTextView.text = "The app will restart in ${millisUntilFinished / 1000} seconds..."
            }

            override fun onFinish() {
                dialog.dismiss()
                restartApp()
            }
        }.start()
    }

    private fun saveIntervalTime(interval: Long) {
        val editor = sharedPreferences.edit()
        editor.putLong("intervalTime", interval)
        editor.apply()
    }

    private fun restartApp() {
        val packageManager = packageManager
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        if (intent != null) {
            val componentName = intent.component
            val mainIntent = Intent.makeRestartActivityTask(componentName)
            startActivity(mainIntent)
            Runtime.getRuntime().exit(0)
        }
    }
}

class NotificationsActivity : AppCompatActivity() {

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Open the notification settings for the app
        val intent = Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        intent.putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, packageName)
        startActivity(intent)

        finish() // Close the NotificationsActivity after opening the notification settings
    }
}