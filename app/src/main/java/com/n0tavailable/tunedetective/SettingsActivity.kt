package com.n0tavailable.tunedetective

import android.annotation.SuppressLint
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Switch
import android.widget.ToggleButton

class SettingsActivity : AppCompatActivity() {
    private lateinit var sharedPreferences: SharedPreferences

    @SuppressLint("UseSwitchCompatOrMaterialCode")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        sharedPreferences = getSharedPreferences("MyPrefs", MODE_PRIVATE)
        val pepeGifSwitch = findViewById<Switch>(R.id.pepeGifSwitch)
        val welcomeMessageToggleButton = findViewById<Switch>(R.id.welcomeMessageToggleButton)

        pepeGifSwitch.isChecked = sharedPreferences.getBoolean("pepeGifEnabled", true)
        pepeGifSwitch.setOnCheckedChangeListener { _, isChecked ->
            sharedPreferences.edit().putBoolean("pepeGifEnabled", isChecked).apply()
        }

        welcomeMessageToggleButton.isChecked = sharedPreferences.getBoolean("welcomeMessageVisible", true)
        welcomeMessageToggleButton.setOnCheckedChangeListener { _, isChecked ->
            sharedPreferences.edit().putBoolean("welcomeMessageVisible", isChecked).apply()
        }
    }
}