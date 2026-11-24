package com.github.damontecres.wholphin.ui.door

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.services.GameLibraryClient
import com.github.damontecres.wholphin.services.KidsMode
import com.github.damontecres.wholphin.ui.theme.BenflixColors
import com.github.damontecres.wholphin.ui.theme.BenflixType
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** How well a title fits the person watching (Tower's benflix_match.py) */
data class TitleMatch(
    val percent: Int,
    val why: String?,
)

/**
 * Glowfin "94% for you" (Idea Box 28, Ben 2026-11-24). Asks Tower once per title and keeps the answer while
 * the app is open. Nothing shows for Kids, or until the person has watched or liked enough.
 */
object MatchScores {
    private val known = ConcurrentHashMap<String, TitleMatch>()
    private val none = TitleMatch(0, null)

    /** Each person has their own matches */
    private fun key(
        user: UUID?,
        id: UUID,
    ) = "$user/$id"

    /** True when Tower hasn't given a final answer for this title yet */
    fun waiting(
        user: UUID?,
        id: UUID,
    ): Boolean = !known.containsKey(key(user, id))

    fun cached(
        user: UUID?,
        id: UUID,
    ): TitleMatch? = known[key(user, id)]?.takeIf { it !== none }

    suspend fun get(
        helper: GameLibraryClient,
        user: UUID?,
        id: UUID,
    ): TitleMatch? {
        known[key(user, id)]?.let { return it.takeIf { m -> m !== none } }
        val got =
            try {
                val json = helper.getJson("/api/match/${id.toString().replace("-", "")}")
                val pct = json.optInt("pct", 0)
                // Tower is still working out this person's taste (speed check 2026-11-24): ask again in a moment
                if (json.optBoolean("later", false)) return null
                if (pct > 0) TitleMatch(pct, json.optString("why").ifBlank { null }) else none
            } catch (ex: CancellationException) {
                throw ex
            } catch (_: Exception) {
                null
            }
        if (got != null) known[key(user, id)] = got
        return got?.takeIf { it !== none }
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface MatchHelperEntryPoint {
    fun helper(): GameLibraryClient

    fun servers(): ServerRepository
}

private fun matchEntry(context: Context): MatchHelperEntryPoint =
    EntryPointAccessors.fromApplication(context.applicationContext, MatchHelperEntryPoint::class.java)

private val MATCH_AQUA = Color(0xFF3FE0E8)

/**
 * "94% for you" under the score pill, with "Because you loved Arrival" under it. It sits in the empty space at
 * the top right, laid over the page, so nothing else moves (Ben 2026-11-24). Movies and shows only.
 */
@Composable
fun MatchPill(
    itemId: UUID?,
    modifier: Modifier = Modifier,
    small: Boolean = false,
    showWhy: Boolean = true,
) {
    val context = LocalContext.current
    val kids by KidsMode.active.collectAsState()
    val entry = remember(context) { matchEntry(context) }
    val user = entry.servers().currentUser?.id
    var match by remember(itemId, user) { mutableStateOf(itemId?.let { MatchScores.cached(user, it) }) }
    LaunchedEffect(itemId, kids, user) {
        if (itemId == null || kids) {
            match = null
            return@LaunchedEffect
        }
        if (match == null) {
            // Moving quickly along a row: only ask once you stop on a title
            delay(350)
            match = MatchScores.get(entry.helper(), user, itemId)
            // Tower answers right away while it works out someone's taste, so look again a few times
            var tries = 0
            while (match == null && tries < 3 && MatchScores.waiting(user, itemId)) {
                tries++
                delay(4000)
                match = MatchScores.get(entry.helper(), user, itemId)
            }
        }
    }
    val m = match ?: return
    if (kids) return
    val shape = RoundedCornerShape(50)
    Column(horizontalAlignment = Alignment.End, modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            modifier =
                Modifier
                    .background(BenflixColors.Tray.copy(alpha = .8f), shape)
                    .border(1.5.dp, MATCH_AQUA.copy(alpha = .7f), shape)
                    .padding(horizontal = if (small) 8.dp else 12.dp, vertical = if (small) 2.dp else 4.dp),
        ) {
            Text(
                text = "${m.percent}%",
                color = MATCH_AQUA,
                fontSize = if (small) BenflixType.small else BenflixType.lead,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
            )
            Text(
                text = "for you",
                color = BenflixColors.Text,
                fontSize = if (small) BenflixType.tag else BenflixType.body,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
        }
        if (showWhy && m.why != null) {
            Text(
                text = m.why,
                color = BenflixColors.Soft,
                fontSize = BenflixType.tag,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.End,
                modifier = Modifier.padding(top = 4.dp, end = 4.dp).widthIn(max = 260.dp),
            )
        }
    }
}
