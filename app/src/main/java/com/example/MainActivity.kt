package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.data.local.AppDatabase
import com.example.data.local.PreferencesManager
import com.example.data.repository.AppRepository
import com.example.data.repository.GeminiRepository
import com.example.data.repository.CloudSyncRepository
import com.example.data.repository.FirestoreSyncRepository
import com.example.ui.MainApp
import com.example.ui.PosViewModel
import com.example.ui.theme.MyApplicationTheme
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier

class MainActivity : ComponentActivity() {
    
    private val viewModel: PosViewModel by viewModels {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val database = AppDatabase.getDatabase(applicationContext)
                val prefs = PreferencesManager(applicationContext)
                val repository = AppRepository(database, prefs)
                val cloudSyncRepository = CloudSyncRepository(repository)
                val firestoreSyncRepository = FirestoreSyncRepository(repository, applicationContext)
                val geminiRepository = GeminiRepository(prefs)
                return PosViewModel(repository, geminiRepository, cloudSyncRepository, firestoreSyncRepository) as T
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val appTheme by viewModel.appTheme.collectAsStateWithLifecycle()
            MyApplicationTheme(darkTheme = (appTheme == "DARK")) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainApp(viewModel)
                }
            }
        }
    }
}
