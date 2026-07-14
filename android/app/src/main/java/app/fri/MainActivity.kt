package app.fri

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import app.fri.ui.PostEditorScreen
import app.fri.ui.PostListScreen
import app.fri.ui.SettingsScreen
import app.fri.ui.StatsScreen
import app.fri.ui.TripScreen
import app.fri.ui.TripsScreen

private val CamperColors = lightColorScheme(
    primary = Color(0xFFB4552D),
    onPrimary = Color(0xFFFAF6EF),
    surface = Color(0xFFFAF6EF),
    background = Color(0xFFFAF6EF),
    onSurface = Color(0xFF2B2620),
    onBackground = Color(0xFF2B2620),
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = CamperColors) {
                Surface { AppNav() }
            }
        }
    }
}

@Composable
private fun AppNav() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = "trip") {
        composable("trip") { TripScreen(nav) }
        composable("trips") { TripsScreen(nav) }
        composable("posts") { PostListScreen(nav) }
        composable("post") { PostEditorScreen(nav) }
        composable("post/{slug}") { entry ->
            PostEditorScreen(nav, editSlug = entry.arguments?.getString("slug"))
        }
        composable("stats") { StatsScreen(nav) }
        composable("settings") { SettingsScreen(nav) }
    }
}
