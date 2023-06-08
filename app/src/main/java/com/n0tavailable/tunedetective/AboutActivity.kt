package com.n0tavailable.tunedetective

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ImageView
import android.widget.Spinner
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions


class AboutActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about)

        val spinner: Spinner = findViewById(R.id.spinner)
        val options = arrayOf("About", "Home")

        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, options)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter

        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                val selectedItem = parent.getItemAtPosition(position) as String
                if (selectedItem == "Home") {
                    val intent = Intent(this@AboutActivity, MainActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
                    startActivity(intent)
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>) {
                // Do nothing
            }
        }

        val profileImageView: ImageView = findViewById(R.id.profile)
        Glide.with(this)
            .load(R.drawable.profile)
            .apply(RequestOptions.circleCropTransform())
            .into(profileImageView)

        val joinMatrixButton: Button = findViewById(R.id.joinMatrix)
        joinMatrixButton.setOnClickListener {
            val uri = Uri.parse("https://matrix.to/#/#tunedetective:matrix.org") // Replace with your actual URL
            val intent = Intent(Intent.ACTION_VIEW, uri)
            startActivity(intent)
        }
    }
}