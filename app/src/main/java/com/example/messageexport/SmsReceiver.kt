package com.example.messageexport

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log

class SmsReceiver : BroadcastReceiver() {
    private val TAG = "SmsReceiver"

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            Log.d(TAG, "SMS Received Action Detected")
            try {
                val msgs = Telephony.Sms.Intents.getMessagesFromIntent(intent)
                if (msgs.isNotEmpty()) {
                    val firstMsg = msgs.first()
                    // SMS messages can be split into multiple parts, but usually share the same
                    // sender
                    val sender = firstMsg.originatingAddress
                    val timestamp = firstMsg.timestampMillis

                    val sb = StringBuilder()
                    for (msg in msgs) {
                        sb.append(msg.messageBody)
                    }
                    val body = sb.toString()

                    Log.i(TAG, "Intercepted SMS from $sender at $timestamp")
                    Relay.forwardSms(context, sender, body, timestamp)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing SMS: ${e.message}")
                e.printStackTrace()
            }
        }
    }
}
