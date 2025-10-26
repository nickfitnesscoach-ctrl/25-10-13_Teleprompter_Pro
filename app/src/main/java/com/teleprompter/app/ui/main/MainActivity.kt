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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
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

    /**
     * Convert markdown-style formatting to AnnotatedString for Compose Text
     * Supports: **bold**, _italic_, __underline__
     */
    private fun convertMarkdownToAnnotatedString(text: String): AnnotatedString {
        return buildAnnotatedString {
            // Process in order: Bold, Underline, Italic (important: __ before _)
            // Find all markers with their positions
            data class Marker(val start: Int, val end: Int, val type: String, val content: String)
            val markers = mutableListOf<Marker>()

            // Find bold **text**
            Regex("""\*\*(.+?)\*\*""").findAll(text).forEach { match ->
                markers.add(Marker(
                    start = match.range.first,
                    end = match.range.last + 1,
                    type = "bold",
                    content = match.groupValues[1]
                ))
            }

            // Find underline __text__
            Regex("""__(.+?)__""").findAll(text).forEach { match ->
                markers.add(Marker(
                    start = match.range.first,
                    end = match.range.last + 1,
                    type = "underline",
                    content = match.groupValues[1]
                ))
            }

            // Find italic _text_ (but not __)
            Regex("""(?<!_)_([^_]+?)_(?!_)""").findAll(text).forEach { match ->
                markers.add(Marker(
                    start = match.range.first,
                    end = match.range.last + 1,
                    type = "italic",
                    content = match.groupValues[1]
                ))
            }

            // Sort markers by start position
            val sortedMarkers = markers.sortedBy { it.start }

            var currentIndex = 0
            for (marker in sortedMarkers) {
                // Add text before marker
                if (marker.start > currentIndex) {
                    append(text.substring(currentIndex, marker.start))
                }

                // Add styled text
                when (marker.type) {
                    "bold" -> {
                        withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                            append(marker.content)
                        }
                    }
                    "italic" -> {
                        withStyle(style = SpanStyle(fontStyle = FontStyle.Italic)) {
                            append(marker.content)
                        }
                    }
                    "underline" -> {
                        withStyle(style = SpanStyle(textDecoration = TextDecoration.Underline)) {
                            append(marker.content)
                        }
                    }
                }

                currentIndex = marker.end
            }

            // Add remaining text
            if (currentIndex < text.length) {
                append(text.substring(currentIndex))
            }
        }
    }

    @Composable
    fun MainScreen() {
        val allScripts = remember { mutableStateOf<List<Script>>(emptyList()) }
        val searchQuery = remember { mutableStateOf("") }
        val showMenu = remember { mutableStateOf(false) }
        val focusManager = LocalFocusManager.current

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
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                ) {
                    // Top bar with search field and menu
                    TopAppBar(
                        title = {
                            TextField(
                                value = searchQuery.value,
                                onValueChange = { searchQuery.value = it },
                                placeholder = {
                                    Text(
                                        "Search",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                    )
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                                    unfocusedIndicatorColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                    cursorColor = MaterialTheme.colorScheme.primary,
                                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                                ),
                                singleLine = true,
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Search,
                                        contentDescription = "Search",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            )
                        },
                        actions = {
                            Box {
                                IconButton(onClick = { showMenu.value = true }) {
                                    Icon(Icons.Default.MoreVert, "Menu")
                                }
                                DropdownMenu(
                                    expanded = showMenu.value,
                                    onDismissRequest = { showMenu.value = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Delete all scripts") },
                                        onClick = {
                                            showMenu.value = false
                                            deleteAllScripts()
                                        }
                                    )
                                }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    )

                    // Scripts title
                    Text(
                        text = "Scripts",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
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
                        .padding(padding)
                        .clickable(
                            indication = null,
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                        ) {
                            focusManager.clearFocus()
                        },
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
                        .padding(padding)
                        .clickable(
                            indication = null,
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                        ) {
                            focusManager.clearFocus()
                        },
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
        // Calculate character count in thousands (K) - strip HTML tags first
        val plainText = android.text.Html.fromHtml(script.content, android.text.Html.FROM_HTML_MODE_LEGACY).toString()
        val charCount = (plainText.length / 1000.0)
        val displayCount = if (charCount < 1) {
            "${plainText.length}"
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
                                text = convertMarkdownToAnnotatedString(script.title),
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
            @Suppress("DEPRECATION")
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

    private fun deleteAllScripts() {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                database.scriptDao().deleteAllScripts()
            }
        }
    }

    override fun onResume() {
        super.onResume()
    }
}
