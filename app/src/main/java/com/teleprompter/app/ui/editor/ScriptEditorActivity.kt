package com.teleprompter.app.ui.editor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.teleprompter.app.data.db.AppDatabase
import com.teleprompter.app.data.models.Script
import com.teleprompter.app.utils.Constants
import kotlinx.coroutines.launch

/**
 * Activity for creating and editing scripts
 */
@OptIn(ExperimentalMaterial3Api::class)
class ScriptEditorActivity : ComponentActivity() {

    private lateinit var database: AppDatabase
    private lateinit var validator: com.teleprompter.app.data.validation.ScriptValidator
    private var scriptId: Long? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        database = AppDatabase.getDatabase(this)
        validator = com.teleprompter.app.data.validation.ScriptValidatorImpl()
        scriptId = intent.getLongExtra(Constants.EXTRA_SCRIPT_ID, -1L).takeIf { it != -1L }

        setContent {
            MaterialTheme {
                ScriptEditorScreen()
            }
        }
    }

    @Composable
    fun ScriptEditorScreen() {
        var title by remember { mutableStateOf("") }
        var content by remember { mutableStateOf("") }
        var isLoading by remember { mutableStateOf(true) }
        var titleError by remember { mutableStateOf<String?>(null) }
        var contentError by remember { mutableStateOf<String?>(null) }

        // Validate inputs
        val isValid = remember(title, content) {
            val titleValidation = validator.validateTitle(title)
            val contentValidation = validator.validateContent(content)
            titleError = titleValidation.errorMessage
            contentError = contentValidation.errorMessage
            titleValidation.isValid && contentValidation.isValid
        }

        // Load existing script if editing
        LaunchedEffect(scriptId) {
            scriptId?.let { id ->
                withContext(Dispatchers.IO) {
                    database.scriptDao().getScriptById(id)
                }?.let { script ->
                    title = script.title
                    content = script.content
                }
            }
            isLoading = false
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(if (scriptId == null) "New Script" else "Edit Script") },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
            }
        ) { padding ->
            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = androidx.compose.ui.Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Title input
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Script Title") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        isError = titleError != null,
                        supportingText = titleError?.let { { Text(it) } }
                    )

                    // Content input
                    OutlinedTextField(
                        value = content,
                        onValueChange = { content = it },
                        label = { Text("Script Content") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        minLines = 10,
                        isError = contentError != null,
                        supportingText = contentError?.let { { Text(it) } }
                    )

                    // Action buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { saveScript(title, content) },
                            modifier = Modifier.weight(1f),
                            enabled = isValid
                        ) {
                            Text("Save")
                        }
                        OutlinedButton(
                            onClick = { finish() },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Cancel")
                        }
                    }
                }
            }
        }
    }

    private fun saveScript(title: String, content: String) {
        if (title.isBlank() || content.isBlank()) return

        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                if (scriptId == null) {
                    // Create new script
                    val script = Script(
                        id = 0,
                        title = title,
                        content = content,
                        createdAt = System.currentTimeMillis(),
                        updatedAt = System.currentTimeMillis()
                    )
                    database.scriptDao().insertScript(script)
                } else {
                    // Update existing script - preserve createdAt
                    val existing = database.scriptDao().getScriptById(scriptId!!)
                    if (existing != null) {
                        val updated = existing.copy(
                            title = title,
                            content = content,
                            updatedAt = System.currentTimeMillis()
                        )
                        database.scriptDao().updateScript(updated)
                    }
                }
            }

            finish()
        }
    }
}
