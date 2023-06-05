package com.n0tavailable.tunedetective

import android.annotation.SuppressLint
import android.app.Dialog
import android.app.ProgressDialog
import android.graphics.drawable.Drawable
import android.media.MediaPlayer
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.request.RequestOptions
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.Timer
import java.util.TimerTask


class MainActivity : AppCompatActivity() {
    private lateinit var searchButton: Button
    private lateinit var displayTracks: Button
    private lateinit var artistEditText: EditText
    private lateinit var trackTitleTextView: TextView
    private lateinit var albumCoverImageView: ImageView
    private lateinit var releaseDateTextView: TextView
    private lateinit var fullscreenDialog: Dialog
    private lateinit var albumCoverLayout: LinearLayout
    private lateinit var progressDialog: ProgressDialog
    private lateinit var timer: Timer

    private var mediaPlayer: MediaPlayer? = null



    override fun onDestroy() {
        super.onDestroy()
        stopPlayback()
    }

    private fun stopPlayback() {
        mediaPlayer?.release()
        mediaPlayer = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        progressDialog = ProgressDialog(this)
        progressDialog.setMessage("Loading data...")
        progressDialog.setCancelable(false)

        setContentView(R.layout.activity_main)


        // Display a welcome message based on the time of day
        val welcomeMessage = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
            in 0..5 -> "Good night!"
            in 6..11 -> "Good morning!"
            in 12..17 -> "Good afternoon!"
            else -> "Good evening!"
        }

