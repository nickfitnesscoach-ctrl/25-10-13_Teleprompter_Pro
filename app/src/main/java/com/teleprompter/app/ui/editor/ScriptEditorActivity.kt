package com.teleprompter.app.ui.editor

import android.os.Bundle
import android.text.Html
import android.text.Spanned
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import java.util.regex.Pattern
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
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.teleprompter.app.R
import com.teleprompter.app.data.db.AppDatabase
import com.teleprompter.app.data.models.Script
import com.teleprompter.app.data.preferences.OverlayPreferences
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
        var showFontSelector by remember { mutableStateOf(false) }

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

                                // Font selector button
                                TextButton(
                                    onClick = {
                                        showFontSelector = true
                                    },
                                    colors = ButtonDefaults.textButtonColors(
                                        contentColor = MaterialTheme.colorScheme.primary
                                    )
                                ) {
                                    Text("T", fontSize = 18.sp)
                                }
                            }

                            // Font selector dialog
                            if (showFontSelector) {
                                FontSelectorDialog(
                                    onDismiss = { showFontSelector = false },
                                    onFontSelected = { fontName, fontFamily ->
                                        content = applyFontFamily(content, fontFamily)
                                        showFontSelector = false

                                        // Save font family to preferences for overlay
                                        saveFontFamilyToPreferences(fontName)
                                    }
                                )
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
     * Font Selector Dialog
     */
    @Composable
    fun FontSelectorDialog(
        onDismiss: () -> Unit,
        onFontSelected: (String, FontFamily) -> Unit
    ) {
        var searchQuery by remember { mutableStateOf("") }
        var selectedFont by remember { mutableStateOf<Pair<String, FontFamily>?>(null) }

        // Available fonts list (only fonts with Cyrillic support)
        val availableFonts = remember {
            listOf(
                "Default" to FontFamily.Default,
                "Bebas Neue" to FontFamily(Font(R.font.bebas_neue)),
                "Comfortaa" to FontFamily(Font(R.font.comfortaa_regular)),
                "Druk Cyr Bold" to FontFamily(Font(R.font.drukcyr_bold)),
                "Montserrat" to FontFamily(Font(R.font.montserrat_regular)),
                "Open Sans" to FontFamily(Font(R.font.opensans_regular)),
                "Oswald" to FontFamily(Font(R.font.oswald_regular)),
                "PT Sans" to FontFamily(Font(R.font.ptsans_regular)),
                "Raleway" to FontFamily(Font(R.font.raleway_regular)),
                "Roboto" to FontFamily(Font(R.font.roboto_regular)),
                "Ubuntu" to FontFamily(Font(R.font.ubuntu_regular))
            )
        }

        // Filter fonts based on search
        val filteredFonts = remember(searchQuery) {
            if (searchQuery.isEmpty()) {
                availableFonts
            } else {
                availableFonts.filter {
                    it.first.contains(searchQuery, ignoreCase = true)
                }
            }
        }

        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .fillMaxHeight(0.7f),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    // Title
                    Text(
                        text = "Select Font",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    // Search bar
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        placeholder = { Text("Search fonts...") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        )
                    )

                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    // Font list
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(filteredFonts) { font ->
                            val isSelected = selectedFont == font
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedFont = font },
                                shape = RoundedCornerShape(8.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSelected) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    }
                                ),
                                border = if (isSelected) {
                                    androidx.compose.foundation.BorderStroke(
                                        2.dp,
                                        MaterialTheme.colorScheme.primary
                                    )
                                } else null
                            ) {
                                Text(
                                    text = font.first,
                                    fontFamily = font.second,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = if (isSelected) {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Action buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Close button
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.onSurface
                            )
                        ) {
                            Text("Close")
                        }

                        // Save button
                        Button(
                            onClick = {
                                selectedFont?.let { font ->
                                    onFontSelected(font.first, font.second)
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            enabled = selectedFont != null,
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
     * Apply font family to ALL text (global font change)
     */
    private fun applyFontFamily(textFieldValue: TextFieldValue, fontFamily: FontFamily): TextFieldValue {
        val originalAnnotated = textFieldValue.annotatedString

        // Build new AnnotatedString with font family applied to ALL text
        val newAnnotatedString = buildAnnotatedString {
            append(originalAnnotated.text)

            // First, apply font family to entire text
            if (originalAnnotated.text.isNotEmpty()) {
                addStyle(
                    style = SpanStyle(fontFamily = fontFamily),
                    start = 0,
                    end = originalAnnotated.text.length
                )
            }

            // Then copy all existing styles (bold, italic, underline) without fontFamily
            // This ensures other styles are preserved but fontFamily doesn't duplicate
            originalAnnotated.spanStyles.forEach { span ->
                // Create style with only non-font properties
                val styleWithoutFont = SpanStyle(
                    color = span.item.color,
                    fontSize = span.item.fontSize,
                    fontWeight = span.item.fontWeight,
                    fontStyle = span.item.fontStyle,
                    fontSynthesis = span.item.fontSynthesis,
                    fontFeatureSettings = span.item.fontFeatureSettings,
                    letterSpacing = span.item.letterSpacing,
                    baselineShift = span.item.baselineShift,
                    textGeometricTransform = span.item.textGeometricTransform,
                    localeList = span.item.localeList,
                    background = span.item.background,
                    textDecoration = span.item.textDecoration,
                    shadow = span.item.shadow
                )
                addStyle(styleWithoutFont, span.start, span.end)
            }
        }

        return TextFieldValue(
            annotatedString = newAnnotatedString,
            selection = textFieldValue.selection  // Preserve selection
        )
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

            // Font family handling
            val fontFamilyName = when (style.fontFamily) {
                FontFamily.Serif -> "serif"
                FontFamily.SansSerif -> "sans-serif"
                FontFamily.Monospace -> "monospace"
                FontFamily.Cursive -> "cursive"
                // Note: Custom fonts like Bebas Neue are applied globally, not per-span
                else -> null
            }

            if (fontFamilyName != null) {
                openTags += "<span style=\"font-family: $fontFamilyName;\">"
                closeTags = "</span>$closeTags"
            }

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

        // Calculate positions for font-family spans after HTML parsing
        val text = spanned.toString()

        // Convert Spanned to AnnotatedString
        return buildAnnotatedString {
            append(text)

            // Apply font families based on HTML structure
            var textIndex = 0
            var currentFontFamily: FontFamily? = null
            var fontStartIndex = 0

            for (i in html.indices) {
                if (html.substring(i).startsWith("<span style=\"font-family:")) {
                    val endIndex = html.indexOf(">", i)
                    val styleContent = html.substring(i, endIndex)
                    val familyMatch = Pattern.compile("font-family: ([^;\"]+)").matcher(styleContent)
                    if (familyMatch.find()) {
                        currentFontFamily = when (familyMatch.group(1)?.trim()) {
                            "bebas_neue" -> FontFamily(Font(R.font.bebas_neue))
                            "comfortaa_regular" -> FontFamily(Font(R.font.comfortaa_regular))
                            "drukcyr_bold" -> FontFamily(Font(R.font.drukcyr_bold))
                            "montserrat_regular" -> FontFamily(Font(R.font.montserrat_regular))
                            "opensans_regular" -> FontFamily(Font(R.font.opensans_regular))
                            "oswald_regular" -> FontFamily(Font(R.font.oswald_regular))
                            "ptsans_regular" -> FontFamily(Font(R.font.ptsans_regular))
                            "raleway_regular" -> FontFamily(Font(R.font.raleway_regular))
                            "roboto_regular" -> FontFamily(Font(R.font.roboto_regular))
                            "ubuntu_regular" -> FontFamily(Font(R.font.ubuntu_regular))
                            else -> FontFamily.Default
                        }
                        fontStartIndex = textIndex
                    }
                } else if (html.substring(i).startsWith("</span>") && currentFontFamily != null) {
                    if (fontStartIndex < textIndex && textIndex <= text.length) {
                        addStyle(SpanStyle(fontFamily = currentFontFamily), fontStartIndex, textIndex)
                    }
                    currentFontFamily = null
                } else if (!html.substring(i).startsWith("<")) {
                    textIndex++
                }
            }

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

    /**
     * Save font family to preferences for overlay
     */
    private fun saveFontFamilyToPreferences(fontName: String) {
        val fontFamilyName = when (fontName) {
            "Bebas Neue" -> "bebas_neue"
            "Comfortaa" -> "comfortaa_regular"
            "Druk Cyr Bold" -> "drukcyr_bold"
            "Montserrat" -> "montserrat_regular"
            "Open Sans" -> "opensans_regular"
            "Oswald" -> "oswald_regular"
            "PT Sans" -> "ptsans_regular"
            "Raleway" -> "raleway_regular"
            "Roboto" -> "roboto_regular"
            "Ubuntu" -> "ubuntu_regular"
            else -> "default"
        }

        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                val overlayPreferences = OverlayPreferences(this@ScriptEditorActivity)
                overlayPreferences.saveFontFamily(fontFamilyName)
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
