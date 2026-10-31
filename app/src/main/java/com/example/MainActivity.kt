package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import com.example.data.AppDatabase
import com.example.data.AutomationRepository
import com.example.ui.screens.MainNavigationScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.AutomationViewModel
import com.example.ui.viewmodel.AutomationViewModelFactory

class MainActivity : ComponentActivity() {

    private lateinit var viewModel: AutomationViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val database = AppDatabase.getInstance(applicationContext)
        val repository = AutomationRepository(database.automationDao())
        val factory = AutomationViewModelFactory(repository, applicationContext)
        viewModel = ViewModelProvider(this, factory)[AutomationViewModel::class.java]

        setContent {
            MyApplicationTheme {
                MainNavigationScreen(viewModel = viewModel)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (::viewModel.isInitialized) {
            viewModel.refreshPermissions()
        }
    }
}
