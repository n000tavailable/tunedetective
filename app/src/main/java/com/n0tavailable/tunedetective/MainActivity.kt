package com.n0tavailable.tunedetective

import android.annotation.SuppressLint
import android.app.Dialog
import android.app.ProgressDialog
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import android.widget.ToggleButton
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.text.HtmlCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bumptech.glide.request.RequestOptions
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import pl.droidsonroids.gif.GifImageView
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {
    private lateinit var searchButton: Button
    private lateinit var displayTracks: Button
    private lateinit var discographyButton: Button
    private lateinit var artistEditText: EditText
    private lateinit var trackTitleTextView: TextView
    private lateinit var albumCoverImageView: ImageView
    private lateinit var releaseDateTextView: TextView
    private lateinit var fullscreenDialog: Dialog
    private lateinit var albumCoverLayout: LinearLayout
    private lateinit var progressDialog: ProgressDialog
    private lateinit var searchHistoryDatabaseHelper: SearchHistoryDatabaseHelper
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var welcomeMessageTextView: TextView
    private lateinit var artistNameTextView: TextView
    private var welcomeMessageVisible = true
    private var mediaPlayer: MediaPlayer? = null
    private var pepeGifEnabled = true
    private var artistName: String? = null
    private var selectedArtist: String? = null



    override fun onDestroy() {
        super.onDestroy()
        stopPlayback()
        resetLayout()
    }

    override fun onStop() {
        super.onStop()
        stopPlayback()
        resetLayout()
    }

    private fun stopPlayback() {
        mediaPlayer?.release()
        mediaPlayer = null
    }

    override fun onPause() {
        super.onPause()
    }

    override fun onResume() {
        super.onResume()

        // Retrieve the toggle state from SharedPreferences
        welcomeMessageVisible = sharedPreferences.getBoolean("welcomeMessageVisible", true)

        updateWelcomeMessageVisibility()

        // Retrieve the toggle state from SharedPreferences
        pepeGifEnabled = sharedPreferences.getBoolean("pepeGifEnabled", true)

        // Update the PepeGif visibility
        updatePepeGifVisibility()

        updateWelcomeMessageWithTime()

    }

    private fun updateWelcomeMessageWithTime() {
        val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val welcomeMessageWithTime = when (currentHour) {
            in 0..4 -> "It's late at night."
            in 5..8 -> "Enjoy the early morning!"
            in 9..11 -> "Have a productive day!"
            in 12..14 -> "It's lunchtime!"
            in 15..17 -> "Keep up the good work!"
            in 18..20 -> "Relax in the evening."
            else -> "Have a pleasant night!"
        }

        welcomeMessageTextView.text = welcomeMessageWithTime
    }

    private fun resetLayout() {
        albumCoverLayout.visibility = View.GONE
        trackTitleTextView.visibility = View.GONE
        releaseDateTextView.visibility = View.GONE
        albumCoverImageView.setImageResource(R.drawable.round_album_cover)
        artistEditText.text = null

        val pepeGif = findViewById<GifImageView>(R.id.pepeGif)
        // Hide the PepeGif based on the toggle state
        if (pepeGifEnabled) {
            pepeGif.visibility = View.VISIBLE
        } else {
            pepeGif.visibility = View.GONE
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        progressDialog = ProgressDialog(this)
        progressDialog.setMessage("Loading data...")
        progressDialog.setCancelable(false)

        setContentView(R.layout.activity_main)

        discographyButton = findViewById(R.id.discographyButton)


        sharedPreferences = getSharedPreferences("MyPrefs", Context.MODE_PRIVATE)


        showFeedbackDialog()

        val discographyButton = findViewById<Button>(R.id.discographyButton)

        // Initialize the welcomeMessageTextView
        welcomeMessageTextView = findViewById(R.id.welcomeMessageTextView)

        // Initialize the SharedPreferences instance
        sharedPreferences = getSharedPreferences("MyPrefs", Context.MODE_PRIVATE)

        // Retrieve the toggle state from SharedPreferences
        pepeGifEnabled = sharedPreferences.getBoolean("pepeGifEnabled", true)

        val showSearchHistoryButton: Button = findViewById(R.id.showSearchHistoryButton)
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_search_history, null)


        val historyListView = dialogView.findViewById<ListView>(R.id.historyListView)
        val maxHeight = resources.getDimensionPixelSize(R.dimen.max_listview_height)
        historyListView.layoutParams.height =
            Math.min(historyListView.layoutParams.height, maxHeight)



        searchHistoryDatabaseHelper = SearchHistoryDatabaseHelper(this)

        showSearchHistoryButton.setOnClickListener {

            val pepeGif = findViewById<GifImageView>(R.id.pepeGif)
            // Hide the PepeGif based on the toggle state
            if (pepeGifEnabled) {
                pepeGif.visibility = View.GONE
            }

            hideKeyboard()


            val searchHistory = searchHistoryDatabaseHelper.getLatestSearchQueries(20)
            val historyListView = dialogView.findViewById<ListView>(R.id.historyListView)
            val maxHeight = resources.getDimensionPixelSize(R.dimen.max_listview_height)
            historyListView.layoutParams.height = maxHeight
            val closeButton = dialogView.findViewById<Button>(R.id.closeButton)
            val fetchReleasesButton = dialogView.findViewById<Button>(R.id.fetchReleasesButton)

            val historyAdapter =
                ArrayAdapter(this, android.R.layout.simple_list_item_1, searchHistory)
            historyListView.adapter = historyAdapter

            val alertDialogBuilder = AlertDialog.Builder(this)
            alertDialogBuilder.setView(dialogView)

            val alertDialog = alertDialogBuilder.create()
            (dialogView.parent as? ViewGroup)?.removeView(dialogView)

            alertDialog.show()

            historyListView.setOnItemClickListener { parent, view, position, id ->
                selectedArtist = searchHistory[position] // Update the selected artist
                selectedArtist?.let { artist ->
                    searchArtist(artist)
                    alertDialog.dismiss()

                    val pepeGif = findViewById<GifImageView>(R.id.pepeGif)
                    pepeGif.visibility = View.GONE
                }
            }

            historyListView.setOnItemLongClickListener { parent, view, position, id ->
                val selectedArtist = searchHistory[position]
                val alertDialogBuilder = AlertDialog.Builder(this)
                alertDialogBuilder.setTitle("Confirmation")
                alertDialogBuilder.setMessage("Do you really want to delete \"$selectedArtist\"?")
                alertDialogBuilder.setPositiveButton("Yes") { dialog, which ->
                    searchHistoryDatabaseHelper.deleteSearchQuery(selectedArtist)
                    historyAdapter.remove(selectedArtist)
                    historyAdapter.notifyDataSetChanged()
                }
                alertDialogBuilder.setNegativeButton("No", null)
                alertDialogBuilder.show()
                true
            }

            fetchReleasesButton.setOnClickListener {
                fetchAndDisplayReleases()
            }

            closeButton.setOnClickListener {
                alertDialog.dismiss()
            }
        }


        searchHistoryDatabaseHelper = SearchHistoryDatabaseHelper(this)

        searchButton = findViewById(R.id.searchButton)
        displayTracks = findViewById(R.id.displayTracks)
        artistEditText = findViewById(R.id.artistEditText)
        trackTitleTextView = findViewById(R.id.trackTitleTextView)
        albumCoverImageView = findViewById(R.id.albumCoverImageView)
        releaseDateTextView = findViewById(R.id.releaseDateTextView)
        albumCoverLayout = findViewById(R.id.albumCoverLayout)


        albumCoverLayout.visibility = View.GONE
        trackTitleTextView.visibility = View.GONE
        releaseDateTextView.visibility = View.GONE
        fullscreenDialog = Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)

        albumCoverImageView.setBackgroundResource(R.drawable.round_album_cover)


        val dropdownMenu = findViewById<Spinner>(R.id.dropdownMenu)
        val dropdownMenuItems = resources.getStringArray(R.array.dropdown_menu_items)

        dropdownMenu.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>, view: View?, position: Int, id: Long
            ) {
                val selectedItem = dropdownMenuItems[position]
                when (selectedItem) {
                    "About" -> {
                        val intent = Intent(this@MainActivity, AboutActivity::class.java)
                        startActivity(intent)
                        // Reset the spinner selection
                        dropdownMenu.setSelection(0)
                    }

                    "Settings" -> {
                        val intent = Intent(this@MainActivity, SettingsActivity::class.java)
                        startActivity(intent)
                        // Reset the spinner selection
                        dropdownMenu.setSelection(0)
                    }
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>) {
                // Handle nothing selected if needed
            }
        }



        searchButton.setOnClickListener {
            val artistName = artistEditText.text.toString().trim()
            if (artistName.isEmpty()) {
                Toast.makeText(this, "Please enter an artist name", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            Toast.makeText(this, "Searching for data...", Toast.LENGTH_SHORT).show()
            hideKeyboard()

            val pepeGif = findViewById<GifImageView>(R.id.pepeGif)
            // Hide the PepeGif based on the toggle state
            if (pepeGifEnabled) {
                pepeGif.visibility = View.GONE
            }
            searchSimilarArtists(artistName)
        }

        discographyButton.setOnClickListener {
            val artistName = artistEditText.text.toString().trim()

            val selected = selectedArtist // Assign the value of selectedArtist to a local variable

            if (artistName.isNotEmpty()) {
                showArtistDiscography(artistName)
            } else if (selected != null) {
                showArtistDiscography(selected) // Use the selected artist
            } else {
                Toast.makeText(
                    this,
                    "Please enter an artist name or select from search history",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        albumCoverImageView.setOnClickListener {
            val drawable = albumCoverImageView.drawable
            if (drawable != null) {
                showFullscreenImage(drawable)
            }
        }
    }

    private fun fetchAndDisplayReleases() {
        // Fetch the artists from the database (you will need to implement this part)
        val artists = fetchArtistsFromDatabase()

        // Loop through the artists and fetch their latest releases
        for (artist in artists) {
            fetchLatestRelease(artist)
        }

        // Start the ReleasesActivity
        val intent = Intent(this, ReleasesActivity::class.java)
        startActivity(intent)
    }

    private fun fetchArtistsFromDatabase(): List<String> {
        // Use your existing database helper class to fetch the artists from the database
        val dbHelper = SearchHistoryDatabaseHelper(this)
        return dbHelper.getLatestSearchQueries(limit = 10) // Adjust the limit as needed
    }

    private fun fetchLatestRelease(artistName: String) {
        val apiKey = APIKeys.DEEZER_API_KEY
        val client = OkHttpClient()
        val request = Request.Builder()
            .url("https://api.deezer.com/search/artist?q=$artistName")
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
                    val artistArray = jsonResponse.getJSONArray("data")

                    if (artistArray.length() > 0) {
                        val artist = artistArray.getJSONObject(0)
                        val artistId = artist.getString("id")
                        val artistImageUrl = artist.getString("picture_big")
                        getLatestRelease(artistId, artistImageUrl, artistName)
                    } else {
                        runOnUiThread {
                            // Handle case when no artist is found
                        }
                    }
                } else {
                    println("Error: ${response.code} ${response.message}")
                }
            }
        })
    }

    private fun showArtistDiscography(artistName: String) {
        val apiKey = APIKeys.DEEZER_API_KEY
        val client = OkHttpClient()
        val request = Request.Builder()
            .url("https://api.deezer.com/search/artist?q=$artistName")
            .addHeader("Authorization", "Bearer $apiKey")
            .build()

        progressDialog.show()

        this.artistName = artistName

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

                        // Start a new activity to display the artist discography
                        val intent =
                            Intent(this@MainActivity, ArtistDiscographyActivity::class.java)
                        intent.putExtra("artistId", artistId)
                        startActivity(intent)
                    } else {
                        runOnUiThread {
                            Toast.makeText(
                                this@MainActivity,
                                "No artist found",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                } else {
                    runOnUiThread {
                        Toast.makeText(
                            this@MainActivity,
                            "Error: ${response.code} ${response.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        })
    }

    private fun showFeedbackDialog() {
        val sharedPreferences = getSharedPreferences("AppPreferences", Context.MODE_PRIVATE)
        val showDialog = sharedPreferences.getBoolean("ShowFeedbackDialog", true)

        if (showDialog) {
            val dialog = Dialog(this)
            dialog.setContentView(R.layout.dialog_feedback)

            val yesButton = dialog.findViewById<Button>(R.id.yesButton)
            val noThanksButton = dialog.findViewById<Button>(R.id.noThanksButton)

            yesButton.setOnClickListener {
                val intent = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://github.com/n000tavailable/tunedetective/issues/5")
                )
                startActivity(intent)
                dialog.dismiss()
            }

            noThanksButton.setOnClickListener {
                dialog.dismiss()

                // Save the preference to not show the dialog again
                sharedPreferences.edit().apply {
                    putBoolean("ShowFeedbackDialog", false)
                    apply()
                }
            }

            dialog.show()
        }
    }

    private fun showAlbumDetails(albumTitle: String, artistName: String) {
        val apiKey = APIKeys.DEEZER_API_KEY
        val client = OkHttpClient()
        val encodedAlbumTitle = Uri.encode(albumTitle) // Encode the album title if needed

        val request = Request.Builder()
            .url("https://api.deezer.com/search/album?q=$encodedAlbumTitle")
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
                        var foundAlbum: JSONObject? = null

                        // Iterate over the albums and find the one with a matching title and artist
                        for (i in 0 until albumArray.length()) {
                            val album = albumArray.getJSONObject(i)
                            val currentAlbumTitle = album.getString("title")
                            val currentArtistName = album.getJSONObject("artist").getString("name")

                            if (currentAlbumTitle.equals(albumTitle, ignoreCase = true) &&
                                currentArtistName.equals(artistName, ignoreCase = true)
                            ) {
                                foundAlbum = album
                                break
                            }
                        }

                        if (foundAlbum != null) {
                            val albumId = foundAlbum.getString("id")
                            val albumTitle = foundAlbum.getString("title")
                            val albumCoverUrl = foundAlbum.getString("cover_medium")

                            runOnUiThread {
                                // Create a dialog to display the album details
                                val dialog = Dialog(this@MainActivity)
                                dialog.setContentView(R.layout.album_details_dialog)

                                // Set the album details in the dialog views
                                val albumTitleTextView =
                                    dialog.findViewById<TextView>(R.id.albumTitleTextView)
                                val albumCoverImageView =
                                    dialog.findViewById<ImageView>(R.id.albumCoverImageView)
                                val tracklistButton =
                                    dialog.findViewById<Button>(R.id.tracklistButton)

                                albumTitleTextView.text = albumTitle

                                // Load the album cover image using Glide
                                Glide.with(this@MainActivity)
                                    .load(albumCoverUrl)
                                    .into(albumCoverImageView)

                                // Set click listener for the tracklist button
                                tracklistButton.setOnClickListener {
                                    fetchAndShowTracklist(albumId)
                                }

                                dialog.show()
                            }
                        } else {
                            runOnUiThread {
                                Toast.makeText(
                                    this@MainActivity,
                                    "Album details not found",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    } else {
                        runOnUiThread {
                            Toast.makeText(
                                this@MainActivity,
                                "Album details not found",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                } else {
                    runOnUiThread {
                        Toast.makeText(
                            this@MainActivity,
                            "Error: ${response.code} ${response.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        })
    }

    private fun fetchAndShowTracklist(albumId: String) {
        val apiKey = APIKeys.DEEZER_API_KEY
        val client = OkHttpClient()
        val request = Request.Builder()
            .url("https://api.deezer.com/album/$albumId/tracks")
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
                    val tracklistArray = jsonResponse.getJSONArray("data")

                    if (tracklistArray.length() > 0) {
                        val tracklist = mutableListOf<String>()

                        for (i in 0 until tracklistArray.length()) {
                            val track = tracklistArray.getJSONObject(i)
                            val trackTitle = track.getString("title")
                            tracklist.add(trackTitle)
                        }

                        runOnUiThread {
                            // Create a dialog to display the tracklist
                            val dialogView =
                                layoutInflater.inflate(R.layout.dialog_tracklist2, null)
                            val tracklistListView =
                                dialogView.findViewById<ListView>(R.id.trackListRecyclerView2)

                            val tracklistAdapter = object : ArrayAdapter<String>(
                                this@MainActivity,
                                android.R.layout.simple_list_item_1,
                                tracklist
                            ) {
                                override fun getView(
                                    position: Int,
                                    convertView: View?,
                                    parent: ViewGroup
                                ): View {
                                    val view = super.getView(position, convertView, parent)
                                    val textView = view.findViewById<TextView>(android.R.id.text1)
                                    textView.setTextColor(
                                        ContextCompat.getColor(
                                            this@MainActivity,
                                            R.color.white
                                        )
                                    )
                                    textView.gravity = Gravity.CENTER // Set text gravity to center
                                    return view
                                }
                            }

                            tracklistListView.adapter = tracklistAdapter

                            val tracklistDialog = AlertDialog.Builder(this@MainActivity)
                                .setTitle("Tracklist")
                                .setPositiveButton("OK", null)
                                .setView(dialogView)
                                .create()

                            tracklistDialog.show()
                        }
                    } else {
                        runOnUiThread {
                            Toast.makeText(
                                this@MainActivity,
                                "Tracklist not found",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                } else {
                    runOnUiThread {
                        Toast.makeText(
                            this@MainActivity,
                            "Error: ${response.code} ${response.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        })
    }

    private fun updateWelcomeMessageVisibility() {
        welcomeMessageTextView.visibility = if (welcomeMessageVisible) View.VISIBLE else View.GONE
    }

    private fun updatePepeGifVisibility() {
        val pepeGif = findViewById<GifImageView>(R.id.pepeGif)

        if (pepeGifEnabled) {
            pepeGif.visibility = View.VISIBLE
        } else {
            pepeGif.visibility = View.GONE
        }
    }

    private fun searchArtistById(artistId: String) {
        val apiKey = APIKeys.DEEZER_API_KEY
        val client = OkHttpClient()
        val request = Request.Builder().url("https://api.deezer.com/artist/$artistId")
            .addHeader("Authorization", "Bearer $apiKey").build()

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
                    val artistName = jsonResponse.getString("name")
                    val artistImageUrl = jsonResponse.getString("picture_big")
                    val artist = Pair(artistName, artistImageUrl)

                    runOnUiThread {
                        showArtistSelectionDialog(listOf(artist))
                    }
                } else {
                    runOnUiThread {
                        Toast.makeText(
                            this@MainActivity,
                            "Error: ${response.code} ${response.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        })
    }

    private fun searchSimilarArtists(artistName: String) {
        val apiKey = APIKeys.DEEZER_API_KEY
        val client = OkHttpClient()

        // Check if artistName is a valid ID
        if (artistName.matches(Regex("\\d+"))) {
            searchArtistById(artistName)
            return
        }

        val request = Request.Builder().url("https://api.deezer.com/search/artist?q=$artistName")
            .addHeader("Authorization", "Bearer $apiKey").build()

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
                        val artists = mutableListOf<Pair<String, String>>()

                        for (i in 0 until artistArray.length()) {
                            val artist = artistArray.getJSONObject(i)
                            val artistName = artist.getString("name")
                            val artistImageUrl = artist.getString("picture_big")
                            artists.add(
                                Pair(
                                    artistName, artistImageUrl
                                )
                            ) // Add the artist name and image URL as a pair
                        }

                        val finalArtists =
                            artists.toList() // Create a final copy of the artists list

                        runOnUiThread {
                            showArtistSelectionDialog(finalArtists) // Use the finalArtists variable here
                        }
                    } else {
                        runOnUiThread {
                            Toast.makeText(
                                this@MainActivity, "No artists found", Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                } else {
                    runOnUiThread {
                        Toast.makeText(
                            this@MainActivity,
                            "Error: ${response.code} ${response.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        })
    }

    private fun showArtistSelectionDialog(artists: List<Pair<String, String>>) {
        val dialog = Dialog(this)
        dialog.setContentView(R.layout.dialog_artist_selection)

        val artistListView = dialog.findViewById<ListView>(R.id.artistListView)
        val closeButton = dialog.findViewById<Button>(R.id.closeButton)

        val artistAdapter = ArtistAdapter(this, artists)
        artistListView.adapter = artistAdapter

        artistListView.setOnItemClickListener { parent, view, position, id ->
            val selectedArtist = artists[position]
            saveSelectedArtist(selectedArtist.first) // Save the selected artist to the database
            searchArtist(selectedArtist.first)
            dialog.dismiss()
        }

        closeButton.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun saveSelectedArtist(artistName: String) {
        if (!searchHistoryDatabaseHelper.isSearchQueryExists(artistName)) {
            searchHistoryDatabaseHelper.insertSearchQuery(artistName)
        }
    }

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
                        getLatestRelease(artistId, artistImageUrl, artistName)
                    } else {
                        runOnUiThread {
                            trackTitleTextView.text = "No artist found"
                            albumCoverImageView.setImageResource(R.drawable.round_music_note_24)
                            releaseDateTextView.text = ""
                        }
                    }
                } else {
                    runOnUiThread {
                        Toast.makeText(
                            this@MainActivity,
                            "Error: ${response.code} ${response.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        })
    }

    private fun getLatestRelease(artistId: String, artistImageUrl: String, artistName: String) {
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
                            getAlbumDetails(albumId, albumCoverUrl, latestReleaseDate, artistImageUrl, artistName)
                        } else {
                            runOnUiThread {
                                // Handle case when no releases are found for the artist
                            }
                        }
                    } else {
                        runOnUiThread {
                            // Handle case when no releases are found for the artist
                        }
                    }
                } else {
                    println("Error: ${response.code} ${response.message}")
                }
            }
        })
    }

    private fun getAlbumDetails(
        albumId: String, albumCoverUrl: String, releaseDate: String?, artistImageUrl: String, artistName: String
    ) {
        val currentDate = Calendar.getInstance().time
        val sdfInput = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val sdfOutput = SimpleDateFormat("dd. MMMM yyyy", Locale.getDefault())
        val formattedReleaseDate = sdfOutput.format(sdfInput.parse(releaseDate))

        val apiKey = APIKeys.DEEZER_API_KEY
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
                    val artist = jsonResponse.getJSONObject("artist")
                    val artistName = artist.getString("name")
                    val albumUrl = jsonResponse.getString("link")

                    runOnUiThread {
                        // Update the UI to display the fetched release for the artist
                        // You can use this opportunity to show the artist name, album title, release date, album cover, etc.
                        albumCoverLayout.visibility = View.VISIBLE
                        trackTitleTextView.visibility = View.VISIBLE
                        releaseDateTextView.visibility = View.VISIBLE

                        // Set the artist name

                        // Set the album title
                        trackTitleTextView.text = albumTitle

                        // Load the album cover image
                        loadAlbumCoverImage(albumCoverUrl)

                        // Create a SpannableString and apply formatting to the release date
                        val spannableString = SpannableString("Release Date: $formattedReleaseDate")
                        spannableString.setSpan(
                            StyleSpan(Typeface.BOLD),
                            0,
                            "Release Date: ".length,
                            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                        releaseDateTextView.text = spannableString
                    }
                } else {
                    println("Error: ${response.code} ${response.message}")
                }
            }
        })
    }

    private fun getTrackList(tracklistUrl: String) {
        val apiKey = APIKeys.DEEZER_API_KEY
        val client = OkHttpClient()
        val request =
            Request.Builder().url(tracklistUrl).addHeader("Authorization", "Bearer $apiKey").build()


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

    private fun loadAlbumCoverImage(url: String) {
        Glide.with(this).load(url).apply(RequestOptions().transform(RoundedCorners(50)))
            .into(albumCoverImageView)
    }

    private fun showFullscreenImage(drawable: Drawable) {
        val imageView = ImageView(this)
        imageView.setImageDrawable(drawable)

        fullscreenDialog.setContentView(imageView)
        fullscreenDialog.show()
    }

    private fun hideKeyboard() {
        val inputMethodManager = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        val currentFocusView = currentFocus
        if (currentFocusView != null) {
            inputMethodManager.hideSoftInputFromWindow(currentFocusView.windowToken, 0)
        }
    }
}

class Track(val title: String, val previewUrl: String)


class TrackListAdapter(private val trackList: List<Track>) :
    RecyclerView.Adapter<TrackListAdapter.TrackViewHolder>() {

    private var mediaPlayer: MediaPlayer? = null
    private var currentlyPlayingPosition: Int = -1
    private var totalTracks: Int = 0

    init {
        totalTracks = trackList.size
    }

    fun stopPlayback() {
        mediaPlayer?.release()
        mediaPlayer = null
        currentlyPlayingPosition = -1
    }

    inner class TrackViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val titleTextView: TextView = itemView.findViewById(R.id.titleTextView)
        val totalTracksTextView: TextView = itemView.findViewById(R.id.totalTracksTextView)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TrackViewHolder {
        val itemView =
            LayoutInflater.from(parent.context).inflate(R.layout.item_track, parent, false)
        return TrackViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: TrackViewHolder, position: Int) {
        val track = trackList[position]
        val trackNumber = position + 1

        holder.titleTextView.text = track.title

        val trackTitleWithNumber = "<font color='#797979'>$trackNumber.</font> ${track.title}"
        holder.titleTextView.text =
            HtmlCompat.fromHtml(trackTitleWithNumber, HtmlCompat.FROM_HTML_MODE_LEGACY)

        // Set total tracks indicator
        holder.totalTracksTextView.text = "Total Tracks: $totalTracks"
        holder.totalTracksTextView.visibility = if (position == 0) View.VISIBLE else View.GONE

        holder.itemView.setOnClickListener {
            val currentPosition = holder.adapterPosition

            if (currentlyPlayingPosition == currentPosition) {
                mediaPlayer?.pause()
                mediaPlayer?.release()
                mediaPlayer = null
                currentlyPlayingPosition = -1
            } else {
                mediaPlayer?.release()
                mediaPlayer = MediaPlayer().apply {
                    setDataSource(track.previewUrl)
                    prepareAsync()
                    setOnPreparedListener {
                        start()
                        currentlyPlayingPosition = currentPosition
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

class SearchHistoryDatabaseHelper(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "search_history.db"
        private const val DATABASE_VERSION = 1
        private const val TABLE_NAME = "search_history"
        private const val COLUMN_ID = "id"
        private const val COLUMN_QUERY = "query"
    }

    override fun onCreate(db: SQLiteDatabase?) {
        val createTableQuery =
            "CREATE TABLE $TABLE_NAME ($COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT, $COLUMN_QUERY TEXT)"
        db?.execSQL(createTableQuery)
    }

    override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {
        val dropTableQuery = "DROP TABLE IF EXISTS $TABLE_NAME"
        db?.execSQL(dropTableQuery)
        onCreate(db)
    }

    fun insertSearchQuery(query: String) {
        val formattedQuery = query.toLowerCase(Locale.getDefault())
        val db = writableDatabase
        val values = ContentValues()
        values.put(COLUMN_QUERY, formattedQuery)
        db.insert(TABLE_NAME, null, values)
        db.close()
    }

    fun isSearchQueryExists(query: String): Boolean {
        val formattedQuery = query.toLowerCase(Locale.getDefault())
        val db = readableDatabase
        val selection = "$COLUMN_QUERY = ?"
        val selectionArgs = arrayOf(formattedQuery)
        val cursor = db.query(TABLE_NAME, null, selection, selectionArgs, null, null, null)
        val count = cursor.count
        cursor.close()
        db.close()
        return count > 0
    }

    @SuppressLint("Range")
    fun getLatestSearchQueries(limit: Int): List<String> {
        val db = readableDatabase
        val columns = arrayOf(COLUMN_QUERY)
        val orderBy = "$COLUMN_ID DESC"
        val limitString = limit.toString()
        val cursor = db.query(TABLE_NAME, columns, null, null, null, null, orderBy, limitString)
        val queries = mutableListOf<String>()

        if (cursor.moveToFirst()) {
            do {
                val query = cursor.getString(cursor.getColumnIndex(COLUMN_QUERY))
                queries.add(query)
            } while (cursor.moveToNext())
        }

        cursor.close()
        db.close()
        return queries
    }

    fun deleteSearchQuery(query: String) {
        val formattedQuery = query.toLowerCase(Locale.getDefault())
        val db = writableDatabase
        db.delete(TABLE_NAME, "$COLUMN_QUERY=?", arrayOf(formattedQuery))
        db.close()
    }

}

class ArtistAdapter(context: Context, artists: List<Pair<String, String>>) :
    ArrayAdapter<Pair<String, String>>(context, R.layout.item_artist, artists) {

    private val inflater: LayoutInflater = LayoutInflater.from(context)

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = inflater.inflate(R.layout.item_artist, parent, false)

        val artistNameTextView: TextView = view.findViewById(R.id.artistNameTextView)
        val artistImageView: ImageView = view.findViewById(R.id.artistImageView)

        val artist = getItem(position)
        artistNameTextView.text = artist?.first

        Glide.with(context).load(artist?.second)
            .apply(RequestOptions().transform(RoundedCorners(100))).into(artistImageView)

        return view
    }
}

class ArtistDiscographyActivity : AppCompatActivity() {
    private lateinit var artistId: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_artist_discography)

        // Retrieve the artist ID from the intent
        artistId = intent.getStringExtra("artistId") ?: ""

        // Set the title "Discography"
        title = "Discography"


        // Call a function to retrieve the full artist discography
        getFullArtistDiscography(artistId)
    }

    private fun getFullArtistDiscography(artistId: String) {
        val apiKey = APIKeys.DEEZER_API_KEY
        val client = OkHttpClient()

        val request = Request.Builder()
            .url("https://api.deezer.com/artist/$artistId/albums?limit=1000&type=album,single,ep")
            .addHeader("Authorization", "Bearer $apiKey")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                e.printStackTrace()
                runOnUiThread {
                    Toast.makeText(
                        this@ArtistDiscographyActivity,
                        "Failed to retrieve artist discography",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val responseData = response.body?.string()

                if (response.isSuccessful && responseData != null) {
                    val jsonResponse = JSONObject(responseData)
                    val albumArray = jsonResponse.getJSONArray("data")

                    val albums = ArrayList<Album>()

                    for (i in 0 until albumArray.length()) {
                        val albumData = albumArray.getJSONObject(i)
                        val albumId = albumData.getString("id")
                        val albumTitle = albumData.getString("title")
                        val albumCoverUrl = albumData.getString("cover_big")
                        val albumReleaseDate = albumData.getString("release_date")

                        val album = Album(albumId, albumTitle, albumCoverUrl, albumReleaseDate)
                        albums.add(album)
                    }

                    // Sort albums by release date in descending order (newest on top)
                    albums.sortByDescending { it.releaseDate }

                    runOnUiThread {
                        val recyclerView = findViewById<RecyclerView>(R.id.recyclerView)
                        val adapter = AlbumAdapter(albums)
                        recyclerView.adapter = adapter
                        recyclerView.layoutManager =
                            LinearLayoutManager(this@ArtistDiscographyActivity)
                    }
                } else {
                    runOnUiThread {
                        Toast.makeText(
                            this@ArtistDiscographyActivity,
                            "Failed to retrieve artist discography",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        })
    }

    data class Album(
        val albumId: String,
        val title: String,
        val coverUrl: String,
        val releaseDate: String
    )

    class AlbumAdapter(private val albums: List<Album>) :
        RecyclerView.Adapter<AlbumAdapter.ViewHolder>() {

        class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView), View.OnClickListener {
            val albumCoverImageView: ImageView = itemView.findViewById(R.id.albumCoverImageView)
            val albumTitleTextView: TextView = itemView.findViewById(R.id.albumTitleTextView)
            val albumReleaseDateTextView: TextView =
                itemView.findViewById(R.id.albumReleaseDateTextView)

            lateinit var album: Album

            init {
                itemView.setOnClickListener(this)
            }

            fun bind(album: Album) {
                this.album = album

                Glide.with(itemView)
                    .load(album.coverUrl)
                    .transition(DrawableTransitionOptions.withCrossFade())
                    .transform(RoundedCorners(50))
                    .into(albumCoverImageView)

                albumTitleTextView.text = album.title
                albumReleaseDateTextView.text = album.releaseDate
            }

            override fun onClick(view: View) {
                val context = itemView.context

                // Start a new activity to display the tracklist
                val intent = Intent(context, TracklistActivity::class.java)
                intent.putExtra("albumId", album.albumId)
                context.startActivity(intent)
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_album, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val album = albums[position]
            holder.bind(album)
        }

        override fun getItemCount(): Int {
            return albums.size
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int, payloads: MutableList<Any>) {
            super.onBindViewHolder(holder, position, payloads)

            // Calculate the margin for each item to create spacing between them
            val context = holder.itemView.context
            val margin = context.resources.getDimensionPixelSize(R.dimen.album_item_margin)
            val params = holder.itemView.layoutParams as RecyclerView.LayoutParams
            params.marginEnd = margin

            // Apply the margin to the item's layout params
            holder.itemView.layoutParams = params
        }
    }
}

class TracklistActivity : AppCompatActivity() {
    private lateinit var albumId: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.dialog_tracklist2)

        // Retrieve the album ID from the intent
        albumId = intent.getStringExtra("albumId") ?: ""

        // Call a function to retrieve the tracklist for the album
        getAlbumTracklist(albumId)
    }

    override fun onBackPressed() {
        super.onBackPressed()
        finish()
    }

    private fun getAlbumTracklist(albumId: String) {
        val apiKey = APIKeys.DEEZER_API_KEY
        val client = OkHttpClient()

        val request = Request.Builder()
            .url("https://api.deezer.com/album/$albumId/tracks")
            .addHeader("Authorization", "Bearer $apiKey")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                e.printStackTrace()
                runOnUiThread {
                    Toast.makeText(
                        this@TracklistActivity,
                        "Failed to retrieve album tracklist",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val responseData = response.body?.string()

                if (response.isSuccessful && responseData != null) {
                    val jsonResponse = JSONObject(responseData)
                    val trackArray = jsonResponse.getJSONArray("data")

                    val tracks = ArrayList<Track>()

                    for (i in 0 until trackArray.length()) {
                        val trackData = trackArray.getJSONObject(i)
                        val trackTitle = trackData.getString("title")
                        val trackDuration = trackData.getInt("duration")

                        val track = Track(trackTitle, trackDuration)
                        tracks.add(track)
                    }

                    runOnUiThread {
                        // Find the RecyclerView in the dialog layout
                        val trackRecyclerView = findViewById<RecyclerView>(R.id.trackListRecyclerView2)
                        // Find the TextView for the track count
                        val trackCountTextView = findViewById<TextView>(R.id.trackCountTextView)

                        // Create an instance of the adapter and pass in the list of tracks
                        val adapter = TrackAdapter(tracks)

                        // Set the adapter on the RecyclerView
                        trackRecyclerView.adapter = adapter

                        // Set the layout manager on the RecyclerView
                        trackRecyclerView.layoutManager = LinearLayoutManager(this@TracklistActivity)

                        // Set the track count
                        trackCountTextView.text = getString(R.string.track_count_format, tracks.size)

                        // Find the close button and set a click listener
                        val closeButton = findViewById<Button>(R.id.closeButton)
                        closeButton.setOnClickListener {
                            finish()
                        }
                    }
                } else {
                    runOnUiThread {
                        Toast.makeText(
                            this@TracklistActivity,
                            "Failed to retrieve album tracklist",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        })
    }

    data class Track(
        val title: String,
        val duration: Int
    )

    class TrackAdapter(private val tracks: List<Track>) :
        RecyclerView.Adapter<TrackAdapter.ViewHolder>() {

        class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val trackTitleTextView: TextView = itemView.findViewById(R.id.trackTitleTextView)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.new_item_track, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val track = tracks[position]
            holder.trackTitleTextView.text = track.title
        }

        override fun getItemCount(): Int {
            return tracks.size
        }
    }
}

class ReleasesActivity : AppCompatActivity() {
    private lateinit var releaseContainer: LinearLayout


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_releases)

        releaseContainer = findViewById(R.id.releaseContainer)

        fetchAndDisplayReleases()
    }

    data class Artist(
        val artistId: String,
        val artistName: String,
        val artistImageUrl: String
    )

    private fun fetchAndDisplayReleases() {
        val artists = fetchArtistsFromDatabase()

        for (artist in artists) {
            fetchLatestRelease(artist)
        }
    }

    private fun fetchArtistsFromDatabase(): List<String> {
        // Use your existing database helper class to fetch the artists from the database
        val dbHelper = SearchHistoryDatabaseHelper(this)
        return dbHelper.getLatestSearchQueries(limit = 10) // Adjust the limit as needed
    }

    private fun fetchLatestRelease(artistName: String) {
        val apiKey = APIKeys.DEEZER_API_KEY
        val client = OkHttpClient()
        val request = Request.Builder()
            .url("https://api.deezer.com/search/artist?q=$artistName")
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
                    val artistArray = jsonResponse.getJSONArray("data")

                    if (artistArray.length() > 0) {
                        val artist = artistArray.getJSONObject(0)
                        val artistId = artist.getString("id")
                        val artistImageUrl = artist.getString("picture_big")
                        fetchArtistLatestAlbum(artistId, artistImageUrl, artistName)
                    } else {
                        runOnUiThread {
                            // Handle case when no artist is found
                        }
                    }
                } else {
                    println("Error: ${response.code} ${response.message}")
                }
            }
        })
    }

    private fun fetchArtistLatestAlbum(artistId: String, artistImageUrl: String, artistName: String) {
        val apiKey = APIKeys.DEEZER_API_KEY
        val client = OkHttpClient()
        val request = Request.Builder()
            .url("https://api.deezer.com/artist/$artistId/albums?limit=1000&type=album,single,ep")
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
                        val latestAlbum = findLatestAlbum(albumArray)
                        if (latestAlbum != null) {
                            val albumId = latestAlbum.getString("id")
                            val albumTitle = latestAlbum.getString("title")
                            val albumCoverUrl = latestAlbum.getString("cover_big")
                            val releaseDate = latestAlbum.getString("release_date")

                            val albumItem = ArtistDiscographyActivity.Album(
                                albumId,
                                albumTitle,
                                albumCoverUrl,
                                releaseDate
                            )

                            runOnUiThread {
                                addAlbumToView(albumItem, artistName, artistImageUrl)
                            }
                        } else {
                            runOnUiThread {
                                // Handle case when no album is found
                            }
                        }
                    } else {
                        runOnUiThread {
                            // Handle case when no album is found
                        }
                    }
                } else {
                    println("Error: ${response.code} ${response.message}")
                }
            }
        })
    }

    private fun findLatestAlbum(albumArray: JSONArray): JSONObject? {
        var latestAlbum: JSONObject? = null
        var latestReleaseDate: String? = null

        for (i in 0 until albumArray.length()) {
            val album = albumArray.getJSONObject(i)
            val releaseDate = album.getString("release_date")

            if (latestReleaseDate == null || releaseDate > latestReleaseDate) {
                latestAlbum = album
                latestReleaseDate = releaseDate
            }
        }

        return latestAlbum
    }

    private fun addAlbumToView(album: ArtistDiscographyActivity.Album, artistName: String, artistImageUrl: String) {
        val releaseItemView = LayoutInflater.from(this)
            .inflate(R.layout.item_release, releaseContainer, false)

        val artistTextView = releaseItemView.findViewById<TextView>(R.id.artistTextView)
        val releaseTitleTextView = releaseItemView.findViewById<TextView>(R.id.releaseTitleTextView)
        val releaseDateTextView = releaseItemView.findViewById<TextView>(R.id.releaseDateTextView)
        val releaseCoverImageView = releaseItemView.findViewById<ImageView>(R.id.releaseCoverImageView)

        artistTextView.text = artistName.toUpperCase() // Convert to uppercase
        releaseTitleTextView.text = album.title
        releaseDateTextView.text = album.releaseDate

        val requestOptions = RequestOptions()
            .transform(RoundedCorners(50))

        Glide.with(this)
            .load(album.coverUrl)
            .apply(requestOptions)
            .into(releaseCoverImageView)

        releaseContainer.addView(releaseItemView)
    }

    private fun fetchArtistReleases(artistId: String, artistImageUrl: String, artistName: String) {
        val apiKey = APIKeys.DEEZER_API_KEY
        val client = OkHttpClient()
        val request = Request.Builder()
            .url("https://api.deezer.com/artist/$artistId/albums?limit=1000&type=album,single,ep")
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
                        val latestRelease = findLatestRelease(albumArray)
                        if (latestRelease != null) {
                            val albumId = latestRelease.getString("id")
                            val albumTitle = latestRelease.getString("title")
                            val albumCoverUrl = latestRelease.getString("cover_big")
                            val releaseDate = latestRelease.getString("release_date")

                            val albumItem = ArtistDiscographyActivity.Album(
                                albumId,
                                albumTitle,
                                albumCoverUrl,
                                releaseDate
                            )

                            runOnUiThread {
                                addAlbumToView(albumItem, artistName, artistImageUrl)
                            }
                        } else {
                            runOnUiThread {
                                // Handle case when no release is found
                            }
                        }
                    } else {
                        runOnUiThread {
                            // Handle case when no release is found
                        }
                    }
                } else {
                    println("Error: ${response.code} ${response.message}")
                }
            }
        })
    }

    private fun fetchArtistDiscography(artistId: String, artistImageUrl: String, artistName: String) {
        val apiKey = APIKeys.DEEZER_API_KEY
        val client = OkHttpClient()
        val request = Request.Builder()
            .url("https://api.deezer.com/artist/$artistId/albums?limit=10")
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
                        val latestRelease = findLatestRelease(albumArray)
                        if (latestRelease != null) {
                            val albumId = latestRelease.getString("id")
                            val albumTitle = latestRelease.getString("title")
                            val albumCoverUrl = latestRelease.getString("cover_big")
                            val releaseDate = latestRelease.getString("release_date")

                            val albumItem = ArtistDiscographyActivity.Album(
                                albumId,
                                albumTitle,
                                albumCoverUrl,
                                releaseDate
                            )

                            runOnUiThread {
                                addAlbumToView(albumItem, artistName, artistImageUrl)
                            }
                        } else {
                            runOnUiThread {
                                // Handle case when no release is found
                            }
                        }
                    } else {
                        runOnUiThread {
                            // Handle case when no release is found
                        }
                    }
                } else {
                    println("Error: ${response.code} ${response.message}")
                }
            }
        })
    }

    private fun findLatestRelease(albumArray: JSONArray): JSONObject? {
        var latestRelease: JSONObject? = null
        var latestReleaseDate: String? = null

        for (i in 0 until albumArray.length()) {
            val release = albumArray.getJSONObject(i)
            val releaseType = release.getString("type")
            val releaseDate = release.getString("release_date")

            // Consider only albums, EPs, and singles
            if (releaseType == "album" || releaseType == "ep" || releaseType == "single") {
                if (latestReleaseDate == null || releaseDate > latestReleaseDate) {
                    latestRelease = release
                    latestReleaseDate = releaseDate
                }
            }
        }

        return latestRelease
    }



}