        val currentTime = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Calendar.getInstance().time)
        val welcomeMessageWithTime = "$welcomeMessage"

        findViewById<TextView>(R.id.welcomeMessageTextView).text = welcomeMessageWithTime

        // Initialize views
        searchButton = findViewById(R.id.searchButton)
        displayTracks = findViewById(R.id.displayTracks)
        artistEditText = findViewById(R.id.artistEditText)
        trackTitleTextView = findViewById(R.id.trackTitleTextView)
        albumCoverImageView = findViewById(R.id.albumCoverImageView)
        releaseDateTextView = findViewById(R.id.releaseDateTextView)
        albumCoverLayout = findViewById(R.id.albumCoverLayout)

        // Set initial visibility of views
        albumCoverLayout.visibility = View.GONE
        trackTitleTextView.visibility = View.GONE
        releaseDateTextView.visibility = View.GONE
        fullscreenDialog = Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)

        // Set background resource for album cover image view
        albumCoverImageView.setBackgroundResource(R.drawable.round_album_cover)

        // Set click listener for search button
        searchButton.setOnClickListener {
            val artistName = artistEditText.text.toString()
            Toast.makeText(this, "Searching for data...", Toast.LENGTH_SHORT).show()
            hideKeyboard()
            searchArtist(artistName)
        }

        // Set click listener for album cover image view to show fullscreen image
        albumCoverImageView.setOnClickListener {
            val drawable = albumCoverImageView.drawable
            if (drawable != null) {
                showFullscreenImage(drawable)
            }
        }
    }




    // Search for an artist using Deezer API
    private fun searchArtist(artistName: String) {
        val apiKey = APIKeys.DEEZER_API_KEY
        val client = OkHttpClient()
        val request = Request.Builder()
            .url("https://api.deezer.com/search/artist?q=$artistName")
            .addHeader("Authorization", "Bearer $apiKey")
            .build()

        progressDialog.show()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                e.printStackTrace()
                progressDialog.dismiss()
            }

            override fun onResponse(call: Call, response: Response) {
                val responseData = response.body?.string()

                progressDialog.dismiss()


                if (response.isSuccessful && responseData != null) {
                    val jsonResponse = JSONObject(responseData)
                    val artistArray = jsonResponse.getJSONArray("data")

                    if (artistArray.length() > 0) {
                        val artist = artistArray.getJSONObject(0)
                        val artistId = artist.getString("id")
                        val artistImageUrl = artist.getString("picture_big")
                        getLatestRelease(artistId, artistImageUrl)
                    } else {
                        runOnUiThread {
                            trackTitleTextView.text = "No artist found"
                            albumCoverImageView.setImageResource(R.drawable.round_music_note_24)
                            releaseDateTextView.text = ""
                        }
                    }
                } else {
                    println("Error: ${response.code} ${response.message}")
                }
            }
        })
    }

    // Get the latest release by an artist using Deezer API
    private fun getLatestRelease(artistId: String, artistImageUrl: String) {
        val apiKey = APIKeys.DEEZER_API_KEY
        val client = OkHttpClient()
        val request = Request.Builder()
            .url("https://api.deezer.com/artist/$artistId/albums")
            .addHeader("Authorization", "Bearer $apiKey")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                e.printStackTrace()
            }

            override fun onResponse(call: Call, response: Response) {
                val responseData = response.body?.string()

                if (response.isSuccessful && responseData != null) {
                    val jsonResponse = JSONObject(responseData)
                    val albumArray = jsonResponse.getJSONArray("data")

                    if (albumArray.length() > 0) {
                        var latestRelease: JSONObject? = null
                        var latestReleaseDate: String? = null

                        for (i in 0 until albumArray.length()) {
                            val album = albumArray.getJSONObject(i)
                            val releaseDate = album.getString("release_date")

                            if (latestRelease == null || releaseDate > latestReleaseDate.toString()) {
                                latestRelease = album
                                latestReleaseDate = releaseDate
                            }
                        }

                        if (latestRelease != null) {
                            val albumId = latestRelease.getString("id")
                            val albumCoverUrl = latestRelease.getString("cover_big")
                            getAlbumDetails(albumId, albumCoverUrl, latestReleaseDate, artistImageUrl)
                        } else {
                            runOnUiThread {
                                trackTitleTextView.text = "No releases found"
                                albumCoverImageView.setImageResource(R.drawable.round_music_note_24)
                                releaseDateTextView.text = ""
                            }
                        }
                    } else {
                        runOnUiThread {
                            trackTitleTextView.text = "No releases found"
                            albumCoverImageView.setImageResource(R.drawable.round_music_note_24)
                            releaseDateTextView.text = ""
                        }
                    }
                } else {
                    println("Error: ${response.code} ${response.message}")
                }
            }
        })
    }

    // Get details of an album using Deezer API
    // Get details of an album using Deezer API
    private fun getAlbumDetails(albumId: String, albumCoverUrl: String, releaseDate: String?, artistImageUrl: String) {
        val apiKey = APIKeys.DEEZER_API_KEY
        val sdfInput = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val sdfOutput = SimpleDateFormat("dd. MMMM yyyy", Locale.getDefault())

        val formattedReleaseDate = try {
            val date = sdfInput.parse(releaseDate)
            sdfOutput.format(date)
        } catch (e: Exception) {
            releaseDate
        }
        val client = OkHttpClient()
        val request = Request.Builder()
            .url("https://api.deezer.com/album/$albumId")
            .addHeader("Authorization", "Bearer $apiKey")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                e.printStackTrace()
            }

            override fun onResponse(call: Call, response: Response) {
                val responseData = response.body?.string()

                if (response.isSuccessful && responseData != null) {
                    val jsonResponse = JSONObject(responseData)
                    val albumTitle = jsonResponse.getString("title")
                    val artistName = jsonResponse.getJSONObject("artist").getString("name")
                    val tracklistUrl = jsonResponse.getString("tracklist")

                    runOnUiThread {
                        albumCoverLayout.visibility = View.VISIBLE
                        trackTitleTextView.visibility = View.VISIBLE
                        releaseDateTextView.visibility = View.VISIBLE
                        trackTitleTextView.text = albumTitle
                        loadAlbumCoverImage(albumCoverUrl)
                        releaseDateTextView.text = "Release Date: $formattedReleaseDate"

                        // Add click listener to open the tracklist URL
                        displayTracks.setOnClickListener {
                            getTrackList(tracklistUrl)
                        }
                    }
                } else {
                    println("Error: ${response.code} ${response.message}")
                }
            }
        })
    }

    // Get the tracklist using Deezer API
    private fun getTrackList(tracklistUrl: String) {
        val apiKey = APIKeys.DEEZER_API_KEY
        val client = OkHttpClient()
        val request = Request.Builder()
            .url(tracklistUrl)
            .addHeader("Authorization", "Bearer $apiKey")
            .build()


        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                e.printStackTrace()
            }

            override fun onResponse(call: Call, response: Response) {
                val responseData = response.body?.string()

                if (response.isSuccessful && responseData != null) {
                    val jsonResponse = JSONObject(responseData)
                    val tracksArray = jsonResponse.getJSONArray("data")
                    val trackList = mutableListOf<Track>()

                    for (i in 0 until tracksArray.length()) {
                        val track = tracksArray.getJSONObject(i)
                        val trackTitle = track.getString("title")
                        val previewUrl = track.getString("preview")
                        trackList.add(Track(trackTitle, previewUrl))
                    }



                    runOnUiThread {
                        showTrackListDialog(trackList)
                    }
                } else {
                    println("Error: ${response.code} ${response.message}")
                }
            }
        })
    }

    // Show the tracklist in a dialog
