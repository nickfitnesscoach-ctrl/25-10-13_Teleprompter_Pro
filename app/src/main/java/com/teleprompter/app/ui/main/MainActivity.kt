package com.teleprompter.app.ui.main

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.teleprompter.app.core.PermissionsManager
import com.teleprompter.app.data.db.AppDatabase
import com.teleprompter.app.data.models.Script
import com.teleprompter.app.ui.editor.ScriptEditorActivity
import com.teleprompter.app.ui.overlay.TeleprompterOverlayService
import com.teleprompter.app.utils.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Main activity with script list and overlay control
 */
@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {

    private lateinit var permissionsManager: PermissionsManager
    private lateinit var database: AppDatabase

    // Track permission state for UI updates
    private val hasPermission = mutableStateOf(false)

    // Permission launcher for POST_NOTIFICATIONS (Android 13+)
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            Toast.makeText(
                this,
                "Notification permission is required for teleprompter overlay",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        permissionsManager = PermissionsManager(this)
        database = AppDatabase.getDatabase(this)

        // Initialize permission state
        hasPermission.value = permissionsManager.hasOverlayPermission()

        // Request notification permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        setContent {
            MaterialTheme {
                MainScreen()
            }
        }
    }

    @Composable
    fun MainScreen() {
        val scripts = remember { mutableStateOf<List<Script>>(emptyList()) }
        val scriptToDelete = remember { mutableStateOf<Script?>(null) }

        // Collect scripts from database
        LaunchedEffect(Unit) {
            database.scriptDao().getAllScripts()
                .flowOn(Dispatchers.IO)
                .collectLatest {
                    scripts.value = it
                }
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("TelePrompt One Pro") },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
            },
            floatingActionButton = {
                FloatingActionButton(onClick = { openScriptEditor(null) }) {
                    Text("+")
                }
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp)
            ) {
                // Permission status card
                if (!hasPermission.value) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                "Overlay Permission Required",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Grant permission to display teleprompter overlay",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(onClick = {
                                startActivity(permissionsManager.createOverlayPermissionIntent())
                            }) {
                                Text("Grant Permission")
                            }
                        }
                    }
                }

                // Scripts list
                if (scripts.value.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "No scripts yet. Tap + to create one.",
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(scripts.value) { script ->
                            ScriptCard(
                                script = script,
                                onPlay = { startOverlayService(script) },
                                onEdit = { openScriptEditor(script) },
                                onDelete = { scriptToDelete.value = script }
                            )
                        }
                    }
                }
            }
        }

        // Delete confirmation dialog
        scriptToDelete.value?.let { script ->
            AlertDialog(
                onDismissRequest = { scriptToDelete.value = null },
                title = { Text("Delete Script?") },
                text = { Text("Are you sure you want to delete \"${script.title}\"? This action cannot be undone.") },
                confirmButton = {
                    Button(
                        onClick = {
                            deleteScript(script)
                            scriptToDelete.value = null
                        }
                    ) {
                        Text("Delete")
                    }
                },
                dismissButton = {
                    OutlinedButton(onClick = { scriptToDelete.value = null }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }

    @Composable
    fun ScriptCard(
        script: Script,
        onPlay: () -> Unit,
        onEdit: () -> Unit,
        onDelete: () -> Unit
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = script.title,
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = script.content.take(100) + if (script.content.length > 100) "..." else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(onClick = onPlay) {
                        Text("Show Overlay")
                    }
                    OutlinedButton(onClick = onEdit) {
                        Text("Edit")
                    }
                    OutlinedButton(onClick = onDelete) {
                        Text("Delete")
                    }
                }
            }
        }
    }

    private fun startOverlayService(script: Script) {
        // Check overlay permission
        if (!permissionsManager.hasOverlayPermission()) {
            startActivity(permissionsManager.createOverlayPermissionIntent())
            return
        }

        // Check notification permission for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                Toast.makeText(
                    this,
                    "Please grant notification permission to use overlay",
                    Toast.LENGTH_LONG
                ).show()
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }

        val intent = Intent(this, TeleprompterOverlayService::class.java).apply {
            putExtra(Constants.EXTRA_SCRIPT_CONTENT, script.content)
        }

        // Use startForegroundService for Android 8.0+ (API 26+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun openScriptEditor(script: Script?) {
        val intent = Intent(this, ScriptEditorActivity::class.java).apply {
            script?.let { putExtra(Constants.EXTRA_SCRIPT_ID, it.id) }
        }
        startActivity(intent)
    }

    private fun deleteScript(script: Script) {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                database.scriptDao().deleteScript(script)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Refresh permission status when returning from Settings
        hasPermission.value = permissionsManager.hasOverlayPermission()
    }
}
