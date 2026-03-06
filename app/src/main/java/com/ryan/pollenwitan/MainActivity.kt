package com.ryan.pollenwitan

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.ryan.pollenwitan.ui.navigation.AppNavGraph
import com.ryan.pollenwitan.ui.theme.PollenWitanTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PollenWitanTheme {
                AppNavGraph()
            }
        }
    }
}
