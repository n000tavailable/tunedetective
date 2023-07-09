package com.n0tavailable.tunedetective

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.edit
import androidx.work.Worker
import androidx.work.WorkerParameters
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Semaphore

class FetchReleasesWorker(context: Context, workerParams: WorkerParameters) : Worker(context, workerParams) {


    private val coroutineScope = CoroutineScope(Dispatchers.Main)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val addedAlbumIds = HashSet<String>() // Store unique artistId_albumId combinations
    private val delayBetweenArtists = 1000L
    private val requestSemaphore = Semaphore(2)
    private val client = OkHttpClient()
    private val fetchedArtistIds = HashSet<String>()
    private val notifiedAlbums = HashSet<String>()
    // Store notified albums in SharedPreferences
    private val sharedPreferences = context.getSharedPreferences("NotifiedAlbums", Context.MODE_PRIVATE)
    private var fetchSuccess = true




    override fun doWork(): Result {
        try {
            // Fetch and display releases
            fetchAndDisplayReleases()

            // Return success if the work is completed successfully
            return Result.success()
        } catch (e: Exception) {
            e.printStackTrace()

            // Return failure if an error occurs
            return Result.failure()
        }
    }

    private fun isWithinLastThreeDays(releaseDate: String): Boolean {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val releaseDateTime = dateFormat.parse(releaseDate)
        val currentTime = System.currentTimeMillis()
        val threeDaysInMillis = 3 * 24 * 60 * 60 * 1000 // 1 day in milliseconds

        return releaseDateTime != null && currentTime - releaseDateTime.time <= threeDaysInMillis
    }
    private fun fetchAndDisplayReleases() {
        val artists = fetchArtistsFromDatabase()

        if (artists.isEmpty()) {
            return
        }

        coroutineScope.launch(Dispatchers.IO) {
            val totalArtists = artists.size

            for ((index, artist) in artists.withIndex()) {
                try {
                    fetchLatestRelease(artist)
                    delay(delayBetweenArtists)
                } catch (e: Exception) {
                    e.printStackTrace()
                    break
                }
            }

            withContext(Dispatchers.Main) {
                showFetchedNotification(totalArtists)
            }
        }
    }

