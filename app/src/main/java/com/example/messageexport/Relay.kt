package com.example.messageexport

import android.util.Log
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

object Relay {
    private const val TAG = "Relay"
    // Configuration
    private const val SERVER_IP = "192.168.29.2"
    private const val SERVER_PORT = 5000
    private const val ENDPOINT = "/" // Using root endpoint for now per instructions

    fun forwardSms(sender: String?, body: String?) {
        if (sender == null || body == null) {
            Log.e(TAG, "Skipping relay: Sender or Body is null")
            return
        }

        Log.d(TAG, "Forwarding SMS from $sender: $body")

        thread(start = true) {
            var urlConnection: HttpURLConnection? = null
            try {
                val url = URL("http://$SERVER_IP:$SERVER_PORT$ENDPOINT")
                urlConnection = url.openConnection() as HttpURLConnection
                urlConnection.apply {
                    requestMethod = "POST"
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                    setRequestProperty("Accept", "application/json")
                    connectTimeout = 5000
                    readTimeout = 5000
                }

                // Create JSON payload manually to avoid adding Gson/Moshi dependency for now
                // Escaping quotes and newlines roughly to be safe-ish
                val safeSender = escapeJsonString(sender)
                val safeBody = escapeJsonString(body)
                val jsonPayload = "{\"sender\": \"$safeSender\", \"message\": \"$safeBody\"}"

                OutputStreamWriter(urlConnection.outputStream).use { writer ->
                    writer.write(jsonPayload)
                    writer.flush()
                }

                val responseCode = urlConnection.responseCode
                Log.d(TAG, "Server responded with code: $responseCode")

            } catch (e: Exception) {
                Log.e(TAG, "Failed to relay message: ${e.message}")
                e.printStackTrace()
            } finally {
                urlConnection?.disconnect()
            }
        }
    }

    private fun escapeJsonString(input: String): String {
        return input.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }
}
