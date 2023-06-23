package com.n0tavailable.tunedetective

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.ActivityManager
import android.app.AlarmManager
import android.app.Dialog
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.ProgressDialog
import android.app.Service
import android.app.TaskStackBuilder
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.text.Spannable
import android.text.SpannableString
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
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ProgressBar
import androidx.appcompat.widget.SearchView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.text.HtmlCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bumptech.glide.request.RequestOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.Random
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
    private lateinit var progressDialog: ProgressDialog
    private lateinit var searchHistoryDatabaseHelper: SearchHistoryDatabaseHelper
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var welcomeMessageTextView: TextView
    private lateinit var dialog: Dialog
    private var welcomeMessageVisible = true
    private var mediaPlayer: MediaPlayer? = null
    private var artistName: String? = null
    private var selectedArtist: String? = null
    private val artistMap = mutableMapOf<String, Pair<String, String>>()


    override fun onBackPressed() {
        val intent = Intent(this@MainActivity, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        startActivity(intent)
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopPlayback()
        resetLayout()
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onStart() {
        super.onStart()

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
            // Permission already granted, continue with the app initialization
            initializeApp()
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceIntent = Intent(this, BackgroundService::class.java)
            startForegroundService(serviceIntent)
        }
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

    override fun onResume() {
        super.onResume()


        // Retrieve the toggle state from SharedPreferences
        welcomeMessageVisible = sharedPreferences.getBoolean("welcomeMessageVisible", true)

        updateWelcomeMessageVisibility()

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
        progressDialog = ProgressDialog(this)
        progressDialog.setMessage("Loading data...")
        progressDialog.setCancelable(false)

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
            // Permission already granted, continue with the app initialization
            initializeApp()
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceIntent = Intent(this, BackgroundService::class.java)
            startForegroundService(serviceIntent)
        }

        discographyButton = findViewById(R.id.discographyButton)


        sharedPreferences = getSharedPreferences("MyPrefs", Context.MODE_PRIVATE)


        showFeedbackDialog()

        val discographyButton = findViewById<Button>(R.id.discographyButton)

        // Initialize the welcomeMessageTextView
        welcomeMessageTextView = findViewById(R.id.welcomeMessageTextView)

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

            val historyAdapter =
                ArrayAdapter(this, android.R.layout.simple_list_item_1, searchHistory)
            historyListView.adapter = historyAdapter

            val alertDialogBuilder = AlertDialog.Builder(this)
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
                val alertDialogBuilder = AlertDialog.Builder(this)
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


        val aboutButton = findViewById<ImageButton>(R.id.infoButton)
        val homeButton = findViewById<ImageButton>(R.id.homeButton)
        val releasesButton = findViewById<ImageButton>(R.id.releasesButton)
        val settingsButton = findViewById<ImageButton>(R.id.settingsButton)

        homeButton.setOnClickListener {
            val intent = Intent(this@MainActivity, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(intent)
            finish()
        }

        aboutButton.setOnClickListener {
            val intent = Intent(this@MainActivity, AboutActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(intent)
            finish()
        }

        releasesButton.setOnClickListener {
            val intent = Intent(this@MainActivity, ReleasesActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(intent)
            finish()
        }

        settingsButton.setOnClickListener {
            val intent = Intent(this@MainActivity, SettingsActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(intent)
            finish()
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

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 101) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted, continue with the app initialization
                initializeApp()
            } else {
                // Permission denied, handle accordingly (e.g., show a message, disable notification-related functionality)
            }
        }
    }

    private fun initializeApp() {
        // Perform the remaining initialization steps
        // ...
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

                    // Start a new activity to display the artist discography
                    val intent = Intent(this@MainActivity, ArtistDiscographyActivity::class.java)
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
        val dialog = Dialog(this)
        dialog.setContentView(R.layout.dialog_artist_selection)

        val artistListView = dialog.findViewById<ListView>(R.id.artistListView)
        val closeButton = dialog.findViewById<Button>(R.id.closeButton)

        val artistAdapter = ArtistAdapter(this, artists)
        artistListView.adapter = artistAdapter

        artistListView.setOnItemClickListener { parent, view, position, id ->
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
                saveSelectedArtist(
                    artistId,
                    artistName
                ) // Save the selected artist name to the database
                searchArtistById(artistId)
            } else {
                Toast.makeText(
                    this@MainActivity, "Error: Artist ID not found", Toast.LENGTH_SHORT
                ).show()
            }
            dialog.dismiss()
        }

        closeButton.setOnClickListener {
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
                            Toast.makeText(applicationContext, "Loading tracks...", Toast.LENGTH_SHORT).show()
                            getTrackList(tracklistUrl, albumCoverUrl)
                        }
                    }
                } else {
                    println("Error: ${response.code} ${response.message}")
                }
            }
        })
    }

    private fun getTrackList(tracklistUrl: String, albumCoverUrl: String) {
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
                        showTrackListDialog(trackList, albumCoverUrl)
                    }
                } else {
                    println("Error: ${response.code} ${response.message}")
                }
            }
        })
    }

    private fun showTrackListDialog(trackList: List<Track>, albumCoverUrl: String) {
        dialog = Dialog(this)
        dialog.setContentView(R.layout.dialog_tracklist)

        val trackListRecyclerView = dialog.findViewById<RecyclerView>(R.id.trackListRecyclerView)
        val closeButton = dialog.findViewById<Button>(R.id.closeButton)
        val albumCoverImageView = dialog.findViewById<ImageView>(R.id.albumCoverImageView)

        // Load the album cover image
        loadAlbumCoverImage(albumCoverUrl, albumCoverImageView)

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

                Toast.makeText(view.context.applicationContext, "Loading tracks...", Toast.LENGTH_SHORT).show()

                // Fetch the tracklist for the album with the given ID
                fetchTrackList(album.albumId) { trackList ->
                    // Display the tracklist in a dialog
                    (context as? Activity)?.runOnUiThread {
                        showTrackListDialog(
                            context,
                            trackList,
                            album.coverUrl
                        ) // Pass the album cover URL to the dialog
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
                                    // All tracks fetched, invoke the callback with the tracklist
                                    callback(trackList)
                                }
                            } else {
                                println("Error: ${response.code} ${response.message}")
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
                albumCoverUrl: String
            ) {
                val dialog = Dialog(context)
                dialog.setContentView(R.layout.dialog_tracklist)

                // Set the album cover image at the top of the dialog
                val albumCoverImageView = dialog.findViewById<ImageView>(R.id.albumCoverImageView)
                Glide.with(context.applicationContext)
                    .load(albumCoverUrl)
                    .transition(DrawableTransitionOptions.withCrossFade())
                    .transform(RoundedCorners(50))
                    .into(albumCoverImageView)

                albumCoverImageView.setOnClickListener {
                    showFullscreenImage(albumCoverImageView.drawable, context)
                }

                val trackListRecyclerView =
                    dialog.findViewById<RecyclerView>(R.id.trackListRecyclerView)
                val closeButton = dialog.findViewById<Button>(R.id.closeButton)

                val layoutManager = LinearLayoutManager(context)
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
    private lateinit var notificationManager: NotificationManagerCompat
    private val handler = Handler(Looper.getMainLooper())
    private val coroutineScope = CoroutineScope(Dispatchers.Main)
    private val delayBetweenArtists = 50L
    private var notificationId = 1 // Initial notification ID
    private val shownNotifications = HashSet<String>()
    private lateinit var nothingHereTextView: TextView
    private lateinit var frognothinghere: ImageView
    private lateinit var progressBar: ProgressBar
    private lateinit var fetchingTextView: TextView
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout

    private val alarmManager by lazy {
        getSystemService(Context.ALARM_SERVICE) as AlarmManager
    }
    private lateinit var fetchReleasesPendingIntent: PendingIntent

    private val fetchRunnable = object : Runnable {
        override fun run() {
            fetchAndDisplayReleases()
            handler.postDelayed(
                this,
                60 * 60 * 1000L // 60 minutes (15 * 60 * 1000)
            ) // Schedule the next execution after 60 minutes
        }
    }

    @SuppressLint("ServiceCast")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_releases)

        nothingHereTextView = findViewById(R.id.nothingHereTextView)
        frognothinghere = findViewById(R.id.frognothinghere)
        releaseContainer = findViewById(R.id.releaseContainer)
        notificationManager = NotificationManagerCompat.from(this)
        fetchingTextView = findViewById(R.id.fetchingTextView)
        progressBar = findViewById(R.id.progressBar)

        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout)
        swipeRefreshLayout.setOnRefreshListener(this)

        // Create the PendingIntent for fetch releases
        val fetchIntent = Intent(this, FetchReleasesReceiver::class.java)
        fetchReleasesPendingIntent = PendingIntent.getBroadcast(
            this,
            0,
            fetchIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )


        createNotificationChannel()
        fetchAndDisplayReleases()
        handler.postDelayed(fetchRunnable, 60 * 60 * 1000L); // Start periodic execution after 1 hour


        val aboutButton = findViewById<ImageButton>(R.id.infoButton)
        val homeButton = findViewById<ImageButton>(R.id.homeButton)
        val releasesButton = findViewById<ImageButton>(R.id.releasesButton)
        val settingsButton = findViewById<ImageButton>(R.id.settingsButton)

        homeButton.setOnClickListener {
            val intent = Intent(this@ReleasesActivity, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(intent)
            finish()
        }

        aboutButton.setOnClickListener {
            val intent = Intent(this@ReleasesActivity, AboutActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(intent)
            finish()
        }

        releasesButton.setOnClickListener {
            val intent = Intent(this@ReleasesActivity, ReleasesActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(intent)
            finish()
        }

        settingsButton.setOnClickListener {
            val intent = Intent(this@ReleasesActivity, SettingsActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(intent)
            finish()
        }


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

    @SuppressLint("MissingPermission")
    private fun showFetchSuccessNotification() {
        val notificationId = generateNotificationId("FetchSuccess", "")

        val intent = Intent(this, ReleasesActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        val channelId = "FetchSuccessChannel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelName = "Fetch Success Channel"
            val channel =
                NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_LOW)
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.notification_icon)
            .setContentTitle("Fetch successfully started")
            .setContentText("Successfully started fetching releases in the background.")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notification.flags =
            notification.flags or NotificationCompat.FLAG_ONLY_ALERT_ONCE or NotificationCompat.FLAG_AUTO_CANCEL

        notificationManager.notify(notificationId, notification)
    }

    private fun showFetchFailureNotification() {
        val notificationId = generateNotificationId("FetchFailure", "")

        val intent = Intent(this, ReleasesActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        val notificationManager: NotificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Create the notification channel
        val channelId = "FetchFailureChannel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelName = "Fetch Failure Channel"
            val channel =
                NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_LOW)
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.notification_icon)
            .setContentTitle("Fetch Failed")
            .setContentText("Failed to fetch releases. Retrying in 60 minutes")
            .setPriority(NotificationCompat.PRIORITY_LOW) // Set the priority to the lowest possible
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        // Modify the notification attributes to make it silent and prevent vibration
        notification.flags =
            notification.flags or NotificationCompat.FLAG_ONLY_ALERT_ONCE or NotificationCompat.FLAG_AUTO_CANCEL

        notificationManager.notify(notificationId, notification)

        handler.postDelayed(fetchRunnable, 60 * 60 * 1000L); // Start periodic execution after 1 hour
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
                    showFetchSuccessNotification() // Show fetch success notification
                } catch (e: Exception) {
                    e.printStackTrace()
                    showFetchFailureNotification()
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

            val handler = Handler()
            val fetchRunnable = Runnable {
                fetchAndDisplayReleases()
            }

            // Schedule the periodic execution of fetch releases
            handler.postDelayed(fetchRunnable, 60 * 60 * 1000)
        }
    }


    private fun showFetchingProgress(current: Int, total: Int) {
        val message = "Fetching artist $current out of $total..."
        fetchingTextView.text = message
        fetchingTextView.visibility = View.VISIBLE
    }

    private fun fetchArtistsFromDatabase(): List<String> {
        val dbHelper = SearchHistoryDatabaseHelper(this)
        val latestSearchQueries = dbHelper.getLatestSearchQueries(limit = 10)
        val artistIds = latestSearchQueries.map { query ->
            query.substringAfter(",").trim()
        }
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
                    showFetchFailureNotification()
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
                    println("Error: ${response.code} ${response.message}")
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
                    showFetchFailureNotification()
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
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val releaseDate = sdf.parse(album.releaseDate)
        val currentDate = Date()

        val releaseKey = "$artistName-${album.title}"
        if (releaseKey !in shownNotifications) {
            if (releaseDate != null && currentDate.time - releaseDate.time <= 1 * 24 * 60 * 60 * 1000) {
                showNotification(
                    artistName,
                    album.title,
                    album.coverUrl
                ) // Pass the album cover URL
                shownNotifications.add(releaseKey)
            }
        }

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
            Toast.makeText(applicationContext, "Loading tracks...", Toast.LENGTH_SHORT).show()
            openTracklist(album) // Pass the album object to the function to open the tracklist
        }

        releaseContainer.addView(releaseItemView)
    }

    private fun showNotification(artistName: String, albumTitle: String, albumCoverUrl: String) {
        val notificationId =
            generateNotificationId(artistName, albumTitle) // Generate unique notification ID

        val intent = Intent(this, ReleasesActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        // Check if the user is on the ReleasesActivity or item_release screen
        val isUserOnReleasesActivity = isUserOnActivity(ReleasesActivity::class.java)

        if (isUserOnReleasesActivity) {
            // User is on one of the screens, don't show the notification
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            val bitmap = getBitmapFromUrl(albumCoverUrl)
            withContext(Dispatchers.Main) {
                val notification = NotificationCompat.Builder(this@ReleasesActivity, CHANNEL_ID)
                    .setSmallIcon(R.drawable.notification_icon)
                    .setContentTitle("New Release")
                    .setContentText("New release from $artistName: $albumTitle")
                    .setLargeIcon(bitmap) // Set the retrieved bitmap as a large icon
                    .setStyle(
                        NotificationCompat.BigPictureStyle()
                            .bigPicture(bitmap) // Set the retrieved bitmap as a big picture
                            .bigLargeIcon(null as Bitmap?) // Explicitly specify the argument type to resolve ambiguity
                    )
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setContentIntent(pendingIntent)
                    .setAutoCancel(true)
                    .build()

                if (ActivityCompat.checkSelfPermission(
                        this@ReleasesActivity,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    return@withContext
                }
                val notificationManager: NotificationManager =
                    getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.notify(
                    notificationId,
                    notification
                ) // Use the unique notification ID

                handler.postDelayed(
                    fetchRunnable,
                    60 * 60 * 1000L
                ); // Start periodic execution after 1 hour
            }
        }
    }

    private fun isUserOnActivity(activityClass: Class<*>): Boolean {
        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val runningActivities = activityManager.getRunningTasks(1)

        if (runningActivities.isNotEmpty()) {
            val topActivity = runningActivities[0].topActivity
            if (topActivity?.className == activityClass.name) {
                return true
            }
        }

        return false
    }

    private suspend fun getBitmapFromUrl(url: String): Bitmap? {
        return try {
            withContext(Dispatchers.IO) {
                val inputStream = URL(url).openStream()
                BitmapFactory.decodeStream(inputStream)
            }
        } catch (e: IOException) {
            e.printStackTrace()
            null
        }
    }

    private fun generateNotificationId(artistName: String, albumTitle: String): Int {
        val artistHash = artistName.hashCode()
        val albumHash = albumTitle.hashCode()
        return artistHash xor albumHash
    }


    @SuppressLint("ServiceCast")
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "New Releases"
            val descriptionText = "Shows notifications for new music releases"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }

            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    companion object {
        private const val CHANNEL_ID = "new_releases_channel"
    }


    private fun openTracklist(album: ArtistDiscographyActivity.Album) {
        // Fetch the tracklist for the album
        fetchTrackList(album.albumId) { trackList ->
            // Display the tracklist in a dialog
            runOnUiThread {
                showTrackListDialog(album.title, album.coverUrl, trackList)
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
                            // All tracks fetched, invoke the callback with the tracklist
                            callback(trackList)
                        }
                    } else {
                        println("Error: ${response.code} ${response.message}")
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
        val dialog = Dialog(this)
        dialog.setContentView(R.layout.dialog_tracklist)

        val albumCoverImageView = dialog.findViewById<ImageView>(R.id.albumCoverImageView)

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

    private fun showFullscreenImage(drawable: Drawable) {
        val fullscreenDialog = Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        val imageView = ImageView(this)
        imageView.setImageDrawable(drawable)

        fullscreenDialog.setContentView(imageView)
        fullscreenDialog.show()
    }


}

class BackgroundService : Service() {
    private lateinit var releasesActivityIntent: Intent
    private val NOTIFICATION_CHANNEL_ID = "ReleasesNotificationChannel"
    private val NOTIFICATION_ID = 1

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()
        startForegroundService()
    }

    private fun startForegroundService() {
        releasesActivityIntent = Intent(this, ReleasesActivity::class.java)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationChannel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Background Service - Can be turned off",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val notificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(notificationChannel)
        }

        val notification = createNotification()
        startForeground(NOTIFICATION_ID, notification)
        startReleasesActivity()
    }

    @SuppressLint("LaunchActivityFromNotification")
    private fun createNotification(): Notification {
        val releasesActivityIntent = Intent(this, ReleasesActivity::class.java)

        // Create a TaskStackBuilder to handle the intent
        val taskStackBuilder = TaskStackBuilder.create(this)
            .addNextIntentWithParentStack(releasesActivityIntent)

        // Get the PendingIntent from the TaskStackBuilder
        val pendingIntent = taskStackBuilder.getPendingIntent(
            0,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Notification-Setup")
            .setContentText("Tap on me to initialize notifications. Then you can hide me by a long click.")
            .setSmallIcon(R.drawable.notification_icon)
            .setContentIntent(pendingIntent)
            .setOngoing(true) // Set the notification as ongoing
            .build()
    }

    private fun startReleasesActivity() {
        releasesActivityIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}

class FetchReleasesReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val releasesActivityIntent = Intent(context, ReleasesActivity::class.java)
        releasesActivityIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(releasesActivityIntent)
    }
}