package com.teleprompter.app.data.validation

import com.teleprompter.app.data.models.Script
import com.teleprompter.app.utils.Constants

/**
 * Result of script validation
 */
data class ValidationResult(
    val isValid: Boolean,
    val errors: List<String> = emptyList()
) {
    val errorMessage: String?
        get() = if (errors.isNotEmpty()) errors.joinToString("\n") else null
}

/**
 * Validator for Script entities
 */
interface ScriptValidator {
    /**
     * Validate a script
     * @param script Script to validate
     * @return ValidationResult with isValid flag and list of errors
     */
    fun validate(script: Script): ValidationResult

    /**
     * Validate just the title
     */
    fun validateTitle(title: String): ValidationResult

    /**
     * Validate just the content
     */
    fun validateContent(content: String): ValidationResult
}

/**
 * Default implementation of ScriptValidator
 */
class ScriptValidatorImpl : ScriptValidator {

    override fun validate(script: Script): ValidationResult {
        val errors = mutableListOf<String>()

        // Validate title
        val titleResult = validateTitle(script.title)
        if (!titleResult.isValid) {
            errors.addAll(titleResult.errors)
        }

        // Validate content
        val contentResult = validateContent(script.content)
        if (!contentResult.isValid) {
            errors.addAll(contentResult.errors)
        }

        return ValidationResult(errors.isEmpty(), errors)
    }

    override fun validateTitle(title: String): ValidationResult {
        val errors = mutableListOf<String>()

        when {
            title.isBlank() -> errors.add("Title cannot be empty")
            title.length > Constants.MAX_TITLE_LENGTH ->
                errors.add("Title cannot exceed ${Constants.MAX_TITLE_LENGTH} characters")
            title.trim() != title -> errors.add("Title has leading or trailing whitespace")
        }

        return ValidationResult(errors.isEmpty(), errors)
    }

    override fun validateContent(content: String): ValidationResult {
        val errors = mutableListOf<String>()

        when {
            content.isBlank() -> errors.add("Content cannot be empty")
            content.length > Constants.MAX_CONTENT_LENGTH ->
                errors.add("Content cannot exceed ${Constants.MAX_CONTENT_LENGTH} characters")
        }

        return ValidationResult(errors.isEmpty(), errors)
    }
}
