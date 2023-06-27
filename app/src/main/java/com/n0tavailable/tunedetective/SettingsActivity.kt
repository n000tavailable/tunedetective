package com.n0tavailable.tunedetective

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.CountDownTimer
import android.provider.Settings
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
import androidx.preference.PreferenceManager

class SettingsActivity : AppCompatActivity() {
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var setRepeatInterval: EditText


    companion object {
        const val PREF_REPEAT_INTERVAL = "repeatInterval"
        const val DEFAULT_REPEAT_INTERVAL = 60 // Default interval in minutes
    }

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

        setRepeatInterval = findViewById(R.id.setRepeatInterval)
        val btnSave = findViewById<Button>(R.id.btnSave)

        sharedPreferences = getSharedPreferences("MyPrefs", MODE_PRIVATE)


        // Load the saved interval and display it in the EditText
        val savedInterval = sharedPreferences.getInt(PREF_REPEAT_INTERVAL, DEFAULT_REPEAT_INTERVAL)
        setRepeatInterval.setText(savedInterval.toString())

        btnSave.setOnClickListener {
            val interval = setRepeatInterval.text.toString().toIntOrNull() ?: DEFAULT_REPEAT_INTERVAL
            saveRepeatInterval(interval)
            Toast.makeText(this, "Interval saved!", Toast.LENGTH_SHORT).show()
        }

        val notificationSettingsButton = findViewById<Button>(R.id.notificationSettingsButton)
        notificationSettingsButton.setOnClickListener {
            openNotificationSettings()
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
            // Check if the current activity is already SettingsActivity
            if (!isTaskRoot) {
                // If not, navigate back to SettingsActivity instead of recreating it
                onBackPressed()
            }
        }
    }

    private fun saveRepeatInterval(interval: Int) {
        val editor = sharedPreferences.edit()
        editor.putInt(PREF_REPEAT_INTERVAL, interval)
        editor.apply()
    }

    private fun openNotificationSettings() {
        val intent = Intent()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            intent.action = Settings.ACTION_APP_NOTIFICATION_SETTINGS
            intent.putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        } else {
            intent.action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
            intent.addCategory(Intent.CATEGORY_DEFAULT)
            intent.data = Uri.parse("package:$packageName")
        }
        startActivity(intent)
    }
}