package com.github.damontecres.wholphin.ui.main

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.services.BenflixBedtime
import com.github.damontecres.wholphin.services.BenflixParentPin
import com.github.damontecres.wholphin.services.KidsMode
import com.github.damontecres.wholphin.ui.components.BasicDialog
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.setup.PinEntryCreate
import com.github.damontecres.wholphin.ui.theme.BenflixTitleFont
import com.github.damontecres.wholphin.util.WholphinDispatchers
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class BedtimeViewModel
    @Inject
    constructor(
        private val serverRepository: ServerRepository,
        private val parentPin: BenflixParentPin,
    ) : ViewModel() {
        /** True when it's bedtime in Kids (and a parent PIN is set) */
        private val _locked = MutableStateFlow(false)
        val locked: StateFlow<Boolean> = _locked

        private val _hasPin = MutableStateFlow(false)
        val hasPin: StateFlow<Boolean> = _hasPin

        /** True while the "15 minutes until bedtime" note is showing */
        private val _windDown = MutableStateFlow(false)
        val windDown: StateFlow<Boolean> = _windDown

        init {
            viewModelScope.launch {
                var lastUser: java.util.UUID? = null
                while (isActive) {
                    val userId = serverRepository.currentUser?.id
                    if (userId != null && userId != lastUser) {
                        lastUser = userId
                        refreshPin()
                    }
                    _locked.value = BenflixBedtime.lockedNow()
                    if (BenflixBedtime.windDownNow()) {
                        BenflixBedtime.windDownShownOn = java.time.LocalDate.now()
                        launch {
                            _windDown.value = true
                            delay(WIND_DOWN_SHOW_MS)
                            _windDown.value = false
                        }
                    }
                    delay(CHECK_EVERY_MS)
                }
            }
        }

        fun refreshPin() {
            val userId = serverRepository.currentUser?.id ?: return
            viewModelScope.launch(WholphinDispatchers.IO) {
                val has = parentPin.hasPin(userId)
                BenflixBedtime.pinSet = has
                _hasPin.value = has
                _locked.value = BenflixBedtime.lockedNow()
            }
        }

        suspend fun checkPin(pin: String): Boolean {
            val userId = serverRepository.currentUser?.id ?: return false
            return withContext(WholphinDispatchers.IO) { parentPin.check(userId, pin) }
        }

        suspend fun setPin(pin: String?): Boolean {
            val userId = serverRepository.currentUser?.id ?: return false
            return try {
                withContext(WholphinDispatchers.IO) { parentPin.set(userId, pin) }
                _hasPin.value = !pin.isNullOrBlank()
                true
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                Timber.w(ex, "Couldn't save the parent PIN")
                false
            }
        }

        fun moreTime() {
            BenflixBedtime.giveMoreTime()
            _locked.value = false
        }

        companion object {
            private const val CHECK_EVERY_MS = 20_000L
            private const val WIND_DOWN_SHOW_MS = 15_000L
        }
    }

/**
 * Sits on top of every page. At Kids bedtime (with a parent PIN set) it shows "Time for bed!".
 * If something is playing, it waits for it to finish first.
 */
