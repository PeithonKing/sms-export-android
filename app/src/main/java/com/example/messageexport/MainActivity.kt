package com.example.messageexport

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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

@OptIn(ExperimentalFoundationApi::class)
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
    var isServiceRunning by remember {
        mutableStateOf(RelayService.isServiceRunning || prefs.getBoolean("service_enabled", false))
    }

    // Ensure service matches state on launch if needed (e.g. if killed and restarted)
    LaunchedEffect(Unit) {
        if (prefs.getBoolean("service_enabled", false) && !RelayService.isServiceRunning) {
            val intent = Intent(context, RelayService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            RelayService.isServiceRunning = true
        }
    }
    var bufferedMessages by remember { mutableStateOf(emptyList<BufferedMessage>()) }

    // Multi-selection state
    var selectedIds by remember { mutableStateOf(emptySet<String>()) }
    var selectionMode by remember { mutableStateOf(false) }
    var showDiscardConfirmation by remember { mutableStateOf(false) }
    var showSendConfirmation by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { bufferedMessages = MessageBuffer.getMessages(context) }

    DisposableEffect(Unit) {
        val receiver =
                object : android.content.BroadcastReceiver() {
                    override fun onReceive(
                            context: android.content.Context?,
                            intent: android.content.Intent?
                    ) {
                        context?.let { ctx -> bufferedMessages = MessageBuffer.getMessages(ctx) }
                    }
                }
        val filter = android.content.IntentFilter("com.example.messageexport.BUFFER_UPDATED")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(
                    receiver,
                    filter,
                    android.content.Context.RECEIVER_NOT_EXPORTED
            )
        } else {
            context.registerReceiver(receiver, filter)
        }
        onDispose { context.unregisterReceiver(receiver) }
    }

    // Handle Back Press
    BackHandler(enabled = selectionMode) {
        selectionMode = false
        selectedIds = emptySet()
    }

    // Confirmation Dialogs
    if (showDiscardConfirmation) {
        AlertDialog(
                onDismissRequest = { showDiscardConfirmation = false },
                title = { Text("Discard Messages?") },
                text = {
                    Text("Do you really want to discard the ${selectedIds.size} selected items?")
                },
                confirmButton = {
                    TextButton(
                            onClick = {
                                val idsToDelete = selectedIds // Capture current set
                                if (idsToDelete.isNotEmpty()) {
                                    MessageBuffer.removeMessages(context, idsToDelete)
                                    bufferedMessages = MessageBuffer.getMessages(context)
                                    selectedIds = emptySet()
                                    selectionMode = false // Exit mode after action
                                    Toast.makeText(
                                                    context,
                                                    "${idsToDelete.size} messages discarded",
                                                    Toast.LENGTH_SHORT
                                            )
                                            .show()
                                }
                                showDiscardConfirmation = false
                            }
                    ) { Text("Discard") }
                },
                dismissButton = {
                    TextButton(onClick = { showDiscardConfirmation = false }) { Text("Cancel") }
                }
        )
    }

    if (showSendConfirmation) {
        AlertDialog(
                onDismissRequest = { showSendConfirmation = false },
                title = { Text("Send Messages?") },
                text = {
                    Text("Do you really want to send the ${selectedIds.size} selected items?")
                },
                confirmButton = {
                    TextButton(
                            onClick = {
                                val idsToSend = selectedIds // Capture current set
                                kotlin.concurrent.thread(start = true) {
                                    val (sent, failed) =
                                            MessageBuffer.retryMessages(context, idsToSend)
                                    val message =
                                            "Selected Retry Complete. Sent: $sent, Failed: $failed"
                                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                                        bufferedMessages = MessageBuffer.getMessages(context)
                                        selectedIds = emptySet()
                                        selectionMode = false // Exit selection mode
                                    }
                                }
                                showSendConfirmation = false
                            }
                    ) { Text("Send") }
                },
                dismissButton = {
                    TextButton(onClick = { showSendConfirmation = false }) { Text("Cancel") }
                }
        )
    }

    LazyColumn(modifier = modifier.fillMaxSize().padding(16.dp)) {
        item {
            Text(
                    "SMS Export Relay",
                    style = MaterialTheme.typography.headlineMedium,
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
                                style = MaterialTheme.typography.bodySmall,
                                color =
                                        if (isServiceRunning) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.error
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
                                    prefs.edit().putBoolean("service_enabled", true).apply()
                                    Toast.makeText(context, "Service Started", Toast.LENGTH_SHORT)
                                            .show()
                                } else {
                                    context.stopService(intent)
                                    isServiceRunning = false
                                    prefs.edit().putBoolean("service_enabled", false).apply()
                                    Toast.makeText(context, "Service Stopped", Toast.LENGTH_SHORT)
                                            .show()
                                }
                            }
                    )
                }
                Text(
                        text = "Keeps the app alive in the background to listen for SMS.",
                        style = MaterialTheme.typography.labelSmall,
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
                        style = MaterialTheme.typography.labelSmall,
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
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }

        // Group 3: Unsent Messages
        item {
            SettingsGroup {
                if (selectionMode) {
                    // Contextual Top Bar
                    Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                    checked =
                                            selectedIds.size == bufferedMessages.size &&
                                                    bufferedMessages.isNotEmpty(),
                                    onCheckedChange = { isChecked ->
                                        selectedIds =
                                                if (isChecked) {
                                                    bufferedMessages.map { it.id }.toSet()
                                                } else {
                                                    emptySet()
                                                }
                                    }
                            )
                            Text(
                                    text = "${selectedIds.size} selected",
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.padding(start = 8.dp)
                            )
                        }

                        Row {
                            // Send
                            IconButton(onClick = { showSendConfirmation = true }) {
                                Icon(
                                        imageVector =
                                                androidx.compose.material.icons.Icons.AutoMirrored
                                                        .Filled.Send,
                                        contentDescription = "Send Selected"
                                )
                            }
                            // Discard
                            IconButton(onClick = { showDiscardConfirmation = true }) {
                                Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Discard Selected"
                                )
                            }
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                } else {
                    // Normal Header
                    Column {
                        Text("Unsent Messages")
                        Text(
                                "Count: ${bufferedMessages.size}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                            onClick = {
                                kotlin.concurrent.thread(start = true) {
                                    val (sent, failed) =
                                            MessageBuffer.retryBufferedMessages(context)
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
                }

                // Messages List
                if (bufferedMessages.isNotEmpty()) {
                    if (!selectionMode)
                            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                    Column {
                        bufferedMessages.forEach { msg ->
                            val isSelected = selectedIds.contains(msg.id)
                            Card(
                                    modifier =
                                            Modifier.fillMaxWidth()
                                                    .padding(vertical = 4.dp)
                                                    .combinedClickable(
                                                            onClick = {
                                                                if (selectionMode) {
                                                                    selectedIds =
                                                                            if (isSelected)
                                                                                    selectedIds -
                                                                                            msg.id
                                                                            else
                                                                                    selectedIds +
                                                                                            msg.id
                                                                }
                                                            },
                                                            onLongClick = {
                                                                if (!selectionMode) {
                                                                    selectionMode = true
                                                                    selectedIds = setOf(msg.id)
                                                                }
                                                            }
                                                    ),
                                    colors =
                                            CardDefaults.cardColors(
                                                    containerColor =
                                                            if (isSelected)
                                                                    MaterialTheme.colorScheme
                                                                            .primaryContainer
                                                            else
                                                                    MaterialTheme.colorScheme
                                                                            .surfaceVariant.copy(
                                                                            alpha = 0.3f
                                                                    )
                                            )
                            ) {
                                Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(12.dp)
                                ) {
                                    if (selectionMode) {
                                        Checkbox(
                                                checked = isSelected,
                                                onCheckedChange = null, // Handled by card click
                                                modifier = Modifier.padding(end = 12.dp)
                                        )
                                    }

                                    Column(modifier = Modifier.weight(1f)) {
                                        // Primary Line: Sender and Date
                                        Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                    text = "To: ${msg.sender}",
                                                    style = MaterialTheme.typography.bodyLarge,
                                                    fontWeight =
                                                            androidx.compose.ui.text.font.FontWeight
                                                                    .Medium,
                                                    maxLines = 1,
                                                    overflow =
                                                            androidx.compose.ui.text.style
                                                                    .TextOverflow.Ellipsis,
                                                    modifier =
                                                            Modifier.weight(1f, fill = false)
                                                                    .padding(end = 8.dp)
                                            )
                                            Text(
                                                    text =
                                                            java.text.SimpleDateFormat(
                                                                            "MMM dd",
                                                                            java.util.Locale
                                                                                    .getDefault()
                                                                    )
                                                                    .format(
                                                                            java.util.Date(
                                                                                    msg.timestamp
                                                                            )
                                                                    ),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color =
                                                            MaterialTheme.colorScheme
                                                                    .onSurfaceVariant
                                            )
                                        }

                                        // Secondary Line: Message Body Preview
                                        Text(
                                                text = msg.body,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow =
                                                        androidx.compose.ui.text.style.TextOverflow
                                                                .Ellipsis,
                                                modifier = Modifier.padding(top = 2.dp)
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
}

@Composable
fun SettingsGroup(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Card(
            colors =
                    CardDefaults.cardColors(
                            containerColor =
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.fillMaxWidth()
    ) { Column(modifier = Modifier.padding(16.dp), content = content) }
}
