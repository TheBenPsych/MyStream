package com.github.damontecres.wholphin.ui.setup

import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.ui.components.BenflixIcon
import com.github.damontecres.wholphin.ui.components.BenflixIconImage
import com.github.damontecres.wholphin.ui.components.BenflixWordLogo
import com.github.damontecres.wholphin.ui.components.QrCode
import com.github.damontecres.wholphin.ui.door.doorFocusBorder
import com.github.damontecres.wholphin.ui.theme.BenflixColors
import com.github.damontecres.wholphin.ui.theme.BenflixSpace
import com.github.damontecres.wholphin.ui.theme.BenflixType
import com.github.damontecres.wholphin.ui.touch.rememberIsTv
import com.github.damontecres.wholphin.ui.tryRequestFocus
import kotlinx.coroutines.delay
import timber.log.Timber

/*
 * Benflix redesign (approved 2026-11-07, s2-kb-below): signing in is one question per screen, your
 * name and then your password, with the Benflix keyboard underneath on TVs (phones use their own
 * keyboard). A big "Sign in with my phone" button opens Quick Connect (QR code + code). Neutral
 * greys and white; a soft red line under the box when something is wrong.
 */

private enum class SignInStep { NAME, PASSWORD, PHONE }

/** What the sign-in screens need from the Who's watching page */
class SignInActions(
    val login: (name: String, password: String) -> Unit,
    val startPhone: () -> Unit,
    val stopPhone: () -> Unit,
    val clearError: () -> Unit,
    val openDebug: () -> Unit,
    val close: () -> Unit,
)

@Composable
fun SignInScreens(
    serverName: String,
    serverUrl: String,
    knownName: String?,
    phoneAllowed: Boolean,
    phoneCode: String?,
    error: String?,
    attempts: Int,
    actions: SignInActions,
) {
    val isTv = rememberIsTv()
    var step by rememberSaveable { mutableStateOf(if (knownName.isNullOrBlank()) SignInStep.NAME else SignInStep.PASSWORD) }
    var name by rememberSaveable { mutableStateOf(knownName.orEmpty()) }
    var password by remember { mutableStateOf("") }
    val goBack = {
        actions.clearError()
        when (step) {
            SignInStep.NAME -> {
                actions.close()
            }

            SignInStep.PASSWORD -> {
                step = SignInStep.NAME
            }

            SignInStep.PHONE -> {
                actions.stopPhone()
                step = SignInStep.NAME
            }
        }
    }
    Dialog(
        onDismissRequest = goBack,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(BenflixColors.Tray),
        ) {
            BenflixWordLogo(
                height = if (isTv) 26.dp else 22.dp,
                modifier =
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(start = if (isTv) BenflixSpace.tvEdge else BenflixSpace.phoneEdge, top = if (isTv) 27.dp else 17.dp),
            )
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier =
                    Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(top = if (isTv) 72.dp else 64.dp, bottom = 24.dp, start = 16.dp, end = 16.dp),
            ) {
                when (step) {
                    SignInStep.NAME -> {
                        Question("What's your name?", "Signing in to $serverName")
                        Answer(
                            value = name,
                            onValue = {
                                name = it
                                actions.clearError()
                            },
                            password = false,
                            error = error,
                            isTv = isTv,
                            onDone = { if (name.isNotBlank()) step = SignInStep.PASSWORD },
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 18.dp),
                        ) {
                            SignInButton("Next", white = true, enabled = name.isNotBlank()) { step = SignInStep.PASSWORD }
                            if (phoneAllowed) {
                                SignInButton("Sign in with my phone", icon = BenflixIcon.PHONE, big = true) {
                                    actions.clearError()
                                    actions.startPhone()
                                    step = SignInStep.PHONE
                                }
                            }
                        }
                    }

                    SignInStep.PASSWORD -> {
                        Question("Hi ${name.trim()}. What's your password?", "Leave it empty if you don't have one")
                        LaunchedEffect(password) { actions.clearError() }
                        Answer(
                            value = password,
                            onValue = { password = it },
                            password = true,
                            error = error,
                            isTv = isTv,
                            onDone = { actions.login(name.trim(), password) },
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier.padding(top = 18.dp),
                        ) {
                            SignInButton("Sign in", white = true) { actions.login(name.trim(), password) }
                            SignInButton("Back") { goBack() }
                        }
                    }

                    SignInStep.PHONE -> {
                        PhoneStep(serverUrl = serverUrl, code = phoneCode, isTv = isTv, error = error)
                        SignInButton("Use my name and password", modifier = Modifier.padding(top = 20.dp)) { goBack() }
                    }
                }
                if (attempts > 2 && step != SignInStep.PHONE) {
                    Text(
                        text = "Trouble signing in?",
                        color = BenflixColors.Muted,
                        fontSize = BenflixType.small,
                        modifier = Modifier.padding(top = 20.dp, bottom = 6.dp),
                    )
                    SignInButton("Show details for Ben") { actions.openDebug() }
                }
            }
        }
    }
}

