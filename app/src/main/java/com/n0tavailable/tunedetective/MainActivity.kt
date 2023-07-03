package com.n0tavailable.tunedetective

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.Dialog
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.text.Spannable
import android.text.SpannableString
import android.text.style.StyleSpan
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.AccelerateInterpolator
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.SearchView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.text.HtmlCompat
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkRequest
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CircleCrop
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bumptech.glide.request.RequestOptions
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.lang.Math.abs
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit


class MainActivity : AppCompatActivity() {
    private lateinit var displayTracks: Button
    private lateinit var discographyButton: Button
    private lateinit var searchBar: SearchView
    private lateinit var trackTitleTextView: TextView
    private lateinit var albumCoverImageView: ImageView
    private lateinit var releaseDateTextView: TextView
    private lateinit var fullscreenDialog: Dialog
    private lateinit var albumCoverLayout: LinearLayout
    private lateinit var progressDialog: AlertDialog
    private lateinit var searchHistoryDatabaseHelper: SearchHistoryDatabaseHelper
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var dialog: Dialog
    private var mediaPlayer: MediaPlayer? = null
    private var artistName: String? = null
    private var selectedArtist: String? = null
    private val artistMap = mutableMapOf<String, Pair<String, String>>()
    private var feedbackDialog: Dialog? = null


    override fun onDestroy() {
        super.onDestroy()
        stopPlayback()
        resetLayout()
        feedbackDialog?.dismiss()
        feedbackDialog = null
    }

    override fun onStop() {
        super.onStop()
        stopPlayback()
    }

    private fun stopPlayback() {
        mediaPlayer?.release()
        mediaPlayer = null
    }

    override fun onPause() {
        super.onPause()
    }

