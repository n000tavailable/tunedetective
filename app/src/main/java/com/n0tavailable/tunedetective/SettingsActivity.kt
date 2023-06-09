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
        val toggleButton = findViewById<Switch>(R.id.toggleButton)

        toggleButton.isChecked = sharedPreferences.getBoolean("pepeGifEnabled", true)
        toggleButton.setOnCheckedChangeListener { _, isChecked ->
            sharedPreferences.edit().putBoolean("pepeGifEnabled", isChecked).apply()
        }
    }
}