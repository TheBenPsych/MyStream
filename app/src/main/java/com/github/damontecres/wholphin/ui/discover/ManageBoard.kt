package com.github.damontecres.wholphin.ui.discover

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.Border
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.data.model.RequestStatus
import com.github.damontecres.wholphin.data.model.SeerrAvailability
import com.github.damontecres.wholphin.data.model.SeerrItemType
import com.github.damontecres.wholphin.services.BenflixCantFind
import com.github.damontecres.wholphin.services.GameLibraryClient
import com.github.damontecres.wholphin.ui.showToast
import com.github.damontecres.wholphin.ui.theme.BenflixType
import com.github.damontecres.wholphin.ui.theme.Button
import com.github.damontecres.wholphin.ui.theme.Glowfin
import com.github.damontecres.wholphin.ui.theme.GlowfinDisplayFont
import com.github.damontecres.wholphin.ui.touch.rememberIsTv
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject
import timber.log.Timber
import javax.inject.Inject

/**
 * Manage (Ben 2026-11-21, Ben only): the Your requests tab as a board for whoever approves. Five boxes
 * across the top count everything and pick the list below, with filters by person and by kind, several
 * answers at once, Stop on anything that already had a yes and Changed my mind on anything Ben said no to.
 */
internal enum class ManageLane(
    val title: String,
    val sub: String,
    val color: Color,
    val empty: String,
) {
    NEEDS("Needs you", "Say yes or no", Color(0xFFECEEFB), "Nobody is waiting on you right now."),
    WAY("On its way", "Tower is getting these", Color(0xFF7AA7FF), "Nothing is on its way right now."),
    STUCK("Stuck", "Looking for 5 days or more", Color(0xFFF2B544), "Nothing is stuck. Good."),
    READY("Ready", "Arrived", Color(0xFF5BD18B), "Nothing has arrived yet."),
    NO("Said no", "You turned these down", Color(0xFFFF8A8A), "You haven't said no to anything."),
}

/** Days of looking before a request counts as stuck */
internal const val MANAGE_STUCK_DAYS = 5L

internal fun RequestEntry.manageLane(notFound: Boolean): ManageLane {
    val days = BenflixCantFind.daysSince(request.updatedAt ?: request.createdAt) ?: 0L
    return when {
        available -> ManageLane.READY

        status == RequestStatus.PENDING -> ManageLane.NEEDS

        status == RequestStatus.DECLINED -> ManageLane.NO

        notFound || status == RequestStatus.FAILURE -> ManageLane.STUCK

        // some of it is here: a show still airing sits like this for months, so it is never called stuck
        downloading || request.media?.status == SeerrAvailability.PARTIALLY_AVAILABLE.status -> ManageLane.WAY

        days >= MANAGE_STUCK_DAYS -> ManageLane.STUCK

        else -> ManageLane.WAY
    }
}

internal fun AskLine.manageLane(): ManageLane =
    when {
        onYes != null -> ManageLane.NEEDS
        pill.state == AskState.READY -> ManageLane.READY
        pill.state == AskState.CANT -> ManageLane.STUCK
        else -> ManageLane.WAY
    }

internal fun RequestEntry.manageKind(): String =
    when {
        item?.isAnime == true -> "Anime"
        item?.type == SeerrItemType.MOVIE -> "Movies"
        else -> "Shows"
    }

internal fun AskLine.manageKind(): String = if (key.startsWith("music-")) "Music" else "Books"

internal val MANAGE_KINDS = listOf("Movies", "Shows", "Anime", "Music", "Books")

