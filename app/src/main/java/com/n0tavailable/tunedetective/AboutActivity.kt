package com.n0tavailable.tunedetective

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.Spinner
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.google.android.material.bottomnavigation.BottomNavigationView


class AboutActivity : AppCompatActivity() {

    override fun onBackPressed() {
        val intent = Intent(this@AboutActivity, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        startActivity(intent)
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about)

        //    val profileImageView: ImageView = findViewById(R.id.profile)
        //    Glide.with(this)
        //         .load(R.drawable.profile)
        //        .apply(RequestOptions.circleCropTransform())
        //        .into(profileImageView)

        val joinMatrixButton: Button = findViewById(R.id.joinMatrix)
        joinMatrixButton.setOnClickListener {
            val uri =
                Uri.parse("https://matrix.to/#/#tunedetective:matrix.org") // Replace with your actual URL
            val intent = Intent(Intent.ACTION_VIEW, uri)
            startActivity(intent)
        }

        val githubPage: Button = findViewById(R.id.githubPage)
        githubPage.setOnClickListener {
            val uri =
                Uri.parse("https://github.com/n000tavailable/tunedetective") // Replace with your actual URL
            val intent = Intent(Intent.ACTION_VIEW, uri)
            startActivity(intent)
        }


        val bottomNavigation = findViewById<BottomNavigationView>(R.id.bottomNavigation)

        bottomNavigation.setOnNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.menu_home -> {
                    // Handle home button click
                    val intent = Intent(this@AboutActivity, MainActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    startActivity(intent)
                    finish()
                    true // Return true to indicate that the event is handled
                }
                R.id.menu_releases -> {
                    val intent = Intent(this@AboutActivity, ReleasesActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    startActivity(intent)
                    true // Return true to indicate that the event is handled
                }
                R.id.menu_settings -> {
                    // Handle settings button click
                    val intent = Intent(this@AboutActivity, SettingsActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    startActivity(intent)
                    finish()
                    true // Return true to indicate that the event is handled
                }
                R.id.menu_info -> {
                    // Handle info button click
                    val intent = Intent(this@AboutActivity, AboutActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    startActivity(intent)
                    true // Return true to indicate that the event is handled
                }
                else -> false // Return false for unhandled menu items
            }
        }

        bottomNavigation.selectedItemId = R.id.menu_info
    }
}