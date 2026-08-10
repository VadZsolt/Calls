package com.example.calls.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.core.content.FileProvider
import com.android.volley.Request
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import com.example.calls.BuildConfig
import com.example.calls.R
import com.example.calls.sync.VolleySingleton
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request as OkHttpRequest
import okhttp3.Response
import java.io.File
import java.io.IOException

data class UpdateInfo(
    val latestVersionCode: Int,
    val apkUrl: String,
    val changelog: String,
    val description : String
)

class AppUpdater(private val context: Context) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val client = OkHttpClient()

    fun checkForUpdate(onResult: (UpdateInfo?) -> Unit) {
        val queue = VolleySingleton.getInstance(context)
        val url = "${context.getString(R.string.script_url)}?action=appVersion"

        val request = JsonObjectRequest(
            Request.Method.GET, url, null,
            { response ->
                val latestVersionCode = response.optInt("latestVersionCode", 0)
                val apkUrl = response.optString("apkUrl", "")
                val changelog = response.optString("changelog", "")
                val description = response.optString("description", "")

                val currentVersionCode = BuildConfig.VERSION_CODE

                if (latestVersionCode > currentVersionCode && apkUrl.isNotBlank()) {
                    onResult(UpdateInfo(latestVersionCode, apkUrl, changelog, description))
                } else {
                    onResult(null)
                }
            },
            { onResult(null) }
        )

        queue.add(request)
    }

    fun downloadAndInstall(
        apkUrl: String,
        onStarted: () -> Unit,
        onProgress: (percent: Int) -> Unit,
        onComplete: () -> Unit,
        onError: (String) -> Unit
    ) {
        val dir = context.getExternalFilesDir(null)
        if (dir == null) {
            onError("Storage not available")
            return
        }
        if (!dir.exists()) dir.mkdirs()

        val destination = File(dir, "calls_update.apk")
        if (destination.exists()) destination.delete()

        mainHandler.post { onStarted() }

        val request = OkHttpRequest.Builder()
            .url(apkUrl)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                mainHandler.post { onError(e.message ?: "Network error") }
            }

            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    mainHandler.post { onError("Server returned ${response.code}") }
                    response.close()
                    return
                }

                val body = response.body
                if (body == null) {
                    mainHandler.post { onError("Empty response body") }
                    return
                }

                val totalBytes = body.contentLength()
                var bytesRead = 0L
                var lastReportedPercent = -1

                try {
                    body.byteStream().use { input ->
                        destination.outputStream().use { output ->
                            val buffer = ByteArray(8 * 1024)
                            var read: Int
                            while (input.read(buffer).also { read = it } != -1) {
                                output.write(buffer, 0, read)
                                bytesRead += read

                                if (totalBytes > 0) {
                                    val percent = ((bytesRead * 100) / totalBytes).toInt()
                                    if (percent != lastReportedPercent) {
                                        lastReportedPercent = percent
                                        mainHandler.post { onProgress(percent) }
                                    }
                                }
                            }
                        }
                    }

                    mainHandler.post {
                        onComplete()
                        installApk(destination)
                    }
                } catch (e: Exception) {
                    mainHandler.post { onError(e.message ?: "Download failed while writing file") }
                } finally {
                    response.close()
                }
            }
        })
    }

    private fun installApk(apkFile: File) {
        val apkUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )

        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }

        context.startActivity(installIntent)
    }
}