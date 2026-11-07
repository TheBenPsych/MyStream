package com.github.damontecres.wholphin.ui.main

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.services.BenflixWatchTime
import com.github.damontecres.wholphin.services.KidsMode
import com.github.damontecres.wholphin.services.weekStart
import com.github.damontecres.wholphin.ui.theme.BenflixColors
import com.github.damontecres.wholphin.util.WholphinDispatchers
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import java.time.LocalDate
import javax.inject.Inject

/** One door's part of this week's watching */
data class DoorTime(
    val name: String,
    val color: Color,
    val seconds: Long,
)

@HiltViewModel
class WeekWatchingViewModel
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val serverRepository: ServerRepository,
        private val watchTime: BenflixWatchTime,
    ) : ViewModel() {
        private val _doors = MutableStateFlow<List<DoorTime>?>(null)

        /** This week (since Monday) per door, or null while loading */
        val doors: StateFlow<List<DoorTime>?> = _doors

        init {
            viewModelScope.launch(WholphinDispatchers.IO) {
                val userId = serverRepository.currentUser?.id ?: return@launch
                try {
                    val week = watchTime.read(userId).week()
                    _doors.value =
                        listOf(
                            DoorTime("Movies", BenflixColors.Movies, week["movies"] ?: 0L),
                            DoorTime("Shows", BenflixColors.Shows, week["shows"] ?: 0L),
                            DoorTime("Live TV", BenflixColors.LiveTv, week["livetv"] ?: 0L),
                            DoorTime("Games", BenflixColors.Games, gamesThisWeekMs() / 1000L),
                        )
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.w(ex, "Week watching")
                }
            }
        }

        /**
         * Games run in their own part of the app, which keeps "played today" per game in its own saved
         * file (played_<date>_<game>). This adds up this week's days from that file (this TV or phone only).
         */
        private fun gamesThisWeekMs(): Long {
            val file = File(context.applicationInfo.dataDir, "shared_prefs/game_player.xml")
            if (!file.exists()) return 0L
            val start = weekStart()
            return PLAYED
                .findAll(file.readText())
                .sumOf { m ->
                    val day = runCatching { LocalDate.parse(m.groupValues[1]) }.getOrNull()
                    if (day != null && !day.isBefore(start)) m.groupValues[2].toLongOrNull() ?: 0L else 0L
                }
        }
    }

private val PLAYED = Regex("""name="played_(\d{4}-\d{2}-\d{2})_[^"]*" value="(\d+)"""")

/**
 * Benflix redesign (My watching): the big number for this week, and a bar in the four door colours
 * showing how it splits between Movies, Shows, Live TV and Games. Only your own numbers.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WeekWatchingCard(
    modifier: Modifier = Modifier,
    viewModel: WeekWatchingViewModel = hiltViewModel(),
) {
    val kids by KidsMode.active.collectAsState()
    if (kids) return
    val doors by viewModel.doors.collectAsState()
    val list = doors ?: return
    val total = list.sumOf { it.seconds }
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier =
            modifier
                .widthIn(max = 720.dp)
                .background(BenflixColors.Panel, RoundedCornerShape(14.dp))
                .padding(horizontal = 18.dp, vertical = 14.dp),
    ) {
        Text(
            text = "THIS WEEK",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.3.sp,
            color = BenflixColors.Muted,
        )
        Text(
            text = if (total < 60) "Nothing yet" else timeWords(total),
            fontSize = 40.sp,
            fontWeight = FontWeight.ExtraBold,
            color = BenflixColors.Text,
        )
        if (total >= 60) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(12.dp)
                        .clip(RoundedCornerShape(50))
                        .background(BenflixColors.Raised),
            ) {
                list.filter { it.seconds > 0 }.forEach { door ->
                    Box(
                        Modifier
                            .weight(door.seconds.toFloat())
                            .fillMaxHeight()
                            .background(door.color),
                    )
                }
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                list.filter { it.seconds >= 60 }.forEach { door ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(
                            Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(door.color),
                        )
                        Text("${door.name} ${timeWords(door.seconds)}", fontSize = 14.sp, color = BenflixColors.Soft)
                    }
                }
            }
        }
    }
}

/** "3 h 20 m" or "45 m" */
private fun timeWords(seconds: Long): String {
    val minutes = seconds / 60
    return if (minutes >= 60) "${minutes / 60} h ${minutes % 60} m" else "$minutes m"
}
