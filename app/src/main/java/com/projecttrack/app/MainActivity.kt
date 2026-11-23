package com.projecttrack.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.projecttrack.app.ui.ProjectTrackApp
import com.projecttrack.app.ui.theme.ProjectTrackTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            ProjectTrackTheme {
                ProjectTrackApp()
            }
        }
    }
}
