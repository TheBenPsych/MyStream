package com.github.damontecres.wholphin.ui.main

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Switch
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.services.BenflixKidsLog
import com.github.damontecres.wholphin.services.KidsMode
import com.github.damontecres.wholphin.ui.components.BasicDialog
import com.github.damontecres.wholphin.ui.setup.PinEntryCreate
import com.github.damontecres.wholphin.ui.tryRequestFocus
import kotlinx.coroutines.launch

private enum class ParentalStep { CHECK, MENU, PIN, WEEK }

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
    LaunchedEffect(Unit) { viewModel.refreshPin() }
    var step by remember { mutableStateOf(ParentalStep.MENU) }
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
                    modifier = Modifier.widthIn(max = 520.dp).fillMaxWidth().padding(20.dp),
                ) {
                    Text("Parental controls", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    ListItem(
                        selected = false,
                        onClick = { step = ParentalStep.PIN },
                        headlineContent = { Text(if (hasPin) "Parent PIN: on" else "Parent PIN: off") },
                        supportingContent = {
                            Text(
                                if (hasPin) {
                                    "Kids goes to bed at 9 PM. Change or turn off the PIN."
                                } else {
                                    "Set one to put Kids to bed at 9 PM. Without it, Kids stays unlocked."
                                },
                            )
                        },
                        modifier = Modifier.focusRequester(focus),
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
                    )
                    ListItem(
                        selected = false,
                        onClick = { step = ParentalStep.WEEK },
                        headlineContent = { Text("What Kids watched this week") },
                        supportingContent = { Text("On this TV or phone") },
                    )
                }
            }

            ParentalStep.WEEK -> {
                val week = remember { BenflixKidsLog.thisWeek(context) }
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.widthIn(max = 520.dp).fillMaxWidth().padding(20.dp),
                ) {
                    Text("Kids this week", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    if (week.episodes + week.movies == 0) {
                        Text("Nothing watched on Kids on this device in the last 7 days.")
                    } else {
                        val hours = week.minutes / 60
                        val minutes = week.minutes % 60
                        Text(
                            "About ${if (hours > 0) "$hours hr " else ""}$minutes min · " +
                                "${week.episodes} episodes · ${week.movies} movies",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        week.top.forEach { (title, times) ->
                            ListItem(
                                selected = false,
                                onClick = {},
                                headlineContent = { Text(title) },
                                trailingContent = { Text(if (times == 1) "once" else "$times times") },
                            )
                        }
                    }
                    ListItem(selected = false, onClick = { step = ParentalStep.MENU }, headlineContent = { Text("Back") })
                }
            }

            ParentalStep.PIN -> {}
        }
    }
}