// Show the tracklist in a dialog
    private fun showTrackListDialog(trackList: List<Track>) {
        val dialog = Dialog(this)
        dialog.setContentView(R.layout.dialog_tracklist)

        val trackListRecyclerView = dialog.findViewById<RecyclerView>(R.id.trackListRecyclerView)
        val closeButton = dialog.findViewById<Button>(R.id.closeButton)

        val layoutManager = LinearLayoutManager(this)
        val adapter = TrackListAdapter(trackList)

        trackListRecyclerView.layoutManager = layoutManager
        trackListRecyclerView.adapter = adapter

        closeButton.setOnClickListener {
            dialog.dismiss()
            adapter.stopPlayback()
        }

        dialog.setOnDismissListener {
            adapter.stopPlayback()
        }

        dialog.show()
    }




    // Load album cover image using Glide library
    private fun loadAlbumCoverImage(url: String) {
        Glide.with(this)
            .load(url)
            .apply(RequestOptions().transform(RoundedCorners(50)))
            .into(albumCoverImageView)
    }



    // Show a fullscreen image in a dialog
    private fun showFullscreenImage(drawable: Drawable) {
        val imageView = ImageView(this)
        imageView.setImageDrawable(drawable)

        fullscreenDialog.setContentView(imageView)
        fullscreenDialog.show()
    }

    // Hide the keyboard
    private fun hideKeyboard() {
        val inputMethodManager = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        val currentFocusView = currentFocus
        if (currentFocusView != null) {
            inputMethodManager.hideSoftInputFromWindow(currentFocusView.windowToken, 0)
        }
    }
}

class Track(val title: String, val previewUrl: String)


class TrackListAdapter(private val trackList: List<Track>) : RecyclerView.Adapter<TrackListAdapter.TrackViewHolder>() {

    private var mediaPlayer: MediaPlayer? = null
    private var currentlyPlayingPosition: Int = -1

    fun stopPlayback() {
        mediaPlayer?.release()
        mediaPlayer = null
        currentlyPlayingPosition = -1
    }

    inner class TrackViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val titleTextView: TextView = itemView.findViewById(R.id.titleTextView)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TrackViewHolder {
        val itemView = LayoutInflater.from(parent.context).inflate(R.layout.item_track, parent, false)
        return TrackViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: TrackViewHolder, @SuppressLint("RecyclerView") position: Int) {
        val track = trackList[position]
        holder.titleTextView.text = track.title

        holder.itemView.setOnClickListener {
            if (currentlyPlayingPosition == position) {
                // Track is currently playing, so pause it
                mediaPlayer?.pause()
                mediaPlayer?.release()
                mediaPlayer = null
                currentlyPlayingPosition = -1
            } else {
                // Track is not playing or a different track is playing, so play it
                mediaPlayer?.release()
                mediaPlayer = MediaPlayer().apply {
                    setDataSource(track.previewUrl)
                    prepareAsync()
                    setOnPreparedListener {
                        start()
                        currentlyPlayingPosition = position
                    }
                    setOnCompletionListener {
                        release()
                        currentlyPlayingPosition = -1
                    }
                }
            }
        }
    }

    override fun getItemCount(): Int {
        return trackList.size
    }
}