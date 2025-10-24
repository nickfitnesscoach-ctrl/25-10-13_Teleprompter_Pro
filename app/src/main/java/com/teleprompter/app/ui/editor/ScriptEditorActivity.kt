package com.teleprompter.app.ui.editor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.teleprompter.app.data.db.AppDatabase
import com.teleprompter.app.data.models.Script
import com.teleprompter.app.ui.theme.TelePrompterTheme
import com.teleprompter.app.utils.Constants
import kotlinx.coroutines.launch

/**
 * Activity for creating and editing scripts - redesigned UI
 */
@OptIn(ExperimentalMaterial3Api::class)
class ScriptEditorActivity : ComponentActivity() {

    private lateinit var database: AppDatabase
    private var scriptId: Long? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        database = AppDatabase.getDatabase(this)
        scriptId = intent.getLongExtra(Constants.EXTRA_SCRIPT_ID, -1L).takeIf { it != -1L }

        setContent {
            TelePrompterTheme {
                ScriptEditorScreen()
            }
        }
    }

    @Composable
    fun ScriptEditorScreen() {
        var title by remember { mutableStateOf("") }
        var content by remember { mutableStateOf(TextFieldValue("")) }
        var isLoading by remember { mutableStateOf(true) }

        // Load existing script if editing
        LaunchedEffect(scriptId) {
            scriptId?.let { id ->
                withContext(Dispatchers.IO) {
                    database.scriptDao().getScriptById(id)
                }?.let { script ->
                    title = script.title
                    content = TextFieldValue(script.content)
                }
            }
            isLoading = false
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = if (scriptId == null) "New Script" else "Edit Script",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { finish() }) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            },
            containerColor = MaterialTheme.colorScheme.background
        ) { padding ->
            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = androidx.compose.ui.Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Title input card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        OutlinedTextField(
                            value = title,
                            onValueChange = { title = it },
                            label = { Text("Script Title") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline
                            )
                        )
                    }

                    // Content input card with formatting toolbar
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp)
                        ) {
                            // Formatting toolbar
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Bold button
                                TextButton(
                                    onClick = {
                                        content = applyFormatting(content, "**")
                                    },
                                    colors = ButtonDefaults.textButtonColors(
                                        contentColor = MaterialTheme.colorScheme.primary
                                    )
                                ) {
                                    Text("B", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                }

                                // Italic button
                                TextButton(
                                    onClick = {
                                        content = applyFormatting(content, "_")
                                    },
                                    colors = ButtonDefaults.textButtonColors(
                                        contentColor = MaterialTheme.colorScheme.primary
                                    )
                                ) {
                                    Text("I", fontStyle = FontStyle.Italic, fontSize = 18.sp)
                                }

                                // Underline button
                                TextButton(
                                    onClick = {
                                        content = applyFormatting(content, "__")
                                    },
                                    colors = ButtonDefaults.textButtonColors(
                                        contentColor = MaterialTheme.colorScheme.primary
                                    )
                                ) {
                                    Text("U", textDecoration = TextDecoration.Underline, fontSize = 18.sp)
                                }
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))

                            Spacer(modifier = Modifier.height(8.dp))

                            // Text field
                            OutlinedTextField(
                                value = content,
                                onValueChange = { content = it },
                                label = { Text("Script Content") },
                                modifier = Modifier
                                    .fillMaxSize(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                                )
                            )
                        }
                    }

                    // Action buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = { finish() },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.onSurface
                            )
                        ) {
                            Text("Cancel")
                        }
                        Button(
                            onClick = { saveScript(title, content.text) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            enabled = title.isNotBlank() && content.text.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = Color.White
                            )
                        ) {
                            Text("Save")
                        }
                    }
                }
            }
        }
    }

    /**
     * Apply markdown-style formatting to selected text or insert formatting markers at cursor
     * Toggle functionality: adds or removes specific marker, preserving other formatting
     */
    private fun applyFormatting(textFieldValue: TextFieldValue, marker: String): TextFieldValue {
        val text = textFieldValue.text
        val selection = textFieldValue.selection

        return if (selection.start != selection.end) {
            // Text is selected
            val selectedText = text.substring(selection.start, selection.end)
            val beforeText = text.substring(0, selection.start)
            val afterText = text.substring(selection.end)

            // Strategy 1: Check if marker exists just outside selection
            val hasMarkerBefore = beforeText.endsWith(marker)
            val hasMarkerAfter = afterText.startsWith(marker)

            if (hasMarkerBefore && hasMarkerAfter) {
                // Remove markers from outside
                val newBeforeText = beforeText.substring(0, beforeText.length - marker.length)
                val newAfterText = afterText.substring(marker.length)

                val newText = newBeforeText + selectedText + newAfterText
                TextFieldValue(
                    text = newText,
                    selection = TextRange(
                        selection.start - marker.length,
                        selection.end - marker.length
                    )
                )
            } else {
                // Strategy 2: Check if marker exists inside the selection (at edges)
                val markerInsideStart = selectedText.startsWith(marker)
                val markerInsideEnd = selectedText.endsWith(marker)

                if (markerInsideStart && markerInsideEnd && selectedText.length > marker.length * 2) {
                    // Remove markers from inside selection
                    val newSelectedText = selectedText.substring(marker.length, selectedText.length - marker.length)

                    val newText = beforeText + newSelectedText + afterText
                    TextFieldValue(
                        text = newText,
                        selection = TextRange(
                            selection.start,
                            selection.start + newSelectedText.length
                        )
                    )
                } else {
                    // Strategy 3: Search for markers around current position with some tolerance
                    val searchBefore = if (selection.start >= marker.length * 3) {
                        text.substring(selection.start - marker.length * 3, selection.start)
                    } else {
                        text.substring(0, selection.start)
                    }

                    val searchAfter = if (selection.end + marker.length * 3 <= text.length) {
                        text.substring(selection.end, selection.end + marker.length * 3)
                    } else {
                        text.substring(selection.end)
                    }

                    val foundBefore = searchBefore.lastIndexOf(marker)
                    val foundAfter = searchAfter.indexOf(marker)

                    if (foundBefore != -1 && foundAfter != -1) {
                        // Found markers around selection - remove them
                        val actualBeforeIndex = selection.start - searchBefore.length + foundBefore
                        val actualAfterIndex = selection.end + foundAfter

                        val newText = text.substring(0, actualBeforeIndex) +
                                text.substring(actualBeforeIndex + marker.length, actualAfterIndex) +
                                text.substring(actualAfterIndex + marker.length)

                        TextFieldValue(
                            text = newText,
                            selection = TextRange(
                                actualBeforeIndex,
                                actualAfterIndex - marker.length
                            )
                        )
                    } else {
                        // No markers found - add them
                        val newText = beforeText + marker + selectedText + marker + afterText
                        TextFieldValue(
                            text = newText,
                            selection = TextRange(
                                selection.start + marker.length,
                                selection.end + marker.length
                            )
                        )
                    }
                }
            }
        } else {
            // No selection - insert markers at cursor position
            val newText = text.substring(0, selection.start) +
                    marker + marker +
                    text.substring(selection.start)

            TextFieldValue(
                text = newText,
                selection = TextRange(selection.start + marker.length)
            )
        }
    }

    /**
     * Convert markdown-style formatting to HTML for display
     */
    private fun convertMarkdownToHtml(text: String): String {
        var html = text

        // Bold: **text** -> <b>text</b>
        html = html.replace(Regex("""\*\*([^*]+?)\*\*"""), "<b>$1</b>")

        // Underline: __text__ -> <u>text</u> (process before single _)
        html = html.replace(Regex("""__([^_]+?)__"""), "<u>$1</u>")

        // Italic: _text_ -> <i>text</i> (single _ only)
        html = html.replace(Regex("""_([^_]+?)_"""), "<i>$1</i>")

        return html
    }

    private fun saveScript(title: String, content: String) {
        if (title.isBlank() || content.isBlank()) return

        lifecycleScope.launch {
            val script = Script(
                id = scriptId ?: 0,
                title = title,
                content = content,
                updatedAt = System.currentTimeMillis()
            )

            withContext(Dispatchers.IO) {
                if (scriptId == null) {
                    database.scriptDao().insertScript(script)
                } else {
                    database.scriptDao().updateScript(script)
                }
            }

            finish()
        }
    }
}
