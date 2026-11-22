package com.github.damontecres.wholphin.ui.main

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.damontecres.wholphin.services.BenflixTower
import com.github.damontecres.wholphin.ui.touch.rememberIsTv
import com.github.damontecres.wholphin.util.WholphinDispatchers
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jellyfin.sdk.api.client.ApiClient
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import javax.inject.Inject

/** One button on a Tower card: its words and what to tell Tower when it is pressed */
data class TowerCardAction(
    val label: String,
    val act: String,
)

data class TowerCardRow(
    val light: String,
    val title: String,
    val sub: String,
    val actions: List<TowerCardAction>,
    /** A page inside Jellyfin's own dashboard (like "#/dashboard/logs") this row can open, on phones */
    val link: String? = null,
    /** Server > Health's "Last 24 hours": readings oldest first, null where there's none, drawn as a small line */
    val spark: List<Float?>? = null,
)

/**
 * A Tower card (Ben's picks, 2026-11-21). Tower's helper (benflix_keeper.py) sends its jobs list, the nightly safety
 * copy, the away check and the "Try these" list all in this one shape, so the TV and phone draw them the same way.
 */
data class TowerCard(
    val headline: String,
    val reason: String,
    val level: String,
    val actions: List<TowerCardAction>,
    val rows: List<TowerCardRow>,
    /** What Tower said after the last button press, like "Safety copy made: 412 MB." */
    val said: String? = null,
    val failed: String? = null,
    /** Text to copy for Claude (phones only), and the button's words */
    val copy: String? = null,
    val copyLabel: String? = null,
)

private fun JSONArray?.cardActions(): List<TowerCardAction> =
    this
        ?.let { a -> (0 until a.length()).mapNotNull { a.optJSONObject(it) } }
        .orEmpty()
        .map { TowerCardAction(it.optString("label"), it.optString("do")) }
        .filter { it.label.isNotBlank() && it.act.isNotBlank() }

private fun cardOf(j: JSONObject): TowerCard =
    TowerCard(
        headline = j.optString("headline"),
        reason = j.optString("reason"),
        level = j.optString("level"),
        actions = j.optJSONArray("acts").cardActions(),
        rows =
            j
                .optJSONArray("rows")
                ?.let { a -> (0 until a.length()).mapNotNull { a.optJSONObject(it) } }
                .orEmpty()
                .map {
                    TowerCardRow(
                        it.optString("light"),
                        it.optString("t"),
                        it.optString("s"),
                        it.optJSONArray("acts").cardActions(),
                        it.optString("link").takeIf { link -> link.startsWith("#/") },
                        it.optJSONArray("spark")?.let { a ->
                            (0 until a.length()).map { i -> if (a.isNull(i)) null else a.optDouble(i).toFloat() }
                        },
                    )
                },
        copy = j.optString("copy").takeIf { it.isNotBlank() },
        copyLabel = j.optString("copy_label").takeIf { it.isNotBlank() },
    )

private fun lightOf(word: String): Light =
    when (word) {
        "ok" -> Light.GOOD
        "warn" -> Light.WARN
        "bad" -> Light.BAD
        else -> Light.UNKNOWN
    }

@HiltViewModel
class TowerCardsViewModel
    @Inject
    constructor(
        private val tower: BenflixTower,
        private val api: ApiClient,
    ) : ViewModel() {
        /** Where Jellyfin's own pages live, for rows that open one ("Rarely needed") */
        fun jellyfinPage(link: String): String? = api.baseUrl?.trimEnd('/')?.let { "$it/web/$link" }

        private val _cards = MutableStateFlow<Map<String, TowerCard>>(emptyMap())
        val cards: StateFlow<Map<String, TowerCard>> = _cards

        fun load(
            name: String,
            said: String? = null,
        ) {
            viewModelScope.launch(WholphinDispatchers.IO) {
                try {
                    val card = cardOf(tower.card(name)).copy(said = said)
                    _cards.update { it + (name to card) }
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.w(ex, "Tower card %s", name)
                    val old = _cards.value[name]
                    val why = ex.message ?: "Couldn't ask Tower."
                    _cards.update {
                        it +
                            (name to (old?.copy(failed = why) ?: TowerCard("", "", "off", emptyList(), emptyList(), failed = why)))
                    }
                }
            }
        }

        fun press(
            name: String,
            action: TowerCardAction,
        ) {
            viewModelScope.launch(WholphinDispatchers.IO) {
                val said =
                    try {
                        tower.cardDo(name, action.act)
                    } catch (ex: CancellationException) {
                        throw ex
                    } catch (ex: Exception) {
                        Timber.w(ex, "Tower card %s press", name)
                        ex.message ?: "Couldn't ask Tower."
                    }
                load(name, said)
                // Jobs started from here take a moment: look again so the list shows how it went
                delay(5000)
                load(name, said)
            }
        }
    }

/**
 * A Tower card as rows on the Server page: [name] is jobs, backup, away, try, addons, convert or jellyfin.
 * [max] trims a long list.
 */
