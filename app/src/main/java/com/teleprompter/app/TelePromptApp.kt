package com.teleprompter.app

import android.app.Application
import com.teleprompter.app.data.db.AppDatabase

/**
 * Application class for global initialization
 */
class TelePromptApp : Application() {

    // Lazy initialization of database
    val database: AppDatabase by lazy {
        AppDatabase.getDatabase(this)
    }

    override fun onCreate() {
        super.onCreate()
        // Initialize Room database (accessed lazily when needed)
        // DataStore is initialized on-demand per component
    }

    companion object {
        @Volatile
        private var instance: TelePromptApp? = null

        fun getInstance(): TelePromptApp {
            return instance ?: throw IllegalStateException("Application not initialized")
        }
    }

    init {
        instance = this
    }
}
