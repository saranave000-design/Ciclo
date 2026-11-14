package it.saranave.ciclo.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.glance.appwidget.updateAll
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.Action
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import it.saranave.ciclo.MainActivity
import it.saranave.ciclo.QuickPickActivity
import it.saranave.ciclo.data.Catalog
import it.saranave.ciclo.data.Cycle
import it.saranave.ciclo.data.Store
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private val BG = Color(0xFF2A1F2D)
private val IDLE = Color(0xFF433547)
private val WHITE = Color(0xFFFFFFFF)
private val SOFT = Color(0xFFE6D9E8)
val PERIOD_COLORS = listOf(Color(0xFFF48FB1), Color(0xFFEC407A), Color(0xFFC2185B), Color(0xFF880E4F))
private val SKIN_COLORS = listOf(Color(0xFF5E9E62), Color(0xFFE0A526), Color(0xFFE86A3A), Color(0xFFC62828))
private val ACTIVE = Color(0xFF7B4A86)

class CicloWidget : GlanceAppWidget() {
    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val rev = currentState<Preferences>()[REV] ?: 0L
            // key(rev): a ogni modifica il widget si ridisegna da capo con i dati nuovi
            key(rev) { Content(context, rev) }
        }
    }

    companion object {
        val REV = longPreferencesKey("rev")
    }
}

class CicloWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CicloWidget()
}

object Widgets {
    suspend fun refresh(context: Context) {
        val ctx = context.applicationContext
        val ids = GlanceAppWidgetManager(ctx).getGlanceIds(CicloWidget::class.java)
        for (id in ids) {
            updateAppWidgetState(ctx, id) { it[CicloWidget.REV] = System.currentTimeMillis() }
            CicloWidget().update(ctx, id)
        }
        CicloWidget().updateAll(ctx)
    }

    fun refreshAsync(context: Context) {
        val ctx = context.applicationContext
        CoroutineScope(Dispatchers.Default).launch { runCatching { refresh(ctx) } }
    }
}

class PeriodTap : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        Store.cyclePeriod(context, Store.today())
        Widgets.refresh(context)
    }
}

fun pickIntent(context: Context, what: String): Intent =
    Intent(context, QuickPickActivity::class.java)
        .setData(Uri.parse("ciclo://pick/$what"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)

@Composable
private fun Content(context: Context, @Suppress("UNUSED_PARAMETER") rev: Long) {
    val days = Store.snapshot(context)
    val today = Store.today()
    val d = days[today] ?: emptyMap()
    val info = Cycle.compute(days)
    val period = d[Catalog.PERIOD]?.firstOrNull()
    val header = Cycle.headerText(info, period != null)

    val pain = d["pain"]?.size ?: 0
    val skinVals = d[Catalog.SKIN] ?: emptySet()
    val skinMax = skinVals.maxOfOrNull { Catalog.splitLevel(it).second } ?: 0
    val skinOk = d[Catalog.SKIN_OK]?.isNotEmpty() == true
    val mood = (d["feelings"]?.size ?: 0) + (d["energy"]?.size ?: 0)
    val others = d.keys.count { it !in setOf(Catalog.PERIOD, "pain", Catalog.SKIN, Catalog.SKIN_OK, "feelings", "energy") }

    Column(
        modifier = GlanceModifier.fillMaxSize().background(ColorProvider(BG)).cornerRadius(22.dp).padding(8.dp),
    ) {
        Text(
            text = header,
            style = TextStyle(color = ColorProvider(SOFT), fontSize = 13.sp, fontWeight = FontWeight.Medium),
            maxLines = 1,
            modifier = GlanceModifier.fillMaxWidth().padding(start = 6.dp, bottom = 6.dp)
                .clickable(actionStartActivity<MainActivity>()),
        )
        Row(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
            Btn(
                "🩸", period?.let { Catalog.label(Catalog.PERIOD, it) } ?: "Ciclo",
                period?.let { PERIOD_COLORS[Catalog.periodLevels.indexOf(it).coerceAtLeast(0)] } ?: IDLE,
                actionStartActivity(pickIntent(context, "period")), GlanceModifier.defaultWeight(),
            )
            Spacer(GlanceModifier.width(6.dp))
            Btn("⚡", if (pain > 0) "Dolori · $pain" else "Dolori", if (pain > 0) ACTIVE else IDLE,
                actionStartActivity(pickIntent(context, "pain")), GlanceModifier.defaultWeight())
        }
        Spacer(GlanceModifier.height(6.dp))
        Row(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
            Btn(
                "🌡️",
                when { skinMax > 0 -> "Pelle · ${skinVals.size}"; skinOk -> "Pelle ok"; else -> "Pelle" },
                when { skinMax > 0 -> SKIN_COLORS[skinMax]; skinOk -> SKIN_COLORS[0]; else -> IDLE },
                actionStartActivity(pickIntent(context, "skin")), GlanceModifier.defaultWeight(),
            )
            Spacer(GlanceModifier.width(6.dp))
            Btn("😊", if (mood > 0) "Umore · $mood" else "Umore", if (mood > 0) ACTIVE else IDLE,
                actionStartActivity(pickIntent(context, "mood")), GlanceModifier.defaultWeight())
            Spacer(GlanceModifier.width(6.dp))
            Btn("➕", if (others > 0) "Altro · $others" else "Altro", if (others > 0) ACTIVE else IDLE,
                actionStartActivity(pickIntent(context, "more")), GlanceModifier.defaultWeight())
        }
    }
}

@Composable
private fun Btn(emoji: String, label: String, bg: Color, action: Action, modifier: GlanceModifier) {
    Column(
        modifier = modifier.fillMaxHeight().background(ColorProvider(bg)).cornerRadius(16.dp)
            .clickable(action).padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(emoji, style = TextStyle(fontSize = 18.sp))
        Text(label, style = TextStyle(color = ColorProvider(WHITE), fontSize = 12.sp, fontWeight = FontWeight.Medium), maxLines = 1)
    }
}
