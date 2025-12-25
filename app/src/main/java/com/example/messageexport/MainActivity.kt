package com.example.messageexport

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import com.example.messageexport.ui.theme.MessageExportTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Request permissions immediately on launch for simplicity, as user agreed to grant them
        val permissions =
                mutableListOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        ActivityCompat.requestPermissions(this, permissions.toTypedArray(), 101)

        // Request battery optimization exemption
        val powerManager =
                getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                        !powerManager.isIgnoringBatteryOptimizations(packageName)
        ) {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            intent.data = Uri.parse("package:$packageName")
            startActivity(intent)
        }

        setContent {
            MessageExportTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MainScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@Composable
fun MainScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val prefs = remember {
        context.getSharedPreferences("MessageExportPrefs", android.content.Context.MODE_PRIVATE)
    }
    var wifiOnly by remember { mutableStateOf(prefs.getBoolean("wifi_only", false)) }
    var pauseSync by remember { mutableStateOf(prefs.getBoolean("pause_sync", false)) }
    var serverUrl by remember {
        mutableStateOf(
                prefs.getString("server_url", "http://192.168.29.2:5000")
                        ?: "http://192.168.29.2:5000"
        )
    }
    var isServiceRunning by remember { mutableStateOf(RelayService.isServiceRunning) }
    var bufferedMessages by remember { mutableStateOf(emptyList<BufferedMessage>()) }

    LaunchedEffect(Unit) { bufferedMessages = MessageBuffer.getMessages(context) }

    LazyColumn(modifier = modifier.fillMaxSize().padding(16.dp)) {
        item {
            Text(
                    "SMS Export Relay",
                    style = androidx.compose.material3.MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(bottom = 16.dp)
            )
        }

        // Group 1: Core Configuration (Server, Test, Listener)
        item {
            SettingsGroup {
                OutlinedTextField(
                        value = serverUrl,
                        onValueChange = {
                            serverUrl = it
                            prefs.edit().putString("server_url", it).apply()
                        },
                        label = { Text("Server URL") },
                        modifier = Modifier.fillMaxWidth()
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                Button(
                        onClick = {
                            val dummySender = "+0000000000"
                            val dummyBody = "Test Request from App Button"
                            val dummyTimestamp = System.currentTimeMillis()

                            kotlin.concurrent.thread(start = true) {
                                val success =
                                        Relay.forwardSms(
                                                context,
                                                dummySender,
                                                dummyBody,
                                                dummyTimestamp
                                        )
                                val message =
                                        if (success) "Test Request Sent Successfully"
                                        else "Test Request Failed"
                                android.os.Handler(android.os.Looper.getMainLooper()).post {
                                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                ) { Text("Send Test Request") }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "Always On Listener")
                        Text(
                                text = if (isServiceRunning) "Running" else "Stopped",
                                style =
                                        androidx.compose.material3.MaterialTheme.typography
                                                .bodySmall,
                                color =
                                        if (isServiceRunning)
                                                androidx.compose.material3.MaterialTheme.colorScheme
                                                        .primary
                                        else
                                                androidx.compose.material3.MaterialTheme.colorScheme
                                                        .error
                        )
                    }
                    Switch(
                            checked = isServiceRunning,
                            onCheckedChange = { isChecked ->
                                val intent = Intent(context, RelayService::class.java)
                                if (isChecked) {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                        context.startForegroundService(intent)
                                    } else {
                                        context.startService(intent)
                                    }
                                    isServiceRunning = true
                                    Toast.makeText(context, "Service Started", Toast.LENGTH_SHORT)
                                            .show()
                                } else {
                                    context.stopService(intent)
                                    isServiceRunning = false
                                    Toast.makeText(context, "Service Stopped", Toast.LENGTH_SHORT)
                                            .show()
                                }
                            }
                    )
                }
                Text(
                        text = "Keeps the app alive in the background to listen for SMS.",
                        style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }

        // Group 2: Toggles (Pause Sync, Wi-Fi Only)
        item {
            SettingsGroup {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "Pause Sync", modifier = Modifier.weight(1f))
                    Switch(
                            checked = pauseSync,
                            onCheckedChange = { isChecked ->
                                pauseSync = isChecked
                                prefs.edit().putBoolean("pause_sync", isChecked).apply()
                            }
                    )
                }
                Text(
                        text =
                                "Temporarily prevent messages from being sent. They will be buffered locally.",
                        style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(top = 4.dp)
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "Sync only via Wi-Fi", modifier = Modifier.weight(1f))
                    Switch(
                            checked = wifiOnly,
                            onCheckedChange = { isChecked ->
                                wifiOnly = isChecked
                                prefs.edit().putBoolean("wifi_only", isChecked).apply()
                            }
                    )
                }
                Text(
                        text = "Only send messages when connected to Wi-Fi to save mobile data.",
                        style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }

        // Group 3: Unsent Messages (header, button, and messages all in one group)
        item {
            SettingsGroup {
                Column {
                    Text("Unsent Messages")
                    Text(
                            "Count: ${bufferedMessages.size}",
                            style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                            color =
                                    androidx.compose.material3.MaterialTheme.colorScheme
                                            .onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                        onClick = {
                            kotlin.concurrent.thread(start = true) {
                                val (sent, failed) = MessageBuffer.retryBufferedMessages(context)
                                val message = "Retry Complete. Sent: $sent, Failed: $failed"
                                android.os.Handler(android.os.Looper.getMainLooper()).post {
                                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                                    bufferedMessages = MessageBuffer.getMessages(context)
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = bufferedMessages.isNotEmpty()
                ) { Text("Retry All") }

                // Messages inside the same group
                if (bufferedMessages.isNotEmpty()) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                    Column {
                        bufferedMessages.forEach { msg ->
                            Card(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    colors =
                                            CardDefaults.cardColors(
                                                    containerColor =
                                                            androidx.compose.material3.MaterialTheme
                                                                    .colorScheme
                                                                    .surfaceVariant.copy(
                                                                    alpha = 0.3f
                                                            )
                                            )
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                            "To: ${msg.sender}",
                                            style =
                                                    androidx.compose.material3.MaterialTheme
                                                            .typography
                                                            .bodyMedium
                                    )
                                    Text(
                                            text =
                                                    java.text.SimpleDateFormat(
                                                                    "HH:mm:ss",
                                                                    java.util.Locale.getDefault()
                                                            )
                                                            .format(java.util.Date(msg.timestamp)),
                                            style =
                                                    androidx.compose.material3.MaterialTheme
                                                            .typography
                                                            .labelSmall,
                                            modifier = Modifier.padding(top = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsGroup(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Card(
            colors =
                    CardDefaults.cardColors(
                            containerColor =
                                    androidx.compose.material3.MaterialTheme.colorScheme
                                            .surfaceVariant.copy(alpha = 0.5f)
                    ),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.fillMaxWidth()
    ) { Column(modifier = Modifier.padding(16.dp), content = content) }
}