    private fun fetchLatestRelease(artistId: String) {
        if (fetchedArtistIds.contains(artistId)) {
            // Artist already fetched, skip the network request
            return
        }

        fetchedArtistIds.add(artistId)

        val apiKey = APIKeys.DEEZER_API_KEY
        val request = Request.Builder()
            .url("https://api.deezer.com/artist/$artistId")
            .addHeader("Authorization", "Bearer $apiKey")
            .build()

        requestSemaphore.acquire() // Acquire a permit from the semaphore

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                e.printStackTrace()
                mainHandler.post {
                    // Handle failure case
                    fetchSuccess = false
                    requestSemaphore.release() // Release the permit when the request is finished or failed
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val responseData = response.body?.string()

                if (response.isSuccessful && responseData != null) {
                    val jsonResponse = JSONObject(responseData)
                    val artistName = jsonResponse.getString("name")
                    val artistImageUrl = jsonResponse.getString("picture_big")

                    println("Fetching latest release for artistId: $artistId") // Log artistId

                    fetchArtistLatestAlbum(artistId, artistImageUrl, artistName)
                } else {
                    println("Error: ${response.code} ${response.message}")
                }

                // Switch to the main thread for UI-related operations
                mainHandler.post {
                    // Perform UI-related tasks here
                    requestSemaphore.release() // Release the permit when the request is finished or failed
                }
            }
        })
    }

    private fun addAlbumToView(
        album: ArtistDiscographyActivity.Album,
        artistName: String,
        artistImageUrl: String
    ) {
        // Add album to view
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
                mainHandler.post {
                    // Handle failure case
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
                            if (albumId !in addedAlbumIds && !isAlbumNotified(artistId, albumId)) {
                                val albumTitle = latestAlbum.getString("title")
                                val albumCoverUrl = latestAlbum.getString("cover_big")
                                val releaseDate = latestAlbum.getString("release_date")

                                if (isWithinLastThreeDays(releaseDate)) {
                                    val albumItem = ArtistDiscographyActivity.Album(
                                        albumId,
                                        albumTitle,
                                        albumCoverUrl,
                                        releaseDate
                                    )

                                    mainHandler.post {
                                        addAlbumToView(albumItem, artistName, artistImageUrl)

                                        showNotification(artistName, albumTitle, albumCoverUrl)

                                        markAlbumAsNotified(artistId, albumId)
                                    }

                                    addedAlbumIds.add(albumId)
                                }
                            }
                        } else {
                            mainHandler.post {
                                // Handle case when no album is found
                            }
                        }
                    } else {
                        mainHandler.post {
                            // Handle case when no album is found
                        }
                    }
                } else {
                    println("Error: ${response.code} ${response.message}")
                }
            }
        })
    }

    private fun generateNotificationId(artistName: String, albumTitle: String): String {
        // Generate a unique notification ID based on the artist name and album title
        return "$artistName:$albumTitle"
    }


    private fun isAlbumNotified(artistName: String, albumTitle: String): Boolean {
        // Generate the notification ID
        val notificationId = generateNotificationId(artistName, albumTitle)
        // Check if the notification ID is in the shared preferences
        return sharedPreferences.getBoolean(notificationId, false)
    }

    private fun markAlbumAsNotified(artistName: String, albumTitle: String) {
        // Generate the notification ID
        val notificationId = generateNotificationId(artistName, albumTitle)
        // Store the notification ID in shared preferences
        sharedPreferences.edit().putBoolean(notificationId, true).apply()
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

    private fun fetchArtistsFromDatabase(): List<String> {
        val dbHelper = SearchHistoryDatabaseHelper(applicationContext)
        val latestSearchQueries = dbHelper.getLatestSearchQueries(limit = 10)
        val artistIds = mutableSetOf<String>() // Use a mutable set to store unique artist IDs

        for (query in latestSearchQueries) {
            val artistId = query.substringAfter(",").trim()
            artistIds.add(artistId)
        }

        for (artistId in artistIds) {
            println("Fetching artistId from database: $artistId") // Log artistId from the database
        }

        return artistIds.toList() // Convert the set back to a list before returning
    }

    @SuppressLint("MissingPermission")
    private fun showFetchedNotification(totalArtists: Int) {
        val channelId = "fetch_releases_channel"
        val notificationId = 1

        createNotificationChannel()

        val notificationBuilder = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(R.drawable.frog_noti)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .setDefaults(NotificationCompat.DEFAULT_LIGHTS) // Make the notification silent and disable vibration

        if (fetchSuccess) {
            notificationBuilder
                .setContentTitle("You're up-to-date!")
                .setContentText("Fetching releases for artists started successfully.")
        } else {
            notificationBuilder
                .setContentTitle("Fetch Failed")
                .setContentText("Fetching releases for artists failed. Please try again.")
        }

        with(NotificationManagerCompat.from(applicationContext)) {
            notify(notificationId, notificationBuilder.build())
        }
    }

    @SuppressLint("MissingPermission", "ObsoleteSdkInt")
    private fun showNotification(artistName: String, albumTitle: String, albumCoverUrl: String) {
        val channelId = "new_release_channel"
        // Check if the album has already been notified
        if (isAlbumNotified(artistName, albumTitle)) {
            return
        }

        createNotificationChannel()

        val notificationBuilder = NotificationCompat.Builder(applicationContext, channelId)
            .setContentTitle("New Release")
            .setContentText("New release for $artistName: $albumTitle")
            .setSmallIcon(R.drawable.frog_noti) // Set the small icon here
            .setPriority(NotificationCompat.PRIORITY_HIGH)

        val bigPictureStyle = NotificationCompat.BigPictureStyle(notificationBuilder)

        coroutineScope.launch {
            val albumCoverBitmap = getBitmapFromUrl(albumCoverUrl)
            if (albumCoverBitmap != null) {

                bigPictureStyle.bigPicture(albumCoverBitmap)

                with(NotificationManagerCompat.from(applicationContext)) {
                    val notificationId =
                        System.currentTimeMillis().toInt() // Generate a unique notification ID
                    notify(notificationId, notificationBuilder.build())
                }
            }
        }
        markAlbumAsNotified(artistName, albumTitle)
    }

    private suspend fun getBitmapFromUrl(url: String): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val client = OkHttpClient()
            val request = Request.Builder()
                .url(url)
                .build()

            val response = client.newCall(request).execute()
            val inputStream = response.body?.byteStream()
            BitmapFactory.decodeStream(inputStream)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val fetchReleasesChannel = NotificationChannel(
                "fetch_releases_channel",
                "Fetch Releases Channel",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Channel for fetching artist releases"
            }

            val newReleaseChannel = NotificationChannel(
                "new_release_channel",
                "New Release Channel",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Channel for new artist releases"
            }

            val notificationManager =
                applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            notificationManager.createNotificationChannel(fetchReleasesChannel)
            notificationManager.createNotificationChannel(newReleaseChannel)
        }
    }
}
