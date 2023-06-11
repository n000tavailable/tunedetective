package com.n0tavailable.tunedetective

import android.annotation.SuppressLint
import android.content.Intent
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.CountDownTimer
import android.view.LayoutInflater
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

        val pepeGifSwitch = findViewById<Switch>(R.id.pepeGifSwitch)
        val welcomeMessageToggleButton = findViewById<Switch>(R.id.welcomeMessageToggleButton)
        val discographyButtonSwitch = findViewById<Switch>(R.id.discographyButtonSwitch)

        pepeGifSwitch.isChecked = sharedPreferences.getBoolean("pepeGifEnabled", true)
        pepeGifSwitch.setOnCheckedChangeListener { _, isChecked ->
            sharedPreferences.edit().putBoolean("pepeGifEnabled", isChecked).apply()
        }

        welcomeMessageToggleButton.isChecked =
            sharedPreferences.getBoolean("welcomeMessageVisible", true)
        welcomeMessageToggleButton.setOnCheckedChangeListener { _, isChecked ->
            sharedPreferences.edit().putBoolean("welcomeMessageVisible", isChecked).apply()
        }

        discographyButtonSwitch.isChecked =
            sharedPreferences.getBoolean("discographyButtonVisible", false)
        discographyButtonSwitch.setOnCheckedChangeListener { _, isChecked ->
            sharedPreferences.edit().putBoolean("discographyButtonVisible", isChecked).apply()
            showDialogAndRestart()
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