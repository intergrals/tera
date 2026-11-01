package com.envelope.budget

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.envelope.budget.ui.nav.EnvelopeApp
import com.envelope.budget.ui.theme.EnvelopeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            EnvelopeTheme {
                EnvelopeApp()
            }
        }
    }
}
