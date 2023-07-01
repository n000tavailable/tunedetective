package com.n0tavailable.tunedetective

import android.annotation.SuppressLint
import android.app.Activity
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
import java.io.File
import java.io.IOException
import java.io.OutputStream

class SettingsActivity : AppCompatActivity() {
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var setRepeatInterval: EditText
    private val EXPORT_REQUEST_CODE = 1
    private val IMPORT_REQUEST_CODE = 2



    companion object {
        const val PREF_REPEAT_INTERVAL = "repeatInterval"
        const val DEFAULT_REPEAT_INTERVAL = 60 // Default interval in minutes
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (resultCode == Activity.RESULT_OK) {
            when (requestCode) {
                EXPORT_REQUEST_CODE -> {
                    data?.data?.let { uri ->
                        val contentResolver = contentResolver
                        val outputStream: OutputStream? = contentResolver.openOutputStream(uri)
                        if (outputStream != null) {
                            val helper = SearchHistoryDatabaseHelper(this)
                            val dbPath = this.getDatabasePath(SearchHistoryDatabaseHelper.DATABASE_NAME).absolutePath
                            val sourceFile = File(dbPath)
                            try {
                                outputStream.write(sourceFile.readBytes())
                                outputStream.close()
                                Toast.makeText(this, "Database exported successfully", Toast.LENGTH_SHORT).show()
                            } catch (e: IOException) {
                                e.printStackTrace()
                                Toast.makeText(this, "Failed to export database", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            Toast.makeText(this, "Failed to open output stream", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                IMPORT_REQUEST_CODE -> {
                    data?.data?.let { uri ->
                        val helper = SearchHistoryDatabaseHelper(this)
                        val importResult = helper.importDatabase(this, uri)
                        if (importResult) {
                            Toast.makeText(this, "Database imported successfully", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this, "Failed to import database", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
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
        val exportButton = findViewById<Button>(R.id.exportButton)
        val importButton = findViewById<Button>(R.id.importButton)

        sharedPreferences = getSharedPreferences("MyPrefs", MODE_PRIVATE)


        // Load the saved interval and display it in the EditText
        val savedInterval = sharedPreferences.getInt(PREF_REPEAT_INTERVAL, DEFAULT_REPEAT_INTERVAL)
        setRepeatInterval.setText(savedInterval.toString())

        btnSave.setOnClickListener {
            val interval = setRepeatInterval.text.toString().toIntOrNull() ?: DEFAULT_REPEAT_INTERVAL
            saveRepeatInterval(interval)
            Toast.makeText(this, "Interval saved!", Toast.LENGTH_SHORT).show()
        }

        exportButton.setOnClickListener {
            selectExportLocation()
        }

        importButton.setOnClickListener {
            importDatabase()
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

    private fun selectExportLocation() {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT)
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        intent.type = "application/octet-stream"
        intent.putExtra(Intent.EXTRA_TITLE, "search_history_backup.db")
        startActivityForResult(intent, EXPORT_REQUEST_CODE)
    }

    private fun importDatabase() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        intent.type = "application/octet-stream"
        startActivityForResult(intent, IMPORT_REQUEST_CODE)
    }
}