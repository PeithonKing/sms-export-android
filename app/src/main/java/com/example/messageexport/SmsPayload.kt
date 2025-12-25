package com.example.messageexport

import kotlinx.serialization.Serializable

@Serializable data class SmsPayload(val sender: String, val message: String, val timestamp: Long)