@Composable
internal fun Question(
    title: String,
    hint: String,
) {
    Text(
        text = title,
        color = BenflixColors.Text,
        fontSize = BenflixType.pageTitle,
        lineHeight = BenflixType.pageTitle * 1.15f,
        fontWeight = FontWeight.ExtraBold,
        textAlign = TextAlign.Center,
    )
    Text(
        text = hint,
        color = BenflixColors.Muted,
        fontSize = BenflixType.body,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(top = 6.dp, bottom = 18.dp),
    )
}

/** The box you type into, its line (white, or soft red when something is wrong), and on TV the keyboard */
@Composable
internal fun Answer(
    value: String,
    onValue: (String) -> Unit,
    password: Boolean,
    error: String?,
    isTv: Boolean,
    onDone: () -> Unit,
    fieldTag: String = "sign_in_text",
) {
    val boxWidth = 520.dp
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.widthIn(max = boxWidth).fillMaxWidth(),
    ) {
        if (isTv) {
            // Dots, with the last letter showing for a moment so you can check it
            var showLast by remember { mutableStateOf(false) }
            LaunchedEffect(value) {
                showLast = password && value.isNotEmpty()
                delay(LAST_LETTER_MS)
                showLast = false
            }
            val shown =
                if (password) {
                    "•".repeat((value.length - if (showLast) 1 else 0).coerceAtLeast(0)) + if (showLast) value.last() else ""
                } else {
                    value
                }
            Text(
                text = shown.ifEmpty { " " },
                color = BenflixColors.Text,
                fontSize = BenflixType.pageTitle,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                textAlign = TextAlign.Center,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
            )
        } else {
            val focus = remember { FocusRequester() }
            LaunchedEffect(password) { focus.tryRequestFocus() }
            BasicTextField(
                value = value,
                onValueChange = onValue,
                singleLine = true,
                textStyle =
                    TextStyle(
                        color = BenflixColors.Text,
                        fontSize = BenflixType.rowName,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    ),
                cursorBrush = SolidColor(BenflixColors.Text),
                visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
                keyboardOptions =
                    KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        autoCorrectEnabled = false,
                        keyboardType = if (password) KeyboardType.Password else KeyboardType.Text,
                        imeAction = if (password) ImeAction.Go else ImeAction.Next,
                    ),
                keyboardActions = KeyboardActions(onGo = { onDone() }, onNext = { onDone() }),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                        .focusRequester(focus)
                        .testTag(fieldTag),
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(if (error != null) ERROR_RED else BenflixColors.Soft),
        )
        if (error != null) {
            Text(
                text = error,
                color = ERROR_RED,
                fontSize = BenflixType.small,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
    if (isTv) {
        SignInKeyboard(
            value = value,
            onValue = onValue,
            onDone = onDone,
            // No talking your password out loud
            voice = !password,
            modifier = Modifier.padding(top = 18.dp),
        )
    }
}

/** Quick Connect: scan the code (TV) and type the six numbers on a phone that's signed in */
@Composable
private fun PhoneStep(
    serverUrl: String,
    code: String?,
    isTv: Boolean,
    error: String?,
) {
    Question("Sign in with my phone", "No typing on the TV")
    Row(
        horizontalArrangement = Arrangement.spacedBy(28.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isTv) {
            Box(
                Modifier
                    .background(Color.White, RoundedCornerShape(10.dp))
                    .padding(10.dp),
            ) {
                QrCode(text = serverUrl.trimEnd('/') + "/web/#/quickconnect", modifier = Modifier.size(170.dp))
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.widthIn(max = 420.dp)) {
            if (isTv) Line("1. Scan this with your phone's camera and sign in")
            Line(
                (if (isTv) "2. " else "") + "Type this code in Quick Connect (or in Benflix on a signed-in phone: Settings, Quick Connect)",
            )
            Text(
                text = code?.chunked(3)?.joinToString(" ") ?: "Getting a code…",
                color = BenflixColors.Text,
                fontSize = if (code != null) BenflixType.hero else BenflixType.rowName,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 4.sp,
            )
            Line("Waiting for your phone…", BenflixColors.Muted)
            if (error != null) Line(error, ERROR_RED)
        }
    }
}

@Composable
private fun Line(
    text: String,
    color: Color = BenflixColors.Soft,
) {
    Text(text = text, color = color, fontSize = BenflixType.body, lineHeight = BenflixType.body * 1.35f)
}

@Composable
internal fun SignInButton(
    label: String,
    modifier: Modifier = Modifier,
    white: Boolean = false,
    big: Boolean = false,
    enabled: Boolean = true,
    icon: BenflixIcon? = null,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(10.dp)
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = ClickableSurfaceDefaults.shape(shape),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = if (white) BenflixColors.Text else BenflixColors.Raised,
                contentColor = if (white) BenflixColors.Tray else BenflixColors.Text,
                focusedContainerColor = if (white) BenflixColors.Text else BenflixColors.FocusRow,
                focusedContentColor = if (white) BenflixColors.Tray else BenflixColors.Text,
                pressedContainerColor = BenflixColors.Soft,
                pressedContentColor = BenflixColors.Tray,
                disabledContainerColor = BenflixColors.Raised,
                disabledContentColor = BenflixColors.Muted,
            ),
        border =
            ClickableSurfaceDefaults.border(
                focusedBorder =
                    androidx.tv.material3.Border(
                        border = androidx.compose.foundation.BorderStroke(3.dp, BenflixColors.Text),
                        inset = (-4).dp,
                        shape = shape,
                    ),
            ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        modifier = modifier.height(if (big) 60.dp else 48.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(horizontal = if (big) 26.dp else 22.dp).height(if (big) 60.dp else 48.dp),
        ) {
            if (icon != null) {
                BenflixIconImage(icon, color = if (white) BenflixColors.Tray else BenflixColors.Text, size = 26.dp)
            }
            Text(text = label, fontSize = if (big) BenflixType.lead else BenflixType.body, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}

private val NUMBERS = "1234567890".map { it.toString() }
private val ABC = listOf("abcdefghij", "klmnopqrst", "uvwxyz-._@").map { row -> row.map { it.toString() } }
private val QWERTY = listOf("qwertyuiop", "asdfghjkl'", "zxcvbnm-._@").map { row -> row.map { it.toString() } }
private val SYMBOLS = listOf("!#$%&*()+=", "?/\\:;\"<>[]", "{}^~`|,'_@").map { row -> row.map { it.toString() } }

/**
 * The sign-in keyboard (TV): numbers on the top row, then letters in A to Z order (a key swaps to
 * QWERTY), then Shift, symbols, Space, Delete and Done. Hold Delete to clear.
 */
@Composable
private fun SignInKeyboard(
    value: String,
    onValue: (String) -> Unit,
    onDone: () -> Unit,
    voice: Boolean,
    modifier: Modifier = Modifier,
) {
    // A Talk key, only where the TV can turn speech into words
    val context = LocalContext.current
    val canTalk =
        voice &&
            remember { Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).resolveActivity(context.packageManager) != null }
    val talk =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?.let(onValue)
        }
    var upper by remember { mutableStateOf(false) }
    var symbols by remember { mutableStateOf(false) }
    var qwerty by rememberSaveable { mutableStateOf(false) }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { first.tryRequestFocus() }
    val letters =
        if (symbols) {
            SYMBOLS
        } else if (qwerty) {
            QWERTY
        } else {
            ABC
        }
    val key = 44.dp
    val gap = 6.dp
    Column(verticalArrangement = Arrangement.spacedBy(gap), modifier = modifier) {
        Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
            NUMBERS.forEachIndexed { i, n ->
                Key(n, key, modifier = if (i == 0) Modifier.focusRequester(first) else Modifier) { onValue(value + n) }
            }
            Key("Delete", key * 2 + gap, onLongClick = { onValue("") }) { onValue(value.dropLast(1)) }
        }
        letters.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                row.forEach { l ->
                    val typed = if (upper) l.uppercase() else l
                    Key(typed, key) {
                        onValue(value + typed)
                        // Shift is for one letter, like a phone
                        if (upper) upper = false
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
            Key(if (upper) "abc" else "Shift", key * 2 + gap, selected = upper) { upper = !upper }
            Key(if (symbols) "abc" else "#+=", key * 2 + gap, selected = symbols) { symbols = !symbols }
            if (canTalk) {
                Key("Talk", key) {
                    try {
                        talk.launch(
                            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM),
                        )
                    } catch (e: ActivityNotFoundException) {
                        Timber.w(e, "No speech app")
                    }
                }
            }
            Key("Space", if (canTalk) key * 3 + gap * 2 else key * 4 + gap * 3) { onValue("$value ") }
            Key(if (qwerty) "A-Z" else "QWERTY", key * 2 + gap) { qwerty = !qwerty }
            Key("Done", key * 2 + gap, white = true) { onDone() }
        }
    }
}

@Composable
private fun Key(
    label: String,
    width: Dp,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    white: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(10.dp)
    val lit = selected || white
    Surface(
        onClick = onClick,
        onLongClick = onLongClick,
        shape = ClickableSurfaceDefaults.shape(shape),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = if (lit) BenflixColors.Soft else BenflixColors.Raised,
                contentColor = if (lit) BenflixColors.Tray else BenflixColors.Text,
                focusedContainerColor = BenflixColors.Text,
                focusedContentColor = BenflixColors.Tray,
                pressedContainerColor = BenflixColors.Text,
                pressedContentColor = BenflixColors.Tray,
            ),
        border = doorFocusBorder(shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        modifier = modifier.width(width).height(40.dp),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = label,
                fontSize =
                    if (label.length >
                        1
                    ) {
                        BenflixType.small
                    } else {
                        BenflixType.lead
                    },
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
        }
    }
}

private val ERROR_RED = Color(0xFFFF8A8A)
private const val LAST_LETTER_MS = 1000L
