package it.saranave.ciclo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.HorizontalDivider
import it.saranave.ciclo.ui.PeriodColors
import it.saranave.ciclo.widget.Widgets
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import it.saranave.ciclo.data.Catalog
import it.saranave.ciclo.data.Prefs
import it.saranave.ciclo.data.Store
import it.saranave.ciclo.ui.CatEditor
import it.saranave.ciclo.ui.CicloTheme
import it.saranave.ciclo.ui.SkinOkChip
import it.saranave.ciclo.ui.TagsEditor

/** Mini-menu che si apre sopra la home dal widget o dalla notifica, senza aprire l'app. */
class QuickPickActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val what = intent.data?.lastPathSegment ?: "more"
        setContent { CicloTheme { PickSheet(what) { finish() } } }
    }
}

@Composable
private fun PickSheet(what: String, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val rev by Store.revision.collectAsState()
    val date = Store.today()
    val day = remember(rev) { Store.day(ctx, date) }
    val title = when (what) {
        "period" -> "🩸 Mestruazioni di oggi"
        "pain" -> "⚡ Dolori di oggi"
        "skin" -> "🌡️ Pelle di oggi"
        "mood" -> "😊 Come stai oggi"
        else -> "➕ Altro di oggi"
    }
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onClose() },
        contentAlignment = Alignment.BottomCenter,
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth().padding(10.dp).navigationBarsPadding().heightIn(max = 620.dp),
            shape = RoundedCornerShape(26.dp),
            tonalElevation = 2.dp,
        ) {
            Column(Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    TextButton(onClick = onClose) { Text("Fatto") }
                }
                Column(
                    Modifier.verticalScroll(rememberScrollState()).padding(top = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    when (what) {
                        "period" -> {
                            val cur = day[Catalog.PERIOD]?.firstOrNull()
                            Catalog.period.options.forEachIndexed { i, o ->
                                Button(
                                    onClick = {
                                        Store.set(ctx, date, Catalog.PERIOD, setOf(o.key))
                                        Widgets.refreshAsync(ctx); onClose()
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = PeriodColors[i],
                                        contentColor = if (i == 0) Color(0xFF3E0020) else Color.White,
                                    ),
                                    modifier = Modifier.fillMaxWidth().height(54.dp),
                                ) { Text((if (o.key == cur) "✓  " else "") + o.label, style = MaterialTheme.typography.titleMedium) }
                            }
                            if (cur != null) OutlinedButton(
                                onClick = {
                                    Store.set(ctx, date, Catalog.PERIOD, emptySet())
                                    Widgets.refreshAsync(ctx); onClose()
                                },
                                modifier = Modifier.fillMaxWidth().height(50.dp),
                            ) { Text("Togli: niente mestruazioni oggi") }
                        }
                        "pain" -> CatEditor(Catalog.pain, date, day, showTitle = false)
                        "skin" -> {
                            SkinOkChip(date, day)
                            CatEditor(Catalog.skin, date, day, showTitle = false)
                            HorizontalDivider()
                            CatEditor(Catalog.zones, date, day)
                            HorizontalDivider()
                            CatEditor(Catalog.triggers, date, day)
                        }
                        "mood" -> {
                            CatEditor(Catalog.feelings, date, day)
                            CatEditor(Catalog.energy, date, day)
                        }
                        else -> {
                            val skip = setOf(Catalog.PERIOD, "pain", Catalog.SKIN, Catalog.ZONES, "triggers", "feelings", "energy")
                            val prefsRev by Prefs.revision.collectAsState()
                            val cats = remember(prefsRev) { Prefs.orderedCats(ctx).filter { it.key !in skip && Prefs.catVisible(ctx, it) } }
                            for (c in cats) CatEditor(c, date, day)
                            Text("🏷️  Tag", style = MaterialTheme.typography.titleSmall)
                            TagsEditor(date, day)
                        }
                    }
                }
            }
        }
    }
}