/** The five count boxes. On a TV they share the width; on a phone they slide sideways. */
@Composable
internal fun ManageTiles(
    counts: Map<ManageLane, Int>,
    lane: ManageLane,
    onLane: (ManageLane) -> Unit,
) {
    val isTv = rememberIsTv()
    val shape = RoundedCornerShape(if (isTv) 22.dp else 16.dp)
    val row =
        if (isTv) {
            Modifier.fillMaxWidth()
        } else {
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
        }
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = row.padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        ManageLane.entries.forEach { l ->
            val on = l == lane
            Button(
                onClick = { onLane(l) },
                shape = ButtonDefaults.shape(shape = shape),
                // Glowfin new look: see-through pills, the picked box solid white
                colors =
                    ButtonDefaults.colors(
                        containerColor = if (on) MANAGE_PICKED else MANAGE_GLASS,
                        contentColor = if (on) Glowfin.Ink else Glowfin.Text,
                        focusedContainerColor = Glowfin.Text,
                        focusedContentColor = Glowfin.Ink,
                    ),
                border =
                    ButtonDefaults.border(
                        border = Border(BorderStroke(1.5.dp, if (on) Color.Transparent else MANAGE_EDGE), shape = shape),
                        focusedBorder = Border(BorderStroke(2.dp, Glowfin.Aqua), shape = shape),
                    ),
                scale = ButtonDefaults.scale(focusedScale = 1f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                modifier = if (isTv) Modifier.weight(1f) else Modifier.width(118.dp),
            ) {
                Column(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(
                            Modifier
                                .size(10.dp)
                                .clip(RoundedCornerShape(5.dp))
                                .background(l.color),
                        )
                        Text(
                            text = (counts[l] ?: 0).toString(),
                            fontSize = 26.sp,
                            fontFamily = GlowfinDisplayFont,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                        )
                    }
                    Text(
                        text = l.title,
                        fontSize = BenflixType.small,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (isTv) {
                        Text(
                            text = l.sub,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

private val MANAGE_GLASS = Color(0x660B0D1A)
private val MANAGE_PICKED = Color(0xEBFFFFFF)
private val MANAGE_EDGE = Color(0x24FFFFFF)

/**
 * Glowfin new look (Ben approved 2026-11-22): on a TV the board's rows sit on the left and the request you're on
 * fills the right side, its art behind the page and its details beside the rows: who asked and when, their note,
 * and why it's stuck. On phones the rows keep the whole width, like before.
 */
@Composable
internal fun ManageFrame(
    on: Boolean,
    art: String?,
    side: @Composable () -> Unit,
    content: @Composable (Modifier) -> Unit,
) {
    if (!on) {
        content(Modifier.fillMaxSize())
        return
    }
    Box(Modifier.fillMaxSize()) {
        RequestArtUrl(art)
        Row(Modifier.fillMaxSize()) {
            Box(Modifier.weight(0.6f).fillMaxHeight()) { content(Modifier.fillMaxSize()) }
            Box(
                Modifier
                    .weight(0.4f)
                    .fillMaxHeight()
                    .padding(start = 18.dp, end = 32.dp, top = 112.dp),
            ) { side() }
        }
    }
}

/** What the right side says about the request you're on */
internal class ManageShown(
    val key: String,
    val art: String?,
    val title: String,
    val facts: String,
    val tag: RqTag,
    val story: String?,
    /** "Why: no copies found yet", "Mia asked on Nov 17 · you said yes", the note they left */
    val lines: List<String>,
)

@Composable
internal fun ManageSide(shown: ManageShown?) {
    if (shown == null) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        RequestTopText(
            title = shown.title,
            facts = shown.facts,
            tags = listOf(shown.tag),
            story = shown.story,
            hint = null,
        )
        shown.lines.forEach {
            Text(
                text = it,
                fontSize = BenflixType.small,
                fontWeight = FontWeight.SemiBold,
                color = Glowfin.Text.copy(alpha = 0.8f),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** A chip that shows which one is picked */
@Composable
internal fun ManageChip(
    label: String,
    on: Boolean,
    onClick: () -> Unit,
    face: String? = null,
) {
    Button(
        onClick = onClick,
        colors =
            ButtonDefaults.colors(
                containerColor = if (on) Glowfin.Dim else Glowfin.Raise,
                contentColor = if (on) Glowfin.Ink else Glowfin.Text,
                focusedContainerColor = Glowfin.Text,
                focusedContentColor = Glowfin.Ink,
            ),
        scale = ButtonDefaults.scale(focusedScale = 1f),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (face != null) WhoAskedFace(face, 22.dp)
            Text(label, fontSize = BenflixType.small, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}

/** Everyone / one person, then All / one kind, then Auto-yes */
@Composable
internal fun ManageFilters(
    people: List<Pair<String, Int>>,
    who: String?,
    onWho: (String?) -> Unit,
    kinds: List<String>,
    kind: String?,
    onKind: (String?) -> Unit,
    onAutoYes: () -> Unit,
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        item(key = "everyone") { ManageChip("Everyone", who == null, { onWho(null) }) }
        items(people, key = { "who-${it.first}" }) { (name, open) ->
            ManageChip("$name  $open", who == name, { onWho(name) }, face = name)
        }
        if (kinds.size > 1) {
            item(key = "all-kinds") { ManageChip("All kinds", kind == null, { onKind(null) }) }
            items(kinds, key = { "kind-$it" }) { k -> ManageChip(k, kind == k, { onKind(k) }) }
        }
        item(key = "auto-yes") { ManageChip("Auto-yes", false, onAutoYes) }
    }
}

/** A plain button for the bulk line and the extra row buttons */
@Composable
internal fun ManageButton(
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        scale = ButtonDefaults.scale(focusedScale = 1f),
    ) { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false) }
}

/** The square you tick while picking several */
@Composable
internal fun ManageTick(
    on: Boolean,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        shape = ButtonDefaults.shape(shape = RoundedCornerShape(8.dp)),
        border =
            com.github.damontecres.wholphin.ui.theme
                .glowfinButtonBorder(RoundedCornerShape(8.dp)),
        colors =
            ButtonDefaults.colors(
                containerColor = if (on) Glowfin.Text else Glowfin.Raise,
                contentColor = Glowfin.Ink,
                focusedContainerColor = Glowfin.Text,
                focusedContentColor = Glowfin.Ink,
            ),
        scale = ButtonDefaults.scale(focusedScale = 1f),
        contentPadding = PaddingValues(0.dp),
        modifier = Modifier.size(44.dp),
    ) {
        Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
            Text(if (on) "✓" else "", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
        }
    }
}

/** What a stuck row says under it, and the Tower calls behind Stop, Look again and Changed my mind */
@HiltViewModel
class ManageViewModel
    @Inject
    constructor(
        private val helper: GameLibraryClient,
        @param:ApplicationContext private val context: Context,
    ) : ViewModel() {
        private val _holds = MutableStateFlow<Map<String, String>>(emptyMap())

        /** "movie:123" -> "14 copies found, all filmed in a cinema." (only answers at home) */
        val holds: StateFlow<Map<String, String>> = _holds

        fun loadHold(
            kind: String,
            tmdb: Int,
        ) {
            val key = "$kind:$tmdb"
            if (key in _holds.value) return
            _holds.update { it + (key to "") }
            viewModelScope.launch {
                try {
                    val line = helper.getJson("/api/admin/holdup?kind=$kind&tmdb=$tmdb").optString("line")
                    if (line.isNotBlank()) _holds.update { it + (key to line) }
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    // Away from home, or Tower not updated yet: the row just has no reason line
                    Timber.d(ex, "Manage: no hold-up line for %s", key)
                }
            }
        }

        /** Stop a request that already had a yes. [delete] also removes what already arrived. */
        fun stop(
            requestId: Int,
            delete: Boolean,
            onDone: () -> Unit,
        ) {
            viewModelScope.launch {
                try {
                    val answer = helper.postJson("/api/asks/stop", JSONObject().put("id", requestId).put("delete", delete))
                    showToast(context, answer.optString("message").ifBlank { "Stopped." })
                    onDone()
                    DeclineWhy.ask(requestId)
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.w(ex, "Manage: couldn't stop request %s", requestId)
                    showToast(context, "Tower didn't stop that one. It may still be updating itself, so try again in an hour.")
                }
            }
        }

        /** Changed my mind: a request Ben said no to goes back to a yes */
        fun undo(
            requestId: Int,
            onDone: () -> Unit,
        ) {
            viewModelScope.launch {
                try {
                    val answer = helper.postJson("/api/asks/undo", JSONObject().put("id", requestId))
                    showToast(context, answer.optString("message").ifBlank { "Back to a yes. Tower starts looking now." })
                    onDone()
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.w(ex, "Manage: couldn't bring back request %s", requestId)
                    showToast(context, "Tower didn't take that. It may still be updating itself, so try again in an hour.")
                }
            }
        }

        /** Look again now (Radarr / Sonarr search this minute). Only works at home. */
        fun again(
            kind: String,
            tmdb: Int,
        ) {
            viewModelScope.launch {
                try {
                    val answer = helper.postJson("/api/admin/holdup/search?kind=$kind&tmdb=$tmdb")
                    showToast(context, answer.optString("message").ifBlank { "Looking again." })
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.w(ex, "Manage: couldn't search again for %s %s", kind, tmdb)
                    showToast(context, "Couldn't start a new search. This one only works on your home Wi-Fi.")
                }
            }
        }
    }
