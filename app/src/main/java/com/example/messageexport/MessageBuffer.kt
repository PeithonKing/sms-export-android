package com.example.messageexport

import android.content.Context
import android.util.Log
import java.io.File
import java.util.UUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
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
            if (jsonString.isBlank()) return emptyList()
            Json.decodeFromString<List<BufferedMessage>>(jsonString)
        } catch (e: Exception) {
            Log.e(TAG, "Error reading buffered messages", e)
            emptyList()
        }
    }

    @Synchronized
    private fun saveMessages(context: Context, messages: List<BufferedMessage>) {
        try {
            val jsonString = Json.encodeToString(messages)
            context.openFileOutput(FILE_NAME, Context.MODE_PRIVATE).use {
                it.write(jsonString.toByteArray())
            }
            // Notify UI of changes
            val intent = android.content.Intent("com.example.messageexport.BUFFER_UPDATED")
            intent.setPackage(context.packageName) // Restrict to this app
            context.sendBroadcast(intent)
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

    @Synchronized
    fun removeMessages(context: Context, ids: Set<String>) {
        val messages = getMessages(context).toMutableList()
        val initialSize = messages.size
        val removed = messages.removeIf { it.id in ids }
        if (removed) {
            saveMessages(context, messages)
            Log.i(
                    TAG,
                    "Batch removed ${initialSize - messages.size} messages. Remaining: ${messages.size}"
            )
        }
    }

    fun retryBufferedMessages(context: Context): Pair<Int, Int> {
        val messages = getMessages(context)
        if (messages.isEmpty()) return Pair(0, 0)

        Log.i(TAG, "Retrying ${messages.size} buffered messages...")
        var sentCount = 0
        var failCount = 0
        val successfulIds = mutableSetOf<String>()

        messages.forEach { msg ->
            // Note: forwardSms is blocking, so this is synchronous
            val success = Relay.forwardSms(context, msg.sender, msg.body, msg.timestamp)
            if (success) {
                successfulIds.add(msg.id)
                sentCount++
            } else {
                failCount++
            }
        }

        if (successfulIds.isNotEmpty()) {
            removeMessages(context, successfulIds)
        }

        Log.i(TAG, "Retry complete. Sent: $sentCount, Failed: $failCount")
        return Pair(sentCount, failCount)
    }

    fun retryMessages(context: Context, ids: Set<String>): Pair<Int, Int> {
        val allMessages = getMessages(context)
        val messagesToRetry = allMessages.filter { it.id in ids }

        if (messagesToRetry.isEmpty()) return Pair(0, 0)

        Log.i(TAG, "Retrying ${messagesToRetry.size} selected messages...")
        var sentCount = 0
        var failCount = 0
        val successfulIds = mutableSetOf<String>()

        messagesToRetry.forEach { msg ->
            val success = Relay.forwardSms(context, msg.sender, msg.body, msg.timestamp)
            if (success) {
                successfulIds.add(msg.id)
                sentCount++
            } else {
                failCount++
            }
        }

        if (successfulIds.isNotEmpty()) {
            removeMessages(context, successfulIds)
        }

        Log.i(TAG, "Selected retry complete. Sent: $sentCount, Failed: $failCount")
        return Pair(sentCount, failCount)
    }
}
