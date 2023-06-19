package com.n0tavailable.tunedetective

import android.annotation.SuppressLint
import android.content.Intent
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.CountDownTimer
import android.view.LayoutInflater
import android.widget.ImageButton
import android.widget.Switch
import android.widget.TextView
import android.widget.ToggleButton
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat

class SettingsActivity : AppCompatActivity() {
    private lateinit var sharedPreferences: SharedPreferences

    @SuppressLint("UseSwitchCompatOrMaterialCode")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        sharedPreferences = getSharedPreferences("MyPrefs", MODE_PRIVATE)

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
            startActivity(intent)
        }

        aboutButton.setOnClickListener {
            val intent = Intent(this@SettingsActivity, AboutActivity::class.java)
            startActivity(intent)
        }

        releasesButton.setOnClickListener {
            val intent = Intent(this@SettingsActivity, ReleasesActivity::class.java)
            startActivity(intent)
        }

        settingsButton.setOnClickListener {
            val intent = Intent(this@SettingsActivity, SettingsActivity::class.java)
            startActivity(intent)
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