    // Handle the result of the permission request
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 101 && grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            // Permission granted, launch the work
            launchWork()
        }
    }

    override fun onResume() {
        super.onResume()
    }

    private fun resetLayout() {

        if (::dialog.isInitialized && dialog.isShowing) {
            dialog.dismiss()
            val adapter =
                (dialog.findViewById<RecyclerView>(R.id.trackListRecyclerView).adapter as? TrackListAdapter)
            adapter?.stopPlayback()
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enforceDarkMode()

        val dialogViewProgress = layoutInflater.inflate(R.layout.dialog_progress, null)
        val progressBar = dialogViewProgress.findViewById<ProgressBar>(R.id.progressBar)
        val messageTextView = dialogViewProgress.findViewById<TextView>(R.id.messageTextView)

        progressBar.isIndeterminate = true
        messageTextView.text = "Loading data..."

        val progressDialogBuilder = MaterialAlertDialogBuilder(this)
            .setView(dialogViewProgress)
            .setCancelable(false)

        progressDialog = progressDialogBuilder.create()

        setContentView(R.layout.activity_main)

        // Check if the notification permission is granted
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // Request the notification permission
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                101
            )
        } else {
            // Permission is already granted
            launchWork()
        }

        discographyButton = findViewById(R.id.discographyButton)
        sharedPreferences = getSharedPreferences("MyPrefs", Context.MODE_PRIVATE)


        showFeedbackDialog()

        val discographyButton = findViewById<Button>(R.id.discographyButton)

        // Initialize the SharedPreferences instance
        sharedPreferences = getSharedPreferences("MyPrefs", Context.MODE_PRIVATE)

        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_search_history, null)


        val historyListView = dialogView.findViewById<ListView>(R.id.historyListView)
        val maxHeight = resources.getDimensionPixelSize(R.dimen.max_listview_height)
        historyListView.layoutParams.height =
            Math.min(historyListView.layoutParams.height, maxHeight)



        searchHistoryDatabaseHelper = SearchHistoryDatabaseHelper(this)

        val searchHistoryButton = findViewById<ImageButton>(R.id.searchHistoryButton)


        searchHistoryButton.setOnClickListener {

            hideKeyboard()


            val searchHistory = searchHistoryDatabaseHelper.getLatestSearchQueries(20)
            val historyListView = dialogView.findViewById<ListView>(R.id.historyListView)
            val maxHeight = resources.getDimensionPixelSize(R.dimen.max_listview_height)
            historyListView.layoutParams.height = maxHeight
            val closeButton = dialogView.findViewById<Button>(R.id.closeButton)
            val fetchReleasesButton = dialogView.findViewById<Button>(R.id.fetchReleasesButton)

            val historyAdapter = ArtistImage(this, R.layout.dialog_search_history_item, searchHistory)
            historyListView.adapter = historyAdapter

            var initialX = 0f
            var initialY = 0f
            val swipeThreshold = resources.getDimensionPixelSize(R.dimen.swipe_threshold)
            val slideDuration = 200L
            val historyListViewTouchListener = object : View.OnTouchListener {
                override fun onTouch(v: View?, event: MotionEvent?): Boolean {
                    when (event?.action) {
                        MotionEvent.ACTION_DOWN -> {
                            initialX = event.x
                            initialY = event.y
                        }
                        MotionEvent.ACTION_UP -> {
                            val deltaX = event.x - initialX
                            val deltaY = event.y - initialY
                            if (abs(deltaX) > swipeThreshold && abs(deltaY) < swipeThreshold) {
                                val position = historyListView.pointToPosition(initialX.toInt(), initialY.toInt())
                                val selectedArtist = historyAdapter.getItem(position)
                                if (selectedArtist != null) {
                                    // Perform the slide animation
                                    val itemView = historyListView.getChildAt(position - historyListView.firstVisiblePosition)
                                    itemView?.animate()?.translationXBy(deltaX)?.setDuration(slideDuration)?.interpolator =
                                        AccelerateInterpolator()
                                    itemView?.animate()?.alpha(0f)?.setDuration(slideDuration)?.withEndAction {
                                        searchHistoryDatabaseHelper.deleteSearchQuery(selectedArtist)
                                        historyAdapter.remove(selectedArtist)
                                        historyAdapter.notifyDataSetChanged()
                                        itemView.translationX = 0f
                                        itemView.alpha = 1f
                                    }
                                }
                            }
                        }
                    }
                    return false
                }
            }
            historyListView.setOnTouchListener(historyListViewTouchListener)

            val alertDialogBuilder = MaterialAlertDialogBuilder(this)
            alertDialogBuilder.setTitle("SEARCH HISTORY")
            alertDialogBuilder.setView(dialogView)

            val alertDialog = alertDialogBuilder.create()
            (dialogView.parent as? ViewGroup)?.removeView(dialogView)

            alertDialog.show()

            historyListView.setOnItemClickListener { parent, view, position, id ->
                val selectedQuery = searchHistory[position]
                val artistId = selectedQuery.substringAfter(",")
                    .trim() // Extract and trim the artist ID from the query

                val selectedArtistId = artistId // Create a local variable for selected artist ID


                discographyButton.setOnClickListener {
                    if (artistId != null) {
                        showArtistDiscography(artistId) // Pass the selected artist ID
                    } else {
                        Toast.makeText(
                            this,
                            "Please enter an artist name or select from search history",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

                searchArtistById(selectedArtistId)
                alertDialog.dismiss()


                alertDialog.setOnDismissListener {
                }
            }

            historyListView.setOnItemLongClickListener { parent, view, position, id ->
                val selectedArtist = searchHistory[position]
                val alertDialogBuilder = MaterialAlertDialogBuilder(this)
                alertDialogBuilder.setTitle("Confirmation")
                alertDialogBuilder.setMessage("Do you really want to delete \"$selectedArtist\"?")

                alertDialogBuilder.setPositiveButton("Yes") { dialog, which ->
                    searchHistoryDatabaseHelper.deleteSearchQuery(selectedArtist)
                    historyAdapter.remove(selectedArtist)
                    historyAdapter.notifyDataSetChanged()
                }
                alertDialogBuilder.setNegativeButton("No", null)

                val alertDialog = alertDialogBuilder.create()
                alertDialog.setOnShowListener {
                    val messageTextView = alertDialog.findViewById<TextView>(android.R.id.message)
                    messageTextView?.setTextColor(Color.WHITE)
                }

                alertDialog.show()
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

        displayTracks = findViewById(R.id.displayTracks)
        searchBar = findViewById(R.id.searchBar)
        trackTitleTextView = findViewById(R.id.trackTitleTextView)
        albumCoverImageView = findViewById(R.id.albumCoverImageView)
        releaseDateTextView = findViewById(R.id.releaseDateTextView)
        albumCoverLayout = findViewById(R.id.albumCoverLayout)


        albumCoverLayout.visibility = View.GONE
        trackTitleTextView.visibility = View.GONE
        releaseDateTextView.visibility = View.GONE
        fullscreenDialog = Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)

        albumCoverImageView.setBackgroundResource(R.drawable.round_album_cover)


        val searchView = findViewById<SearchView>(R.id.searchBar)

        // Set the query hint and text color
        searchView.queryHint = "Enter artist name"

        // Expand the SearchView
        searchView.isIconified = false

        // Prevent the keyboard from opening automatically
        searchBar.clearFocus()
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)


        val bottomNavigation = findViewById<BottomNavigationView>(R.id.bottomNavigation)

        bottomNavigation.setOnNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.menu_home -> {
                    // Handle home button click
                    val intent = Intent(this@MainActivity, MainActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    startActivity(intent)
                    true
                }
                R.id.menu_releases -> {
                    // Handle releases button click
                    val intent = Intent(this@MainActivity, ReleasesActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    startActivity(intent)
                    finish()
                    true
                }
                R.id.menu_settings -> {
                    // Handle settings button click
                    val intent = Intent(this@MainActivity, SettingsActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    startActivity(intent)
                    finish()
                    true
                }
                R.id.menu_info -> {
                    // Handle info button click
                    val intent = Intent(this@MainActivity, AboutActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    startActivity(intent)
                    finish()
                    true
                }
                else -> false
            }
        }



        searchBar.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String): Boolean {
                val artistName = query.trim()
                if (artistName.isEmpty()) {
                    Toast.makeText(
                        this@MainActivity,
                        "Please enter an artist name",
                        Toast.LENGTH_SHORT
                    ).show()
                    return false
                }

                Toast.makeText(this@MainActivity, "Searching for data...", Toast.LENGTH_SHORT)
                    .show()
                hideKeyboard()
                searchSimilarArtists(artistName)

                // Clear focus to prevent the keyboard from reopening
                searchBar.clearFocus()

                // Return true only when the search is successful
                return true
            }

            override fun onQueryTextChange(newText: String): Boolean {
                // Handle any text changes if needed
                return false
            }
        })

        albumCoverImageView.setOnClickListener {
            val drawable = albumCoverImageView.drawable
            if (drawable != null) {
                showFullscreenImage(drawable)
            }
        }
    }

    private fun launchWork() {
        GlobalScope.launch {
            scheduleReleasesFetchWork()
        }
    }


    private fun scheduleReleasesFetchWork() {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(applicationContext)
        val repeatInterval = sharedPreferences.getInt(SettingsActivity.PREF_REPEAT_INTERVAL, SettingsActivity.DEFAULT_REPEAT_INTERVAL)

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED) // Require network connection
            .build()

        val periodicWorkRequest = PeriodicWorkRequestBuilder<FetchReleasesWorker>(
            repeatInterval.toLong(),
            TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.LINEAR,
                WorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS
            ) // Add backoff criteria for retrying work
            .build()

        val workManager = WorkManager.getInstance(applicationContext)
        val uniqueWorkName = "FetchReleasesWork"

        workManager.enqueueUniquePeriodicWork(
            uniqueWorkName,
            ExistingPeriodicWorkPolicy.REPLACE, // Update existing work with the new one
            periodicWorkRequest
        )
    }

    private fun enforceDarkMode() {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
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
        val dbHelper = SearchHistoryDatabaseHelper(applicationContext)
        val latestSearchQueries = dbHelper.getLatestSearchQueries(limit = 10)
        val artistIds = latestSearchQueries.map { query ->
            query.substringAfter(",").trim()
        }.distinct() // Use the distinct() function to get only unique artist IDs
        return artistIds
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

    private fun showArtistDiscography(artistId: String) {
        val apiKey = APIKeys.DEEZER_API_KEY
        val client = OkHttpClient()
        val request = Request.Builder()
            .url("https://api.deezer.com/artist/$artistId") // Use artist ID in the API URL
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
                    val artistId = jsonResponse.getString("id")
                    val artistName = jsonResponse.getString("name")
                    val artistProfilePicture = jsonResponse.getString("picture_big")

                    // Start a new activity to display the artist discography
                    val intent = Intent(applicationContext, ArtistDiscographyActivity::class.java)
                    intent.putExtra("artistId", artistId)
                    intent.putExtra("artistName", artistName)
                    intent.putExtra("artistProfilePicture", artistProfilePicture)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK // Add this line to set the flag
                    applicationContext.startActivity(intent)
                } else {
                    runOnUiThread {
                        Toast.makeText(
                            this@MainActivity,
                            "No artist found",
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

        if (showDialog && !isFinishing) {
            MaterialAlertDialogBuilder(this)
                .setTitle("Feedback")
                .setMessage("Would you like to provide feedback?")
                .setPositiveButton("Yes") { _, _ ->
                    val intent = Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("https://github.com/n000tavailable/tunedetective/issues/5")
                    )
                    startActivity(intent)
                }
                .setNegativeButton("No, Thanks") { _, _ ->
                    // Save the preference to not show the dialog again
                    sharedPreferences.edit().putBoolean("ShowFeedbackDialog", false).apply()
                }
                .setCancelable(false)
                .show()
        }
    }
    private fun searchArtistById(artistId: String) {
        val apiKey = APIKeys.DEEZER_API_KEY
        val client = OkHttpClient()
        val request = Request.Builder()
            .url("https://api.deezer.com/artist/$artistId")
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
                    val artistName = jsonResponse.getString("name")
                    val artistImageUrl = jsonResponse.getString("picture_big")
                    getLatestRelease(artistId, artistImageUrl, artistName)
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
                        val artists = mutableListOf<Pair<String, String>>()

                        for (i in 0 until artistArray.length()) {
                            val artist = artistArray.getJSONObject(i)
                            val artistName = artist.getString("name")
                            val artistImageUrl = artist.getString("picture_big")
                            val artistId = artist.getString("id")
                            artists.add(
                                Pair(
                                    artistName, artistImageUrl
                                )
                            ) // Add the artist name and image URL as a pair

                            // Store artist id with its name and image URL in a map
                            artistMap[artistId] = Pair(artistName, artistImageUrl)
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
        val builder = MaterialAlertDialogBuilder(this)
        val dialogView = layoutInflater.inflate(R.layout.dialog_artist_selection, null)

        val artistListView = dialogView.findViewById<ListView>(R.id.artistListView)

        val artistAdapter = ArtistAdapter(this, artists)
        artistListView.adapter = artistAdapter

        val dialog = builder
            .setTitle("Select an Artist")
            .setView(dialogView)
            .setNegativeButton("Close") { _, _ ->
                // Handle close button click
            }
            .create()

        artistListView.setOnItemClickListener { _, _, position, _ ->
            val selectedArtist = artists[position]
            val artistName = selectedArtist.first
            val artistId = artistMap.keys.find { key -> artistMap[key]?.first == artistName }

            discographyButton.setOnClickListener {
                if (artistId != null) {
                    showArtistDiscography(artistId) // Pass the selected artist ID
                } else {
                    Toast.makeText(
                        this,
                        "Please enter an artist name or select from search history",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            if (artistId != null) {
                saveSelectedArtist(artistId, artistName) // Save the selected artist name to the database
                searchArtistById(artistId)
            } else {
                Toast.makeText(this, "Error: Artist ID not found", Toast.LENGTH_SHORT).show()
            }

            dialog.dismiss()
        }

        dialog.show()
    }

    private fun saveSelectedArtist(artistId: String, artistName: String) {
        val query = "$artistName, $artistId"
        if (!searchHistoryDatabaseHelper.isSearchQueryExists(query)) {
            searchHistoryDatabaseHelper.insertSearchQuery(query)
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
                            getAlbumDetails(
                                albumId,
                                albumCoverUrl,
                                latestReleaseDate,
                                artistImageUrl,
                                artistName
                            )
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
        albumId: String,
        albumCoverUrl: String,
        releaseDate: String?,
        artistImageUrl: String,
        artistName: String
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
                    val tracklistUrl = jsonResponse.getString("tracklist")

                    runOnUiThread {
                        // Update the UI to display the fetched release for the artist
                        // You can use this opportunity to show the artist name, album title, release date, album cover, etc.
                        albumCoverLayout.visibility = View.VISIBLE
                        trackTitleTextView.visibility = View.VISIBLE
                        releaseDateTextView.visibility = View.VISIBLE

                        // Set the artist name

                        // Set the album title with truncation if necessary
                        val maxTitleLength = 25
                        val truncatedAlbumTitle = if (albumTitle.length <= maxTitleLength) {
                            albumTitle
                        } else {
                            albumTitle.substring(0, maxTitleLength) + "..."
                        }
                        trackTitleTextView.text = truncatedAlbumTitle

                        // Load the album cover image
                        loadAlbumCoverImage(albumCoverUrl, albumCoverImageView)

                        // Create a SpannableString and apply formatting to the release date
                        val spannableString = SpannableString("Release Date: $formattedReleaseDate")
                        spannableString.setSpan(
                            StyleSpan(Typeface.BOLD),
                            0,
                            "Release Date: ".length,
                            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                        releaseDateTextView.text = spannableString

                        displayTracks.setOnClickListener {

                            progressDialog.show()

                            getTrackList(tracklistUrl, albumCoverUrl, formattedReleaseDate)
                        }
                    }
                } else {
                    println("Error: ${response.code} ${response.message}")
                }
            }
        })
    }

    private fun getTrackList(tracklistUrl: String, albumCoverUrl: String, releaseDate: String?) {
        val apiKey = APIKeys.DEEZER_API_KEY
        val client = OkHttpClient()
        val request = Request.Builder()
            .url(tracklistUrl)
            .addHeader("Authorization", "Bearer $apiKey")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                e.printStackTrace()
                progressDialog.dismiss()
            }

            override fun onResponse(call: Call, response: Response) {
                val responseData = response.body?.string()

                if (response.isSuccessful && responseData != null) {

                    progressDialog.dismiss()

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
                        showTrackListDialog(trackList, albumCoverUrl, releaseDate)
                    }
                } else {
                    progressDialog.dismiss()
                    println("Error: ${response.code} ${response.message}")
                }
            }
        })
    }


    private fun showTrackListDialog(
        trackList: List<Track>,
        albumCoverUrl: String,
        releaseDate: String?
    ) {
        val builder = MaterialAlertDialogBuilder(this)
        val dialogView = layoutInflater.inflate(R.layout.dialog_tracklist, null)

        val trackListRecyclerView = dialogView.findViewById<RecyclerView>(R.id.trackListRecyclerView)
        val closeButton = dialogView.findViewById<Button>(R.id.closeButton)
        val albumCoverImageView = dialogView.findViewById<ImageView>(R.id.albumCoverImageView)
        val trackCounterTextView = dialogView.findViewById<TextView>(R.id.trackCounterTextView)
        val releaseDateTextView = dialogView.findViewById<TextView>(R.id.releaseDateTextView)

        // Load the album cover image
        loadAlbumCoverImage(albumCoverUrl, albumCoverImageView)

        val layoutManager = LinearLayoutManager(this)
        val adapter = TrackListAdapter(trackList)

        trackListRecyclerView.layoutManager = layoutManager
        trackListRecyclerView.adapter = adapter

        trackCounterTextView.text = "Total Tracks: ${adapter.itemCount}"
        releaseDateTextView.text = releaseDate ?: "Release Date: N/A"

        val dialog = builder
            .setView(dialogView)
            .create()

        closeButton.setOnClickListener {
            dialog.dismiss()
            adapter.stopPlayback()
        }

        dialog.setOnDismissListener {
            adapter.stopPlayback()
        }

        albumCoverImageView.setOnClickListener {
            showFullscreenImage(albumCoverImageView.drawable)
        }

        dialog.show()
    }

    private fun loadAlbumCoverImage(url: String, imageView: ImageView) {
        Glide.with(this)
            .load(url)
            .transform(RoundedCorners(50))
            .placeholder(R.drawable.round_music_note_24) // Placeholder image while loading
            .error(R.drawable.round_music_note_24) // Error image if loading fails
            .into(imageView)
    }

    private fun showFullscreenImage(drawable: Drawable) {
        val fullscreenDialog = Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
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

class ArtistImage(context: Context, resource: Int, objects: List<String>) :
    ArrayAdapter<String>(context, resource, objects) {

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        var view = convertView
        if (view == null) {
            view = LayoutInflater.from(context)
                .inflate(R.layout.dialog_search_history_item, parent, false)
        }

        val artistImageView = view?.findViewById<ImageView>(R.id.artistImageView)
        val artistNameTextView = view?.findViewById<TextView>(R.id.artistNameTextView)

        val item = getItem(position)
        val artistId = item?.substringAfter(",")?.trim() ?: ""
        val artistName = item?.substringBefore(",") ?: ""

        artistNameTextView?.text = artistName.uppercase()

        // Fetch and load the artist image using Glide or any other image loading library
        fetchAndLoadArtistImage(artistId, artistImageView)

        return view!!
    }

    private fun fetchAndLoadArtistImage(artistId: String, imageView: ImageView?) {
        // Use Deezer API to fetch artist image using the artistId
        // For example, you can construct the URL like this:
        val artistImageUrl = "https://api.deezer.com/artist/$artistId/image"

        // Apply circular transformation using Glide
        val requestOptions = RequestOptions.circleCropTransform()

        // Load the image into the ImageView using Glide or any other image loading library
        if (imageView != null) {
            // Load the image into the ImageView using Glide or any other image loading library
            Glide.with(context)
                .load(artistImageUrl)
                .apply(requestOptions)
                .placeholder(R.drawable.default_profile_image) // Placeholder image while loading
                .error(R.drawable.default_profile_image) // Error image if loading fails
                .into(imageView)
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
        const val DATABASE_NAME = "search_history.db"
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

    fun exportDatabase(context: Context, fileName: String) {
        val dbPath = context.getDatabasePath(DATABASE_NAME).absolutePath
        val exportDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val exportFile = File(exportDir, fileName)

        try {
            val fileChannelSource = FileInputStream(dbPath).channel
            val fileChannelDestination = FileOutputStream(exportFile).channel
            fileChannelDestination.transferFrom(fileChannelSource, 0, fileChannelSource.size())
            fileChannelSource.close()
            fileChannelDestination.close()
            Toast.makeText(context, "Database exported successfully", Toast.LENGTH_SHORT).show()
        } catch (e: IOException) {
            e.printStackTrace()
            Toast.makeText(context, "Failed to export database", Toast.LENGTH_SHORT).show()
        }
    }

    fun importDatabase(context: Context, uri: Uri): Boolean {
        val dbPath = context.getDatabasePath(DATABASE_NAME).absolutePath

        try {
            val contentResolver = context.contentResolver
            val inputStream: InputStream? = contentResolver.openInputStream(uri)
            if (inputStream != null) {
                val buffer = ByteArray(1024)
                val outputFile = File(dbPath)
                val outputStream = FileOutputStream(outputFile)
                var bytesRead = inputStream.read(buffer)
                while (bytesRead != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                    bytesRead = inputStream.read(buffer)
                }
                outputStream.close()
                inputStream.close()
                return true
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return false
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
    private lateinit var artistName: String
    private lateinit var artistProfilePicture: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_artist_discography)

        // Retrieve the artist ID, name, and profile picture from the intent
        artistId = intent.getStringExtra("artistId") ?: ""
        artistName = intent.getStringExtra("artistName") ?: ""
        artistProfilePicture = intent.getStringExtra("artistProfilePicture") ?: ""

        // Set the title to the artist name
        title = artistName

        // Set the artist name and profile picture
        val artistNameTextView = findViewById<TextView>(R.id.artistNameTextView)
        val artistProfileImageView = findViewById<ImageView>(R.id.artistProfileImageView)

        artistNameTextView.text = artistName

        Glide.with(this)
            .load(artistProfilePicture)
            .transition(DrawableTransitionOptions.withCrossFade())
            .transform(CircleCrop())
            .into(artistProfileImageView)

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


        class ViewHolder(itemView: View, private val layoutInflater: LayoutInflater) :
            RecyclerView.ViewHolder(itemView), View.OnClickListener {
            val albumCoverImageView: ImageView = itemView.findViewById(R.id.albumCoverImageView)
            val albumTitleTextView: TextView = itemView.findViewById(R.id.albumTitleTextView)
            val albumReleaseDateTextView: TextView =
                itemView.findViewById(R.id.albumReleaseDateTextView)

            lateinit var album: Album

            private var progressDialog: AlertDialog

            private val dialogViewProgress = layoutInflater.inflate(R.layout.dialog_progress, null)
            private val progressBar = dialogViewProgress.findViewById<ProgressBar>(R.id.progressBar)
            private val messageTextView = dialogViewProgress.findViewById<TextView>(R.id.messageTextView)

            init {
                progressBar.isIndeterminate = true
                messageTextView.text = "Loading data..."

                val progressDialogBuilder = MaterialAlertDialogBuilder(itemView.context)
                    .setView(dialogViewProgress)
                    .setCancelable(false)

                progressDialog = progressDialogBuilder.create()

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

                progressDialog.show()


                // Fetch the tracklist for the album with the given ID
                fetchTrackList(album.albumId) { trackList ->
                    // Display the tracklist in a dialog
                    (context as? Activity)?.runOnUiThread {
                        showTrackListDialog(
                            context,
                            trackList,
                            album.coverUrl,
                            album.releaseDate
                        ) // Pass the album cover URL and release date to the dialog
                    }
                }
            }

            private fun fetchTrackList(albumId: String, callback: (List<Track>) -> Unit) {
                val apiKey = APIKeys.DEEZER_API_KEY
                val client = OkHttpClient()
                val trackList = mutableListOf<Track>()

                fun fetchTracks(url: String) {
                    val request = Request.Builder()
                        .url(url)
                        .addHeader("Authorization", "Bearer $apiKey")
                        .build()

                    client.newCall(request).enqueue(object : Callback {
                        override fun onFailure(call: Call, e: IOException) {
                            e.printStackTrace()
                            progressDialog.dismiss()
                        }

                        override fun onResponse(call: Call, response: Response) {
                            val responseData = response.body?.string()

                            if (response.isSuccessful && responseData != null) {
                                progressDialog.dismiss()

                                val jsonResponse = JSONObject(responseData)
                                val tracksArray = jsonResponse.getJSONArray("data")

                                for (i in 0 until tracksArray.length()) {
                                    val track = tracksArray.getJSONObject(i)
                                    val trackTitle = track.getString("title")
                                    val previewUrl = track.getString("preview")
                                    trackList.add(Track(trackTitle, previewUrl))
                                }

                                val nextPageUrl = jsonResponse.optString("next")

                                if (!nextPageUrl.isNullOrEmpty()) {
                                    // Fetch next page of tracks recursively
                                    fetchTracks(nextPageUrl)
                                } else {
                                    // All tracks fetched, invoke the callback with the tracklist
                                    callback(trackList)
                                }
                            } else {
                                println("Error: ${response.code} ${response.message}")
                                progressDialog.dismiss()

                            }
                        }
                    })
                }

                val initialUrl = "https://api.deezer.com/album/$albumId/tracks?limit=100"
                fetchTracks(initialUrl)
            }


            private fun showTrackListDialog(
                context: Context,
                trackList: List<Track>,
                albumCoverUrl: String,
                releaseDate: String?
            ) {
                val dialogBuilder = MaterialAlertDialogBuilder(context)
                val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_tracklist, null)

                // Set the album cover image at the top of the dialog
                val albumCoverImageView = dialogView.findViewById<ImageView>(R.id.albumCoverImageView)
                Glide.with(context.applicationContext)
                    .load(albumCoverUrl)
                    .transition(DrawableTransitionOptions.withCrossFade())
                    .transform(RoundedCorners(50))
                    .into(albumCoverImageView)

                albumCoverImageView.setOnClickListener {
                    showFullscreenImage(albumCoverImageView.drawable, context)
                }

                val trackListRecyclerView = dialogView.findViewById<RecyclerView>(R.id.trackListRecyclerView)
                val closeButton = dialogView.findViewById<Button>(R.id.closeButton)
                val trackCounterTextView = dialogView.findViewById<TextView>(R.id.trackCounterTextView)
                val releaseDateTextView = dialogView.findViewById<TextView>(R.id.releaseDateTextView)

                val layoutManager = LinearLayoutManager(context)
                val adapter = TrackListAdapter(trackList)

                trackListRecyclerView.layoutManager = layoutManager
                trackListRecyclerView.adapter = adapter

                trackCounterTextView.text = "Total Tracks: ${trackList.size}"
                releaseDateTextView.text = releaseDate ?: ""

                dialogBuilder
                    .setView(dialogView)
                    .setOnDismissListener {
                        adapter.stopPlayback()
                    }
                    .create()
                    .apply {
                        closeButton.setOnClickListener {
                            dismiss()
                            adapter.stopPlayback()
                        }
                        show()
                    }
            }
            private fun showFullscreenImage(drawable: Drawable, context: Context) {
                val fullscreenDialog =
                    Dialog(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
                val imageView = ImageView(context)
                imageView.setImageDrawable(drawable)
                imageView.setOnClickListener {
                    fullscreenDialog.dismiss()
                }

                fullscreenDialog.setContentView(imageView)
                fullscreenDialog.show()
            }

        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val context = parent.context
            val layoutInflater = LayoutInflater.from(context)
            val view = layoutInflater.inflate(R.layout.item_album, parent, false)
            return ViewHolder(view, layoutInflater)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val album = albums[position]
            holder.bind(album)
        }

        override fun getItemCount(): Int {
            return albums.size
        }

        override fun onBindViewHolder(
            holder: ViewHolder,
            position: Int,
            payloads: MutableList<Any>
        ) {
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

        // Call the function to retrieve the tracklist for the album
        getTrackList("https://api.deezer.com/album/$albumId/tracks")
    }

    override fun onBackPressed() {
        super.onBackPressed()
        finish()
    }

    private fun getTrackList(tracklistUrl: String) {
        val apiKey = APIKeys.DEEZER_API_KEY
        val client = OkHttpClient()
        val trackList = mutableListOf<Track>()

        fun fetchTracks(url: String) {
            val request = Request.Builder()
                .url(url)
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

                        for (i in 0 until tracksArray.length()) {
                            val track = tracksArray.getJSONObject(i)
                            val trackTitle = track.getString("title")
                            val previewUrl = track.getString("preview")
                            trackList.add(Track(trackTitle, previewUrl))
                        }

                        val nextPageUrl = jsonResponse.optString("next")

                        if (!nextPageUrl.isNullOrEmpty()) {
                            // Fetch next page of tracks recursively
                            fetchTracks(nextPageUrl)
                        } else {
                            // All tracks fetched, show dialog
                            runOnUiThread {
                                showTrackListDialog(trackList)
                            }
                        }
                    } else {
                        println("Error: ${response.code} ${response.message}")
                    }
                }
            })
        }

        fetchTracks(tracklistUrl)
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
}

class ReleasesActivity : AppCompatActivity(), SwipeRefreshLayout.OnRefreshListener {
    private lateinit var releaseContainer: LinearLayout
    private val addedAlbumIds = HashSet<String>()
    private val coroutineScope = CoroutineScope(Dispatchers.Main)
    private val delayBetweenArtists = 1000L
    private lateinit var nothingHereTextView: TextView
    private lateinit var frognothinghere: ImageView
    private lateinit var progressBar: ProgressBar
    private lateinit var fetchingTextView: TextView
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var progressDialog: AlertDialog



    @SuppressLint("ServiceCast")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val dialogViewProgress = layoutInflater.inflate(R.layout.dialog_progress, null)
        val progressBar2 = dialogViewProgress.findViewById<ProgressBar>(R.id.progressBar)
        val messageTextView = dialogViewProgress.findViewById<TextView>(R.id.messageTextView)

        progressBar2.isIndeterminate = true
        messageTextView.text = "Loading data..."

        val progressDialogBuilder = MaterialAlertDialogBuilder(this)
            .setView(dialogViewProgress)
            .setCancelable(false)

        progressDialog = progressDialogBuilder.create()



        setContentView(R.layout.activity_releases)

        val bottomNavigation = findViewById<BottomNavigationView>(R.id.bottomNavigation)

        bottomNavigation.setOnNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.menu_home -> {
                    // Handle home button click
                    val intent = Intent(this@ReleasesActivity, MainActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    startActivity(intent)
                    finish()
                    true // Return true to indicate that the event is handled
                }
                R.id.menu_releases -> {
                    val intent = Intent(this@ReleasesActivity, ReleasesActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    startActivity(intent)
                    true // Return true to indicate that the event is handled
                }
                R.id.menu_settings -> {
                    // Handle settings button click
                    val intent = Intent(this@ReleasesActivity, SettingsActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    startActivity(intent)
                    finish()
                    true // Return true to indicate that the event is handled
                }
                R.id.menu_info -> {
                    // Handle info button click
                    val intent = Intent(this@ReleasesActivity, AboutActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    startActivity(intent)
                    finish()
                    true // Return true to indicate that the event is handled
                }
                else -> false // Return false for unhandled menu items
            }
        }

        bottomNavigation.selectedItemId = R.id.menu_releases



        nothingHereTextView = findViewById(R.id.nothingHereTextView)
        frognothinghere = findViewById(R.id.frognothinghere)
        releaseContainer = findViewById(R.id.releaseContainer)
        fetchingTextView = findViewById(R.id.fetchingTextView)
        progressBar = findViewById(R.id.progressBar)

        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout)
        swipeRefreshLayout.setOnRefreshListener(this)

        fetchAndDisplayReleases()

    }

    override fun onRefresh() {
        fetchAndDisplayReleases()
    }

    override fun onBackPressed() {
        val intent = Intent(this@ReleasesActivity, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        startActivity(intent)
        resetLayout()
        finish()
    }


    private fun resetLayout() {
        if (releaseContainer.childCount == 0) {
            nothingHereTextView.visibility = View.VISIBLE
            frognothinghere.visibility = View.VISIBLE
        } else {
            nothingHereTextView.visibility = View.GONE
            frognothinghere.visibility = View.GONE
        }
        releaseContainer.removeAllViews()
    }


    private var isFetching = false

    private fun fetchAndDisplayReleases() {

        if (isFetching) {
            return
        }

        isFetching = true

        val artists = fetchArtistsFromDatabase()

        if (artists.isEmpty()) {
            nothingHereTextView.visibility = View.VISIBLE
            frognothinghere.visibility = View.VISIBLE
            return
        }

        progressBar.visibility = View.VISIBLE // Show the progress bar
        progressBar.progress = 0 // Reset the progress to 0

        swipeRefreshLayout.isRefreshing = true // Show the refresh indicator

        coroutineScope.launch {
            val totalArtists = artists.size
            var fetchedArtists = 0

            for ((index, artist) in artists.withIndex()) {
                try {
                    showFetchingProgress(index + 1, totalArtists) // Show the fetching progress
                    fetchLatestRelease(artist)
                    delay(delayBetweenArtists)
                    swipeRefreshLayout.isRefreshing = false // Set isRefreshing to false on success
                } catch (e: Exception) {
                    e.printStackTrace()
                    swipeRefreshLayout.isRefreshing = false // Set isRefreshing to false on error
                    break // Stop fetching further releases on fetch failure
                } finally {
                    fetchedArtists++
                    val progress = (fetchedArtists.toFloat() / totalArtists.toFloat() * 100).toInt()
                    progressBar.progress = progress // Update the progress bar
                }
            }

            progressBar.visibility = View.GONE // Hide the progress bar when done
            fetchingTextView.visibility = View.GONE // Hide the fetching text view


            isFetching = false
        }
    }


    private fun showFetchingProgress(current: Int, total: Int) {
        val message = "Fetching artist $current out of $total..."
        fetchingTextView.text = message
        fetchingTextView.visibility = View.VISIBLE
    }

    private fun fetchArtistsFromDatabase(): List<String> {
        val dbHelper = SearchHistoryDatabaseHelper(applicationContext)
        val latestSearchQueries = dbHelper.getLatestSearchQueries(limit = 10)
        val artistIds = latestSearchQueries.map { query ->
            query.substringAfter(",").trim()
        }.distinct() // Use the distinct() function to get only unique artist IDs
        return artistIds
    }

    private fun fetchLatestRelease(artistId: String) {
        val apiKey = APIKeys.DEEZER_API_KEY
        val client = OkHttpClient()
        val request = Request.Builder()
            .url("https://api.deezer.com/artist/$artistId")
            .addHeader("Authorization", "Bearer $apiKey")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                e.printStackTrace()
                runOnUiThread {
                    Log.e("FetchReleases", "Failed to fetch artist details for artistId: $artistId")
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val responseData = response.body?.string()

                if (response.isSuccessful && responseData != null) {
                    val jsonResponse = JSONObject(responseData)
                    val artistName = jsonResponse.getString("name")
                    val artistImageUrl = jsonResponse.getString("picture_big")
                    fetchArtistLatestAlbum(artistId, artistImageUrl, artistName)
                } else {
                    Log.e("FetchReleases", "Error: ${response.code} ${response.message}")
                }
            }
        })
    }


    private fun fetchArtistLatestAlbum(
        artistId: String,
        artistImageUrl: String,
        artistName: String
    ) {
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
                    Log.e("FetchReleases", "Failed to fetch artist albums for artistId: $artistId")
                }
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
                            if (albumId !in addedAlbumIds) {
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

                                addedAlbumIds.add(albumId)
                            }
                        } else {
                            runOnUiThread {
                                // Handle case when no album is found
                                Log.e("FetchReleases", "No album found for artistId: $artistId")
                            }
                        }
                    } else {
                        runOnUiThread {
                            // Handle case when no album is found
                            Log.e("FetchReleases", "No album found for artistId: $artistId")
                        }
                    }
                } else {
                    Log.e("FetchReleases", "Error: ${response.code} ${response.message}")
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

    private fun addAlbumToView(
        album: ArtistDiscographyActivity.Album,
        artistName: String,
        artistImageUrl: String
    ) {
        val releaseItemView = LayoutInflater.from(this)
            .inflate(R.layout.item_release, releaseContainer, false)

        val artistTextView = releaseItemView.findViewById<TextView>(R.id.artistTextView)
        val releaseTitleTextView = releaseItemView.findViewById<TextView>(R.id.releaseTitleTextView)
        val releaseDateTextView = releaseItemView.findViewById<TextView>(R.id.releaseDateTextView)
        val releaseCoverImageView =
            releaseItemView.findViewById<ImageView>(R.id.releaseCoverImageView)

        artistTextView.text = artistName.toUpperCase() // Convert to uppercase
        releaseTitleTextView.text = album.title
        releaseDateTextView.text = album.releaseDate

        if (!isDestroyed) {
            val requestOptions = RequestOptions()
                .transform(RoundedCorners(50))

            Glide.with(this)
                .load(album.coverUrl)
                .apply(requestOptions)
                .into(releaseCoverImageView)
        }

        // Add a click listener to the album cover image
        releaseCoverImageView.setOnClickListener {
            progressDialog.show()
            openTracklist(album) // Pass the album object to the function to open the tracklist
        }

        // Find the index to insert the releaseItemView based on release dates
        val insertIndex = findInsertIndex(album.releaseDate)

        // Add the releaseItemView at the specified index in the releaseContainer
        releaseContainer.addView(releaseItemView, insertIndex)
    }

    private fun findInsertIndex(releaseDate: String): Int {
        val childCount = releaseContainer.childCount

        for (i in 0 until childCount) {
            val childView = releaseContainer.getChildAt(i)
            val childReleaseDateTextView =
                childView.findViewById<TextView>(R.id.releaseDateTextView)

            if (childReleaseDateTextView != null) {
                val childReleaseDate = childReleaseDateTextView.text.toString()

                if (releaseDate > childReleaseDate) {
                    return i
                }
            }
        }

        return childCount
    }


    private fun openTracklist(album: ArtistDiscographyActivity.Album) {
        // Fetch the tracklist for the album
        fetchTrackList(album.albumId) { trackList ->
            // Display the tracklist in a dialog
            runOnUiThread {
                showTrackListDialog(
                    album.releaseDate,
                    album.coverUrl,
                    trackList
                )
            }
        }
    }

    private fun fetchTrackList(albumId: String, callback: (List<Track>) -> Unit) {
        val apiKey = APIKeys.DEEZER_API_KEY
        val client = OkHttpClient()
        val trackList = mutableListOf<Track>()

        fun fetchTracks(url: String) {
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $apiKey")
                .build()

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    e.printStackTrace()
                    progressDialog.dismiss()
                }

                override fun onResponse(call: Call, response: Response) {
                    val responseData = response.body?.string()

                    if (response.isSuccessful && responseData != null) {
                        progressDialog.dismiss()

                        val jsonResponse = JSONObject(responseData)
                        val tracksArray = jsonResponse.getJSONArray("data")

                        for (i in 0 until tracksArray.length()) {
                            val track = tracksArray.getJSONObject(i)
                            val trackTitle = track.getString("title")
                            val previewUrl = track.getString("preview")
                            trackList.add(Track(trackTitle, previewUrl))
                        }

                        val nextPageUrl = jsonResponse.optString("next")

                        if (!nextPageUrl.isNullOrEmpty()) {
                            // Fetch next page of tracks recursively
                            fetchTracks(nextPageUrl)
                        } else {
                            // All tracks fetched, invoke the callback with the tracklist
                            callback(trackList)
                        }
                    } else {
                        println("Error: ${response.code} ${response.message}")
                        progressDialog.dismiss()

                    }
                }
            })
        }

        val initialUrl = "https://api.deezer.com/album/$albumId/tracks?limit=100"
        fetchTracks(initialUrl)
    }


    private fun showTrackListDialog(
        albumTitle: String,
        albumCoverUrl: String,
        trackList: List<Track>
    ) {
        val dialogBuilder = MaterialAlertDialogBuilder(this)
        val dialogView = layoutInflater.inflate(R.layout.dialog_tracklist, null)

        val albumCoverImageView = dialogView.findViewById<ImageView>(R.id.albumCoverImageView)
        val trackCounterTextView = dialogView.findViewById<TextView>(R.id.trackCounterTextView)
        val releaseDateTextView = dialogView.findViewById<TextView>(R.id.releaseDateTextView)

        // Load the album cover image using Glide
        val requestOptions = RequestOptions()
            .transform(RoundedCorners(50))

        Glide.with(this)
            .load(albumCoverUrl)
            .apply(requestOptions)
            .into(albumCoverImageView)

        // Add click listener to the album cover image
        albumCoverImageView.setOnClickListener {
            showFullscreenImage(albumCoverImageView.drawable)
        }

        trackCounterTextView.text = "Total Tracks: ${trackList.size}"
        releaseDateTextView.text = albumTitle // Set the release date text

        val trackListRecyclerView = dialogView.findViewById<RecyclerView>(R.id.trackListRecyclerView)
        val closeButton = dialogView.findViewById<Button>(R.id.closeButton)

        val layoutManager = LinearLayoutManager(this)
        val adapter = TrackListAdapter(trackList)

        trackListRecyclerView.layoutManager = layoutManager
        trackListRecyclerView.adapter = adapter

        dialogBuilder
            .setView(dialogView)
            .setOnDismissListener {
                adapter.stopPlayback()
            }
            .create()
            .apply {
                closeButton.setOnClickListener {
                    dismiss()
                    adapter.stopPlayback()
                }
                show()
            }
    }

    private fun showFullscreenImage(drawable: Drawable) {
        val fullscreenDialog = Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        val imageView = ImageView(this)
        imageView.setImageDrawable(drawable)

        fullscreenDialog.setContentView(imageView)
        fullscreenDialog.show()
    }


}