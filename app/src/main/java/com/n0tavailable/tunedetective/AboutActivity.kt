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


class AboutActivity : AppCompatActivity() {

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


        val aboutButton = findViewById<ImageButton>(R.id.infoButton)
        val homeButton = findViewById<ImageButton>(R.id.homeButton)
        val releasesButton = findViewById<ImageButton>(R.id.releasesButton)
        val settingsButton = findViewById<ImageButton>(R.id.settingsButton)

        homeButton.setOnClickListener {
            val intent = Intent(this@AboutActivity, MainActivity::class.java)
            startActivity(intent)
        }

        aboutButton.setOnClickListener {
            val intent = Intent(this@AboutActivity, AboutActivity::class.java)
            startActivity(intent)
        }

        releasesButton.setOnClickListener {
            val intent = Intent(this@AboutActivity, ReleasesActivity::class.java)
            startActivity(intent)
        }

        settingsButton.setOnClickListener {
            val intent = Intent(this@AboutActivity, SettingsActivity::class.java)
            startActivity(intent)
        }
    }
}