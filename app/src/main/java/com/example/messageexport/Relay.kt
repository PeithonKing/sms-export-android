package com.example.messageexport

import android.util.Log
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

object Relay {
    private const val TAG = "Relay"
    // Configuration
    private const val PREFS_NAME = "MessageExportPrefs"
    private const val KEY_WIFI_ONLY = "wifi_only"
    private const val KEY_SERVER_URL = "server_url"
    private const val DEFAULT_SERVER_URL = "http://192.168.29.24:5000"

    fun forwardSms(
            context: android.content.Context,
            sender: String?,
            body: String?,
            timestamp: Long
    ): Boolean {
        if (sender == null || body == null) {
            Log.e(TAG, "Skipping relay: Sender or Body is null")
            return false
        }

        // Check Preferences
        val prefs = context.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
        val wifiOnly = prefs.getBoolean(KEY_WIFI_ONLY, false)
        var serverUrl = prefs.getString(KEY_SERVER_URL, DEFAULT_SERVER_URL) ?: DEFAULT_SERVER_URL

        if (wifiOnly && !isWifiConnected(context)) {
            Log.i(TAG, "Skipping relay: Wi-Fi only mode is enabled and not connected to Wi-Fi.")
            return false
        }

        // Sanitize URL
        if (!serverUrl.startsWith("http://") && !serverUrl.startsWith("https://")) {
            serverUrl = "http://$serverUrl"
        }
        if (serverUrl.endsWith("/")) {
            serverUrl = serverUrl.dropLast(1)
        }

        Log.d(TAG, "Forwarding SMS from $sender at $timestamp: $body to $serverUrl")

        var urlConnection: HttpURLConnection? = null
        try {
            // Determine endpoint based on URL structure logic if needed,
            // but for now simply append "/" as the root endpoint was requested.
            // However, URL sanitation removed trailing slash, so we add it back if needed for
            // the endpoint
            val finalUrl = "$serverUrl/"

            val url = URL(finalUrl)
            urlConnection = url.openConnection() as HttpURLConnection
            urlConnection.apply {
                requestMethod = "POST"
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                setRequestProperty("Accept", "application/json")
                connectTimeout = 5000
                readTimeout = 5000
            }

            // Create JSON payload manually
            val safeSender = escapeJsonString(sender)
            val safeBody = escapeJsonString(body)
            val jsonPayload =
                    "{\"sender\": \"$safeSender\", \"message\": \"$safeBody\", \"timestamp\": $timestamp}"

            OutputStreamWriter(urlConnection.outputStream).use { writer ->
                writer.write(jsonPayload)
                writer.flush()
            }

            val responseCode = urlConnection.responseCode
            Log.d(TAG, "Server responded with code: $responseCode")
            return responseCode == 200
        } catch (e: Exception) {
            Log.e(TAG, "Failed to relay message: ${e.message}")
            e.printStackTrace()
            return false
        } finally {
            urlConnection?.disconnect()
        }
    }

    private fun isWifiConnected(context: android.content.Context): Boolean {
        val connectivityManager =
                context.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as
                        android.net.ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI)
    }

    private fun escapeJsonString(input: String): String {
        return input.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t")
    }
}
