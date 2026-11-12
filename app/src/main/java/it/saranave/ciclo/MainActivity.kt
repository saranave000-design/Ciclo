package it.saranave.ciclo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import it.saranave.ciclo.data.Sync
import it.saranave.ciclo.ui.CalendarScreen
import it.saranave.ciclo.ui.CicloTheme
import it.saranave.ciclo.ui.DayScreen
import it.saranave.ciclo.ui.SettingsScreen
import it.saranave.ciclo.ui.StatsScreen
import it.saranave.ciclo.widget.Widgets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { CicloTheme { App() } }
        Reminder.schedule(this)
    }

    override fun onResume() {
        super.onResume()
        // Prende in automatico i dati che Claude ha caricato.
        lifecycleScope.launch(Dispatchers.IO) {
            val r = Sync.pull(applicationContext)
            if (r is Sync.Result.Ok && r.applied > 0) Widgets.refresh(applicationContext)
        }
    }
}

@Composable
private fun App() {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var date by remember { mutableStateOf(LocalDate.now()) }
    val tabs = listOf("📝" to "Giorno", "📅" to "Calendario", "📊" to "Andamento", "⚙️" to "Altro")
    Scaffold(
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { i, (e, l) ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { tab = i },
                        icon = { Text(e, fontSize = 20.sp) },
                        label = { Text(l) },
                    )
                }
            }
        },
    ) { pad ->
        Box(Modifier.padding(pad)) {
            when (tab) {
                0 -> DayScreen(date) { date = it }
                1 -> CalendarScreen { date = it; tab = 0 }
                2 -> StatsScreen()
                else -> SettingsScreen()
            }
        }
    }
}
