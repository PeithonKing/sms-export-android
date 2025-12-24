package com.example.messageexport

import android.content.Context
import android.util.Log
import java.io.File
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

data class BufferedMessage(
        val id: String,
        val sender: String,
        val body: String,
        val timestamp: Long
)

object MessageBuffer {
    private const val TAG = "MessageBuffer"
    private const val FILE_NAME = "buffered_messages.json"

    @Synchronized
    fun addMessage(context: Context, sender: String, body: String, timestamp: Long) {
        val messages = getMessages(context).toMutableList()
        val newMessage =
                BufferedMessage(
                        id = UUID.randomUUID().toString(),
                        sender = sender,
                        body = body,
                        timestamp = timestamp
                )
        messages.add(newMessage)
        saveMessages(context, messages)
        Log.i(TAG, "Buffered new message. Total buffered: ${messages.size}")
    }

    @Synchronized
    fun getMessages(context: Context): List<BufferedMessage> {
        val file = File(context.filesDir, FILE_NAME)
        if (!file.exists()) return emptyList()

        return try {
            val jsonString = file.readText()
            val jsonArray = JSONArray(jsonString)
            val messages = mutableListOf<BufferedMessage>()

            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                messages.add(
                        BufferedMessage(
                                id = obj.getString("id"),
                                sender = obj.getString("sender"),
                                body = obj.getString("body"),
                                timestamp = obj.getLong("timestamp")
                        )
                )
            }
            messages
        } catch (e: Exception) {
            Log.e(TAG, "Error reading buffered messages", e)
            emptyList()
        }
    }

    @Synchronized
    private fun saveMessages(context: Context, messages: List<BufferedMessage>) {
        try {
            val jsonArray = JSONArray()
            messages.forEach { msg ->
                val obj = JSONObject()
                obj.put("id", msg.id)
                obj.put("sender", msg.sender)
                obj.put("body", msg.body)
                obj.put("timestamp", msg.timestamp)
                jsonArray.put(obj)
            }
            context.openFileOutput(FILE_NAME, Context.MODE_PRIVATE).use {
                it.write(jsonArray.toString().toByteArray())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error saving buffered messages", e)
        }
    }

    @Synchronized
    fun removeMessage(context: Context, id: String) {
        val messages = getMessages(context).toMutableList()
        val removed = messages.removeIf { it.id == id }
        if (removed) {
            saveMessages(context, messages)
            Log.i(TAG, "Removed message $id from buffer. Remaining: ${messages.size}")
        }
    }

    fun retryBufferedMessages(context: Context): Pair<Int, Int> {
        val messages = getMessages(context)
        if (messages.isEmpty()) return Pair(0, 0)

        Log.i(TAG, "Retrying ${messages.size} buffered messages...")
        var sentCount = 0
        var failCount = 0

        messages.forEach { msg ->
            // Note: forwardSms is blocking, so this is synchronous
            val success = Relay.forwardSms(context, msg.sender, msg.body, msg.timestamp)
            if (success) {
                removeMessage(context, msg.id)
                sentCount++
            } else {
                failCount++
            }
        }
        Log.i(TAG, "Retry complete. Sent: $sentCount, Failed: $failCount")
        return Pair(sentCount, failCount)
    }
}
