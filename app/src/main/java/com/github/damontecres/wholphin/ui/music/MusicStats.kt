package com.github.damontecres.wholphin.ui.music

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.services.BenflixWatchTime
import com.github.damontecres.wholphin.services.MusicLibrary
import com.github.damontecres.wholphin.services.weekStart
import com.github.damontecres.wholphin.ui.theme.BenflixColors
import com.github.damontecres.wholphin.ui.theme.BenflixType
import com.github.damontecres.wholphin.util.WholphinDispatchers
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.LocalDate
import javax.inject.Inject

/** Your music on My watching: minutes per week (last 8 weeks, oldest first), top artists and top songs */
data class MusicStats(
    val weeks: List<Pair<LocalDate, Long>>,
    val topArtists: List<String>,
    val topSongs: List<Pair<String, String?>>,
) {
    val thisWeekMinutes: Long get() = (weeks.lastOrNull()?.second ?: 0L) / 60L
    val empty: Boolean get() = weeks.all { it.second < 60 } && topSongs.isEmpty()
}

@HiltViewModel
class MusicStatsViewModel
    @Inject
    constructor(
        private val serverRepository: ServerRepository,
        private val watchTime: BenflixWatchTime,
        private val library: MusicLibrary,
    ) : ViewModel() {
        private val _stats = MutableStateFlow<MusicStats?>(null)
        val stats: StateFlow<MusicStats?> = _stats

        init {
            viewModelScope.launch(WholphinDispatchers.IO) {
                val userId = serverRepository.currentUser?.id ?: return@launch
                try {
                    val time = watchTime.read(userId)
                    val start = weekStart()
                    val weeks = (7 downTo 0).map { back -> start.minusWeeks(back.toLong()).let { it to (time.week(it)["music"] ?: 0L) } }
                    val songs = library.mostPlayedSongs(200)
                    val artists =
                        songs
                            .groupBy { it.data.albumArtist ?: it.data.artists?.firstOrNull() }
                            .filterKeys { !it.isNullOrBlank() }
                            .mapValues { (_, list) -> list.sumOf { it.data.userData?.playCount ?: 1 } }
                            .entries
                            .sortedByDescending { it.value }
                            .take(5)
                            .mapNotNull { it.key }
                    val top = songs.take(5).map { (it.title ?: it.name.orEmpty()) to it.data.artists?.joinToString(", ") }
                    _stats.value = MusicStats(weeks, artists, top)
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.w(ex, "Music stats")
                }
            }
        }
    }

/**
 * Benflix music on My watching (approved mw-section): minutes this week with a small violet chart of the
 * last 8 weeks, then your top artists and top songs. Only your own listening.
 */
@Composable
fun MusicStatsCard(
    modifier: Modifier = Modifier,
    viewModel: MusicStatsViewModel = hiltViewModel(),
) {
    val stats by viewModel.stats.collectAsState()
    val s = stats ?: return
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier =
            modifier
                .widthIn(max = 720.dp)
                .background(BenflixColors.Panel, RoundedCornerShape(16.dp))
                .padding(horizontal = 18.dp, vertical = 14.dp),
    ) {
        Text("MUSIC", fontSize = BenflixType.tag, fontWeight = FontWeight.Bold, letterSpacing = 1.3.sp, color = BenflixColors.Music)
        if (s.empty) {
            Text(
                "Nothing played yet. Your songs and playlists are behind the violet door.",
                fontSize = BenflixType.body,
                color = BenflixColors.Soft,
            )
            return@Column
        }
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            Column {
                Text("${s.thisWeekMinutes} min", fontSize = BenflixType.hero, fontWeight = FontWeight.ExtraBold, color = BenflixColors.Text)
                Text("this week", fontSize = BenflixType.small, color = BenflixColors.Muted)
            }
            // Minutes per week, oldest on the left, this week brightest
            val most = (s.weeks.maxOfOrNull { it.second } ?: 0L).coerceAtLeast(1L)
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                modifier = Modifier.height(56.dp),
            ) {
                s.weeks.forEachIndexed { i, (_, secs) ->
                    Box(
                        Modifier
                            .size(width = 12.dp, height = (6f + 50f * secs / most).dp)
                            .background(
                                if (i == s.weeks.lastIndex) BenflixColors.Music else BenflixColors.Music.copy(alpha = 0.4f),
                                RoundedCornerShape(3.dp),
                            ),
                    )
                }
            }
        }
        if (s.topArtists.isNotEmpty()) {
            Text("Top artists", fontSize = BenflixType.small, fontWeight = FontWeight.Bold, color = BenflixColors.Muted)
            Text(
                s.topArtists.joinToString(" · "),
                fontSize = BenflixType.body,
                color = BenflixColors.Text,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (s.topSongs.isNotEmpty()) {
            Text("Top songs", fontSize = BenflixType.small, fontWeight = FontWeight.Bold, color = BenflixColors.Muted)
            s.topSongs.forEachIndexed { i, (song, artist) ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    Text("${i + 1}", fontSize = BenflixType.body, fontWeight = FontWeight.Bold, color = BenflixColors.Music)
                    Text(
                        listOfNotNull(song, artist).joinToString(" · "),
                        fontSize = BenflixType.body,
                        color = BenflixColors.Text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