@Composable
fun BedtimeLayer(
    currentDestination: Destination?,
    viewModel: BedtimeViewModel = hiltViewModel(),
) {
    val locked by viewModel.locked.collectAsState()
    val kids by KidsMode.active.collectAsState()
    LaunchedEffect(kids) { if (kids) viewModel.refreshPin() }
    val playing = currentDestination is Destination.Playback || currentDestination is Destination.PlaybackList
    val windDown by viewModel.windDown.collectAsState()
    // "15 minutes until bedtime": a small note in the corner, even while watching
    Box(contentAlignment = Alignment.TopEnd, modifier = Modifier.fillMaxSize()) {
        AnimatedVisibility(visible = kids && windDown, enter = fadeIn(), exit = fadeOut()) {
            Column(
                modifier =
                    Modifier
                        .padding(24.dp)
                        .widthIn(max = 380.dp)
                        .background(Color(0xEE141418), RoundedCornerShape(12.dp))
                        .padding(horizontal = 18.dp, vertical = 12.dp),
            ) {
                Text("15 minutes until bedtime", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text("Time to pick your last show!", fontSize = 15.sp, color = Color(0xFFD8D8F0))
            }
        }
    }
    if (!kids || !locked || playing) return

    var askPin by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val focusRequester = remember { FocusRequester() }
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false, usePlatformDefaultWidth = false),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(listOf(Color(0xFF0B1030), Color(0xFF1B1446), Color(0xFF050510)))),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.widthIn(max = 640.dp).padding(24.dp),
            ) {
                Text(text = "TIME FOR BED!", fontFamily = BenflixTitleFont, fontSize = 64.sp, color = Color.White)
                Text(
                    text = "Benflix is sleeping too. See you in the morning!",
                    fontSize = 20.sp,
                    color = Color(0xFFD8D8F0),
                    textAlign = TextAlign.Center,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 16.dp)) {
                    Button(onClick = { askPin = true }, modifier = Modifier.focusRequester(focusRequester)) {
                        Text("Grown-up: 30 more minutes", fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
        LaunchedEffect(Unit) {
            delay(200)
            try {
                focusRequester.requestFocus()
            } catch (_: IllegalStateException) {
            }
        }
        if (askPin) {
            BasicDialog(onDismissRequest = { askPin = false }) {
                PinEntryCreate(
                    title = R.string.enter_pin,
                    onTextChange = {},
                    onConfirm = { pin ->
                        scope.launch {
                            if (viewModel.checkPin(pin)) {
                                askPin = false
                                viewModel.moreTime()
                            } else {
                                Toast.makeText(context, context.getString(R.string.incorrect), Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
    }
}

/**
 * Set, change or turn off the parent PIN. If there already is one, it has to be entered first.
 * [onDone] gets true when a PIN is now set.
 */
@Composable
fun ParentPinDialog(
    hasPin: Boolean,
    checkPin: suspend (String) -> Boolean,
    setPin: suspend (String?) -> Boolean,
    onDone: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var step by remember { mutableStateOf(if (hasPin) ParentPinStep.CHECK else ParentPinStep.NEW) }
    var pending by remember { mutableStateOf("") }

    fun toast(text: String) = Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
    BasicDialog(onDismissRequest = { onDone(hasPin) }) {
        when (step) {
            ParentPinStep.CHECK -> {
                PinEntryCreate(
                    title = R.string.enter_pin,
                    onTextChange = {},
                    onConfirm = { pin ->
                        scope.launch { if (checkPin(pin)) step = ParentPinStep.CHOOSE else toast(context.getString(R.string.incorrect)) }
                    },
                    modifier = Modifier.padding(16.dp),
                )
            }

            ParentPinStep.CHOOSE -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(24.dp),
                ) {
                    Text("Parent PIN", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = { step = ParentPinStep.NEW }) { Text("Change PIN") }
                        Button(onClick = {
                            scope.launch {
                                if (setPin(null)) {
                                    toast("Parent PIN is off. Kids stays unlocked at bedtime.")
                                    onDone(false)
                                }
                            }
                        }) { Text("Turn off") }
                    }
                }
            }

            ParentPinStep.NEW -> {
                PinEntryCreate(
                    title = R.string.enter_pin,
                    onTextChange = {},
                    onConfirm = { pin ->
                        if (pin.length >= MIN_PIN) {
                            pending = pin
                            step = ParentPinStep.CONFIRM
                        } else {
                            toast(context.getString(R.string.pin_too_short))
                        }
                    },
                    modifier = Modifier.padding(16.dp),
                )
            }

            ParentPinStep.CONFIRM -> {
                PinEntryCreate(
                    title = R.string.confirm_pin,
                    onTextChange = {},
                    onConfirm = { pin ->
                        if (pin == pending) {
                            scope.launch {
                                if (setPin(pin)) {
                                    toast("Parent PIN saved. Kids goes to bed at 9 PM.")
                                    onDone(true)
                                } else {
                                    toast("Couldn't save the PIN. Try again.")
                                }
                            }
                        } else {
                            toast(context.getString(R.string.incorrect))
                            step = ParentPinStep.NEW
                        }
                    },
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
    }
}

private enum class ParentPinStep { CHECK, CHOOSE, NEW, CONFIRM }

private const val MIN_PIN = 4
