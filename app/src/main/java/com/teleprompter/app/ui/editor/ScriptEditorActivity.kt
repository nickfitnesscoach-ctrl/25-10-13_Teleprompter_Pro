package com.teleprompter.app.ui.editor

import android.os.Bundle
import android.text.Html
import android.text.Spanned
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
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
                    // Convert HTML back to AnnotatedString
                    val annotatedString = htmlToAnnotatedString(script.content)
                    content = TextFieldValue(annotatedString = annotatedString)
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
                            fontWeight = FontWeight.Bold,
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
                                        content = applyFormatting(content, "bold")
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
                                        content = applyFormatting(content, "italic")
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
                                        content = applyFormatting(content, "underline")
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

                            // Text field with AnnotatedString support
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .border(
                                        width = 1.dp,
                                        color = MaterialTheme.colorScheme.outline,
                                        shape = RoundedCornerShape(4.dp)
                                    )
                                    .padding(12.dp)
                            ) {
                                BasicTextField(
                                    value = content,
                                    onValueChange = { newValue ->
                                        // Check if only selection changed (cursor moved)
                                        if (newValue.text == content.text) {
                                            // Only selection changed - preserve annotatedString
                                            content = newValue.copy(annotatedString = content.annotatedString)
                                        } else {
                                            // Text changed - preserve styles
                                            content = preserveStyles(content, newValue)
                                        }
                                    },
                                    modifier = Modifier.fillMaxSize(),
                                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                                        color = MaterialTheme.colorScheme.onSurface
                                    ),
                                    decorationBox = { innerTextField ->
                                        Box {
                                            if (content.text.isEmpty()) {
                                                Text(
                                                    "Script Content",
                                                    style = MaterialTheme.typography.bodyLarge,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                                )
                                            }
                                            innerTextField()
                                        }
                                    }
                                )
                            }
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
                            onClick = {
                                val htmlContent = annotatedStringToHtml(content.annotatedString)
                                saveScript(title, htmlContent)
                            },
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
     * Preserve existing styles when text changes
     */
    private fun preserveStyles(oldValue: TextFieldValue, newValue: TextFieldValue): TextFieldValue {
        // If text length didn't change significantly, preserve all styles
        if (oldValue.text == newValue.text) {
            return newValue
        }

        val oldAnnotated = oldValue.annotatedString
        val newText = newValue.text

        // If no styles exist, return as is
        if (oldAnnotated.spanStyles.isEmpty()) {
            return newValue
        }

        // Build new annotated string with preserved styles
        val newAnnotated = buildAnnotatedString {
            append(newText)

            // Copy all existing styles, adjusting positions
            oldAnnotated.spanStyles.forEach { span ->
                try {
                    // Keep style if it's still within text bounds
                    val adjustedStart = minOf(span.start, newText.length)
                    val adjustedEnd = minOf(span.end, newText.length)

                    if (adjustedStart < adjustedEnd) {
                        addStyle(span.item, adjustedStart, adjustedEnd)
                    }
                } catch (_: Exception) {
                    // Skip invalid spans
                }
            }
        }

        return newValue.copy(annotatedString = newAnnotated)
    }

    /**
     * Apply/Remove visual formatting (Bold/Italic/Underline) to selected text using AnnotatedString
     * Toggle logic: if format exists, remove it; if not, add it
     * Supports combining multiple formats (bold + italic + underline)
     */
    private fun applyFormatting(textFieldValue: TextFieldValue, formatType: String): TextFieldValue {
        val selection = textFieldValue.selection

        // If no text is selected, return unchanged
        if (selection.start == selection.end) {
            return textFieldValue
        }

        val originalAnnotated = textFieldValue.annotatedString

        // Check if the selected text already has this specific format
        val hasFormat = hasFormatInRange(originalAnnotated, selection.start, selection.end, formatType)

        // Build new AnnotatedString with toggle logic
        val newAnnotatedString = buildAnnotatedString {
            append(originalAnnotated.text)

            // Copy all existing styles, but filter out the toggled format in selected range
            originalAnnotated.spanStyles.forEach { span ->
                val isTargetFormat = when (formatType) {
                    "bold" -> span.item.fontWeight == FontWeight.Bold
                    "italic" -> span.item.fontStyle == FontStyle.Italic
                    "underline" -> span.item.textDecoration == TextDecoration.Underline
                    else -> false
                }

                // Check if this span overlaps with selection
                val overlapsSelection = span.start < selection.end && span.end > selection.start

                // If toggling OFF: skip this format in selection range
                // If toggling ON: keep all existing formats
                if (hasFormat && isTargetFormat && overlapsSelection) {
                    // Split the span to exclude the selected range
                    if (span.start < selection.start) {
                        // Keep part before selection
                        addStyle(span.item, span.start, minOf(selection.start, span.end))
                    }
                    if (span.end > selection.end) {
                        // Keep part after selection
                        addStyle(span.item, maxOf(selection.end, span.start), span.end)
                    }
                } else {
                    // Keep all other styles as-is
                    addStyle(span.item, span.start, span.end)
                }
            }

            // If toggling ON, add the new format
            if (!hasFormat) {
                val newStyle = when (formatType) {
                    "bold" -> SpanStyle(fontWeight = FontWeight.Bold)
                    "italic" -> SpanStyle(fontStyle = FontStyle.Italic)
                    "underline" -> SpanStyle(textDecoration = TextDecoration.Underline)
                    else -> return@buildAnnotatedString
                }

                addStyle(
                    style = newStyle,
                    start = selection.start,
                    end = selection.end
                )
            }
        }

        return TextFieldValue(
            annotatedString = newAnnotatedString,
            selection = selection  // Preserve selection
        )
    }

    /**
     * Check if the specified range has the given format applied
     */
    private fun hasFormatInRange(
        annotatedString: AnnotatedString,
        start: Int,
        end: Int,
        formatType: String
    ): Boolean {
        // Check if any span of the specified format type covers the selection
        return annotatedString.spanStyles.any { span ->
            val isTargetFormat = when (formatType) {
                "bold" -> span.item.fontWeight == FontWeight.Bold
                "italic" -> span.item.fontStyle == FontStyle.Italic
                "underline" -> span.item.textDecoration == TextDecoration.Underline
                else -> false
            }

            // Check if this span overlaps with the selection range
            isTargetFormat && span.start < end && span.end > start
        }
    }

    /**
     * Convert AnnotatedString to HTML for database storage
     */
    private fun annotatedStringToHtml(annotatedString: AnnotatedString): String {
        val text = annotatedString.text
        if (annotatedString.spanStyles.isEmpty()) {
            return text
        }

        val htmlBuilder = StringBuilder()
        var currentIndex = 0

        // Sort spans by start position
        val sortedSpans = annotatedString.spanStyles.sortedBy { it.start }

        for (span in sortedSpans) {
            // Add text before span
            if (span.start > currentIndex) {
                htmlBuilder.append(text.substring(currentIndex, span.start))
            }

            // Open tags
            val style = span.item
            var openTags = ""
            var closeTags = ""

            if (style.fontWeight == FontWeight.Bold) {
                openTags += "<b>"
                closeTags = "</b>$closeTags"
            }
            if (style.fontStyle == FontStyle.Italic) {
                openTags += "<i>"
                closeTags = "</i>$closeTags"
            }
            if (style.textDecoration == TextDecoration.Underline) {
                openTags += "<u>"
                closeTags = "</u>$closeTags"
            }

            htmlBuilder.append(openTags)
            htmlBuilder.append(text.substring(span.start, span.end))
            htmlBuilder.append(closeTags)

            currentIndex = span.end
        }

        // Add remaining text
        if (currentIndex < text.length) {
            htmlBuilder.append(text.substring(currentIndex))
        }

        return htmlBuilder.toString()
    }

    /**
     * Convert HTML from database back to AnnotatedString
     */
    private fun htmlToAnnotatedString(html: String): AnnotatedString {
        if (html.isEmpty()) {
            return AnnotatedString("")
        }

        // Parse HTML to Spanned
        val spanned: Spanned = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY)
        } else {
            @Suppress("DEPRECATION")
            Html.fromHtml(html)
        }

        // Convert Spanned to AnnotatedString
        return buildAnnotatedString {
            append(spanned.toString())

            // Apply styles from Spanned
            spanned.getSpans(0, spanned.length, Any::class.java).forEach { span ->
                val start = spanned.getSpanStart(span)
                val end = spanned.getSpanEnd(span)

                when (span) {
                    is StyleSpan -> {
                        when (span.style) {
                            android.graphics.Typeface.BOLD -> {
                                addStyle(SpanStyle(fontWeight = FontWeight.Bold), start, end)
                            }
                            android.graphics.Typeface.ITALIC -> {
                                addStyle(SpanStyle(fontStyle = FontStyle.Italic), start, end)
                            }
                            android.graphics.Typeface.BOLD_ITALIC -> {
                                addStyle(SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic), start, end)
                            }
                        }
                    }
                    is UnderlineSpan -> {
                        addStyle(SpanStyle(textDecoration = TextDecoration.Underline), start, end)
                    }
                }
            }
        }
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