internal fun LazyListScope.towerCardItems(
    name: String,
    title: String,
    max: Int = Int.MAX_VALUE,
    hideWhenEmpty: Boolean = false,
) {
    item(key = "tower-card-$name") { TowerCardRows(name, title, max, hideWhenEmpty) }
}

@Composable
private fun TowerCardRows(
    name: String,
    title: String,
    max: Int,
    hideWhenEmpty: Boolean,
    viewModel: TowerCardsViewModel = hiltViewModel(),
) {
    LaunchedEffect(name) { viewModel.load(name) }
    val cards by viewModel.cards.collectAsState()
    val card = cards[name]
    val isTv = rememberIsTv()
    val context = LocalContext.current
    if (hideWhenEmpty && (card == null || card.rows.isEmpty())) return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SectionTitle(title)
        when {
            card == null -> {
                LoadingBox("Asking Tower…")
            }

            card.headline.isBlank() && card.failed != null -> {
                ServerActionRow(
                    headline = "Tower doesn't have this yet",
                    supporting = "It arrives by itself with Tower's next hourly update.",
                    leading = { Dot(Light.UNKNOWN) },
                    actions = listOf("Try again" to { viewModel.load(name) }),
                )
            }

            else -> {
                ServerActionRow(
                    headline = card.headline,
                    supporting =
                        listOfNotNull(
                            card.said,
                            card.reason.takeIf { it.isNotBlank() },
                        ).joinToString(" · ").takeIf { it.isNotBlank() },
                    leading = { Dot(lightOf(card.level)) },
                    actions =
                        card.actions.map { a -> a.label to { viewModel.press(name, a) } } +
                            // A report to paste to Claude: phones only (a TV has nowhere to paste it)
                            listOfNotNull(
                                card.copy?.takeIf { !isTv }?.let { text ->
                                    (card.copyLabel ?: "Copy for Claude") to {
                                        val clip = context.getSystemService(android.content.ClipboardManager::class.java)
                                        clip?.setPrimaryClip(android.content.ClipData.newPlainText("Glowfin report", text))
                                        Toast.makeText(context, "Copied. Paste it to Claude.", Toast.LENGTH_SHORT).show()
                                    }
                                },
                            ),
                )
                card.rows.take(max).forEach { r ->
                    ServerActionRow(
                        headline = r.title,
                        supporting = r.sub.takeIf { it.isNotBlank() },
                        extra = r.spark?.let { points -> @Composable { SparkLine(points) } },
                        leading = { Dot(lightOf(r.light)) },
                        actions =
                            r.actions.map { a -> a.label to { viewModel.press(name, a) } } +
                                // Jellyfin's dashboard needs a mouse or a finger, so TVs only get the words
                                listOfNotNull(
                                    r.link?.takeIf { !isTv }?.let(viewModel::jellyfinPage)?.let { url ->
                                        "Open in Jellyfin" to {
                                            try {
                                                context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
                                            } catch (ex: Exception) {
                                                Timber.w(ex, "Could not open %s", url)
                                                Toast.makeText(context, "Couldn't open a browser on this device.", Toast.LENGTH_LONG).show()
                                            }
                                        }
                                    },
                                ),
                    )
                }
                if (card.rows.size > max) {
                    ServerActionRow(
                        headline = "And ${card.rows.size - max} more",
                        supporting = "Mark some of these and the next ones show.",
                        leading = { Dot(Light.UNKNOWN) },
                    )
                }
            }
        }
    }
}

/** A small line graph (Server > Health's "Last 24 hours"): oldest on the left, gaps where Tower had no reading */
@Composable
private fun SparkLine(points: List<Float?>) {
    val have = points.filterNotNull()
    if (have.size < 2) return
    val lo = have.min()
    val span = (have.max() - lo).takeIf { it > 0f } ?: 1f
    val color = com.github.damontecres.wholphin.ui.theme.BenflixColors.Shows
    androidx.compose.foundation.Canvas(
        modifier =
            androidx.compose.ui.Modifier
                .padding(top = 6.dp)
                .fillMaxWidth(0.9f)
                .height(40.dp),
    ) {
        val step = size.width / (points.size - 1).coerceAtLeast(1)
        val path = Path()
        var pen = false
        points.forEachIndexed { i, v ->
            if (v == null) {
                pen = false
                return@forEachIndexed
            }
            val x = i * step
            val y = size.height - 2f - (v - lo) / span * (size.height - 4f)
            if (pen) path.lineTo(x, y) else path.moveTo(x, y)
            pen = true
        }
        drawPath(
            path,
            color = color,
            style =
                androidx.compose.ui.graphics.drawscope.Stroke(
                    width = 2.dp.toPx(),
                    cap = androidx.compose.ui.graphics.StrokeCap.Round,
                    join = androidx.compose.ui.graphics.StrokeJoin.Round,
                ),
        )
    }
}
