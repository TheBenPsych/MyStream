package com.github.damontecres.wholphin.ui.main

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ListItem
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Switch
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.services.BenflixBedtime
import com.github.damontecres.wholphin.services.BenflixKidsLog
import com.github.damontecres.wholphin.services.BenflixScreenTime
import com.github.damontecres.wholphin.services.GuestMode
import com.github.damontecres.wholphin.services.KidsMode
import com.github.damontecres.wholphin.services.KidsWatch
import com.github.damontecres.wholphin.ui.components.BasicDialog
import com.github.damontecres.wholphin.ui.setup.PinEntryCreate
import com.github.damontecres.wholphin.ui.theme.BenflixColors
import com.github.damontecres.wholphin.ui.theme.BenflixType
import com.github.damontecres.wholphin.ui.tryRequestFocus
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private enum class ParentalStep { CHECK, MENU, PIN }

/**
 * Benflix Parental controls: the parent PIN, the "Big kids" tile and "What Kids watched
 * this week". When a parent PIN is set, it has to be entered first.
 */
@Composable
fun ParentalControlsDialog(
    onDismiss: () -> Unit,
    viewModel: BedtimeViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val hasPin by viewModel.hasPin.collectAsState()
    var checked by remember { mutableStateOf(false) }
    // Benflix: nothing opens until the parent PIN has been read. If it can't be read, that's not "no PIN".
    var pinTries by remember { mutableIntStateOf(0) }
    var pinLoaded by remember { mutableStateOf(false) }
    var pinError by remember { mutableStateOf(false) }
    LaunchedEffect(pinTries) {
        pinError = false
        val state = viewModel.loadPin()
        pinLoaded = state != null
        pinError = state == null
    }
    var step by remember { mutableStateOf(ParentalStep.MENU) }
    if (!pinLoaded) {
        BasicDialog(onDismissRequest = onDismiss) {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.widthIn(max = 480.dp).padding(20.dp),
            ) {
                Text("Parental controls", fontSize = BenflixType.rowName, fontWeight = FontWeight.Bold)
                if (pinError) {
                    val retryFocus = remember { FocusRequester() }
                    LaunchedEffect(Unit) { retryFocus.tryRequestFocus() }
                    Text("Couldn't check the parent PIN, try again")
                    ListItem(
                        selected = false,
                        onClick = { pinTries++ },
                        headlineContent = { Text("Try again") },
                        modifier = Modifier.focusRequester(retryFocus),
                        scale = ListItemDefaults.scale(focusedScale = 1f),
                    )
                } else {
                    Text("Checking the parent PIN…")
                }
            }
        }
        return
    }
    val current = if (hasPin && !checked && step != ParentalStep.PIN) ParentalStep.CHECK else step

    if (current == ParentalStep.PIN) {
        ParentPinDialog(
            hasPin = hasPin,
            checkPin = viewModel::checkPin,
            setPin = viewModel::setPin,
            onDone = {
                checked = true
                step = ParentalStep.MENU
            },
        )
        return
    }
    BasicDialog(onDismissRequest = onDismiss) {
        when (current) {
            ParentalStep.CHECK -> {
                PinEntryCreate(
                    title = R.string.enter_pin,
                    onTextChange = {},
                    onConfirm = { pin ->
                        scope.launch {
                            if (viewModel.checkPin(pin)) {
                                checked = true
                            } else {
                                Toast.makeText(context, context.getString(R.string.incorrect), Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    modifier = Modifier.padding(16.dp),
                )
            }

            ParentalStep.MENU -> {
                val focus = remember { FocusRequester() }
                LaunchedEffect(Unit) { focus.tryRequestFocus() }
                var bigKids by remember { mutableStateOf(KidsMode.bigKidsTile(context)) }
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier =
                        Modifier
                            .widthIn(max = 640.dp)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(20.dp),
                ) {
                    Text("Parental controls", fontSize = BenflixType.rowName, fontWeight = FontWeight.Bold)
                    ListItem(
                        selected = false,
                        onClick = { step = ParentalStep.PIN },
                        headlineContent = { Text(if (hasPin) "Parent PIN: on" else "Parent PIN: off") },
                        supportingContent = {
                            Text(
                                if (hasPin) {
                                    "Kids goes to bed at ${BenflixBedtime.label()}. Change or turn off the PIN."
                                } else {
                                    "Set one to put Kids to bed at ${BenflixBedtime.label()}. Without it, Kids stays unlocked."
                                },
                            )
                        },
                        modifier = Modifier.focusRequester(focus),
                        scale = ListItemDefaults.scale(focusedScale = 1f),
                    )
                    ListItem(
                        selected = false,
                        onClick = {
                            bigKids = !bigKids
                            KidsMode.setBigKidsTile(context, bigKids)
                        },
                        headlineContent = { Text("Big kids tile") },
                        supportingContent = { Text("A second Kids tile on Who's watching that allows up to PG-13, for older children") },
                        trailingContent = { Switch(checked = bigKids, onCheckedChange = null) },
                        scale = ListItemDefaults.scale(focusedScale = 1f),
                    )
                    // Kids screen time: press to step through Off, 1, 2 and 3 hours a day
                    val dailyMinutes by viewModel.dailyMinutes.collectAsState()
                    ListItem(
                        selected = false,
                        onClick = {
                            val choices = BenflixScreenTime.CHOICES
                            val next = choices[(choices.indexOf(dailyMinutes) + 1).mod(choices.size)]
                            scope.launch {
                                if (!viewModel.setDailyMinutes(next)) {
                                    Toast.makeText(context, "Couldn't save that. Try again.", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        headlineContent = { Text("Kids screen time: ${BenflixScreenTime.label(dailyMinutes)}") },
                        supportingContent = {
                            Text(
                                when {
                                    dailyMinutes == 0 -> {
                                        "Press to set how long Kids can watch each day"
                                    }

                                    !hasPin -> {
                                        "Needs a parent PIN to lock Kids when time is up"
                                    }

                                    else -> {
                                        "Used today on this TV or phone: ${BenflixScreenTime.watchedMinutesToday()} min. " +
                                            "Your PIN adds 30 minutes."
                                    }
                                },
                            )
                        },
                        scale = ListItemDefaults.scale(focusedScale = 1f),
                    )
                    var guestTile by remember { mutableStateOf(GuestMode.tileOn(context)) }
                    ListItem(
                        selected = false,
                        onClick = {
                            guestTile = !guestTile
                            GuestMode.setTileOn(context, guestTile)
                        },
                        headlineContent = { Text("Guest tile") },
                        supportingContent = { Text("For visitors: they can watch anything, and nothing is saved to your account") },
                        trailingContent = { Switch(checked = guestTile, onCheckedChange = null) },
                        scale = ListItemDefaults.scale(focusedScale = 1f),
                    )
                    // Benflix Books: which books Kids may see (kids-rated books always show)
                    val booksNav =
                        androidx.hilt.lifecycle.viewmodel.compose
                            .hiltViewModel<com.github.damontecres.wholphin.ui.books.BooksKidsViewModel>()
                    ListItem(
                        selected = false,
                        onClick = {
                            onDismiss()
                            booksNav.navigationManager.navigateTo(com.github.damontecres.wholphin.ui.nav.Destination.BooksKids)
                        },
                        headlineContent = { Text("Kids books") },
                        supportingContent = { Text("Kids see books rated for kids. Pick any others they may read or listen to") },
                        scale = ListItemDefaults.scale(focusedScale = 1f),
                    )
                    KidsWatchedSection(posterUrl = viewModel::posterUrl)
                }
            }

            ParentalStep.PIN -> {}
        }
    }
}

/**
 * "What Kids watched", right in Parental controls: this week's totals, then a row of everything played on Kids
 * on this device (newest first). Move onto (or tap) a poster to see the day and time it was watched.
 */
@Composable
private fun KidsWatchedSection(posterUrl: (String) -> String?) {
    val context = LocalContext.current
    val week = remember { BenflixKidsLog.thisWeek(context) }
    val recent = remember { BenflixKidsLog.recent(context) }
    var picked by remember { mutableStateOf<KidsWatch?>(null) }
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(top = 12.dp),
    ) {
        Text("What Kids watched", fontSize = BenflixType.rowName, fontWeight = FontWeight.Bold)
        if (recent.isEmpty()) {
            Text(
                "Nothing watched on Kids on this TV or phone yet.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }
        val hours = week.minutes / 60
        val minutes = week.minutes % 60
        Text(
            "This week: about ${if (hours > 0) "$hours hr " else ""}$minutes min · " +
                "${week.episodes} ${if (week.episodes == 1) "episode" else "episodes"} · " +
                "${week.movies} ${if (week.movies == 1) "movie" else "movies"}",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 8.dp, horizontal = 4.dp),
        ) {
            items(recent, key = { "${it.time}-${it.titleId}" }) { watch ->
                val url = remember(watch.titleId) { posterUrl(watch.titleId) }
                Surface(
                    onClick = { picked = watch },
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                    border =
                        ClickableSurfaceDefaults.border(
                            focusedBorder = Border(BorderStroke(3.dp, BenflixColors.Text), shape = RoundedCornerShape(10.dp)),
                        ),
                    modifier =
                        Modifier
                            .size(width = 84.dp, height = 126.dp)
                            .onFocusChanged { if (it.isFocused) picked = watch },
                ) {
                    Box(Modifier.fillMaxSize().background(Color(0xFF24242A))) {
                        Text(
                            watch.title,
                            fontSize = BenflixType.tag,
                            color = Color.White,
                            maxLines = 3,
                            modifier = Modifier.padding(6.dp),
                        )
                        AsyncImage(
                            model = url,
                            contentDescription = watch.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }
        val shown = picked ?: recent.first()
        Text(
            text = "${shown.title} · ${watchedWhen(shown.time)}",
            fontSize = BenflixType.body,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
    }
}

/** "Today at 4:32 PM", "Yesterday at 7:05 PM" or "Tuesday, Nov 2 at 4:32 PM" */
private fun watchedWhen(time: Long): String {
    val zone = ZoneId.systemDefault()
    val then = Instant.ofEpochMilli(time).atZone(zone)
    val today = LocalDate.now(zone)
    val clock = then.format(DateTimeFormatter.ofPattern("h:mm a"))
    val day =
        when (then.toLocalDate()) {
            today -> "Today"
            today.minusDays(1) -> "Yesterday"
            else -> then.format(DateTimeFormatter.ofPattern("EEEE, MMM d"))
        }
    return "$day at $clock"
}
