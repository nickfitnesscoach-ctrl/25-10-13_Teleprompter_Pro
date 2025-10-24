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
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.teleprompter.app.R
import com.teleprompter.app.core.PermissionsManager
import com.teleprompter.app.data.db.AppDatabase
import com.teleprompter.app.data.models.Script
import com.teleprompter.app.ui.editor.ScriptEditorActivity
import com.teleprompter.app.ui.overlay.TeleprompterOverlayService
import com.teleprompter.app.ui.theme.TelePrompterTheme
import com.teleprompter.app.utils.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.ceil

/**
 * Main activity with script list - redesigned UI
 */
@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {

    private lateinit var permissionsManager: PermissionsManager
    private lateinit var database: AppDatabase

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

        // Apply zoom-in animation when returning from overlay (reverse of minimize)
        if (intent.hasExtra("FROM_OVERLAY")) {
            @Suppress("DEPRECATION")
            overridePendingTransition(R.anim.zoom_in_reverse, R.anim.zoom_out_reverse)
        }

        permissionsManager = PermissionsManager(this)
        database = AppDatabase.getDatabase(this)

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
            TelePrompterTheme {
                MainScreen()
            }
        }
    }

    @Composable
    fun MainScreen() {
        val allScripts = remember { mutableStateOf<List<Script>>(emptyList()) }
        val searchQuery = remember { mutableStateOf("") }
        val isSearchActive = remember { mutableStateOf(false) }

        // Collect scripts from database
        LaunchedEffect(Unit) {
            database.scriptDao().getAllScripts()
                .flowOn(Dispatchers.IO)
                .collectLatest {
                    allScripts.value = it
                }
        }

        // Filter scripts based on search query
        val filteredScripts = remember(allScripts.value, searchQuery.value) {
            if (searchQuery.value.isBlank()) {
                allScripts.value
            } else {
                allScripts.value.filter { script ->
                    script.title.contains(searchQuery.value, ignoreCase = true) ||
                            script.content.contains(searchQuery.value, ignoreCase = true)
                }
            }
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        if (isSearchActive.value) {
                            TextField(
                                value = searchQuery.value,
                                onValueChange = { searchQuery.value = it },
                                placeholder = { Text("Search") },
                                modifier = Modifier.fillMaxWidth(),
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent
                                ),
                                singleLine = true
                            )
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Scripts")
                            }
                        }
                    },
                    navigationIcon = {
                        if (isSearchActive.value) {
                            IconButton(onClick = {
                                isSearchActive.value = false
                                searchQuery.value = ""
                            }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Close search")
                            }
                        }
                    },
                    actions = {
                        if (!isSearchActive.value) {
                            IconButton(onClick = { isSearchActive.value = true }) {
                                Icon(Icons.Default.Search, "Search")
                            }
                        }
                        IconButton(onClick = { /* TODO: Menu */ }) {
                            Icon(Icons.Default.MoreVert, "Menu")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = { openScriptEditor(null) },
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(Icons.Default.Add, "Add script", tint = Color.White)
                }
            },
            containerColor = MaterialTheme.colorScheme.background
        ) { padding ->
            if (filteredScripts.isEmpty()) {
                // Empty state
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (searchQuery.value.isBlank()) {
                            "No scripts yet.\nTap + to create one."
                        } else {
                            "No scripts found"
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredScripts, key = { it.id }) { script ->
                        ScriptCard(
                            script = script,
                            onClick = { startOverlayService(script) },
                            onEdit = { openScriptEditor(script) },
                            onDelete = { deleteScript(script) }
                        )
                    }
                }
            }
        }
    }

    @OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
    @Composable
    fun ScriptCard(
        script: Script,
        onClick: () -> Unit,
        onEdit: () -> Unit,
        onDelete: () -> Unit
    ) {
        // Calculate character count in thousands (K)
        val charCount = (script.content.length / 1000.0)
        val displayCount = if (charCount < 1) {
            "${script.content.length}"
        } else {
            "${ceil(charCount).toInt()}K"
        }

        val dismissState = rememberSwipeToDismissBoxState(
            confirmValueChange = {
                if (it == SwipeToDismissBoxValue.EndToStart) {
                    onDelete()
                    true
                } else {
                    false
                }
            }
        )

        SwipeToDismissBox(
            state = dismissState,
            backgroundContent = {
                val color by animateColorAsState(
                    if (dismissState.targetValue == SwipeToDismissBoxValue.EndToStart) {
                        MaterialTheme.colorScheme.error
                    } else {
                        Color.Transparent
                    }, label = "background"
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(color, RoundedCornerShape(12.dp))
                        .padding(horizontal = 20.dp),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    if (dismissState.targetValue == SwipeToDismissBoxValue.EndToStart) {
                        Text(
                            "Delete",
                            color = Color.White,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            },
            content = {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onClick() },
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        // Left side - Title and time
                        Column(
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = script.title,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = formatScriptTime(script.content),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        // Edit icon
                        IconButton(
                            onClick = onEdit,
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = "Edit",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        // Character count badge
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = displayCount,
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        )
    }

    /**
     * Format estimated reading time based on content length
     * Assuming ~200 words per minute reading speed
     */
    private fun formatScriptTime(content: String): String {
        val wordCount = content.split("\\s+".toRegex()).size
        val minutes = wordCount / 200
        val seconds = ((wordCount % 200) / 200.0 * 60).toInt()

        return if (minutes > 0) {
            "[$minutes:${seconds.toString().padStart(2, '0')}]"
        } else {
            "[0:${seconds.toString().padStart(2, '0')}]"
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
            putExtra(Constants.EXTRA_SCRIPT_ID, script.id)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }

        moveTaskToBack(true)
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
    }
}
