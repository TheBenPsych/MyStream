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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.github.damontecres.wholphin.ui.touch.Dialog
import com.github.damontecres.wholphin.ui.touch.rememberIsTv
import com.github.damontecres.wholphin.ui.tryRequestFocus
import com.github.damontecres.wholphin.util.LoadingState
import kotlinx.coroutines.delay
import timber.log.Timber

/*
 * Benflix redesign 2 (su-s-quick, approved 2026-11-10): on a TV the left half is "Sign in with your
 * phone" (the Quick Connect code, where to type it, and a QR code), and the right half is "Or type it
 * in" with your name, your password and the approved Benflix keyboard underneath (A to Z, numbers on
 * top, a Talk key where the TV can listen). Phones get just the form, plus a line about Quick Connect.
 * When signing in fails, the keyboard makes room for a plain-words card with one button.
 */

private enum class SignInField { NAME, PASSWORD }

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
    phoneProblem: SignInProblem?,
    /** Signing in failed: what went wrong (null when nothing did) */
    error: LoadingState.Error?,
    attempts: Int,
    actions: SignInActions,
) {
    val isTv = rememberIsTv()
    var name by rememberSaveable { mutableStateOf(knownName.orEmpty()) }
    var password by remember { mutableStateOf("") }
    var field by rememberSaveable { mutableStateOf(if (knownName.isNullOrBlank()) SignInField.NAME else SignInField.PASSWORD) }
    // Phones: the code is a second screen ("Sign in with a code instead")
    var phoneCodeShown by rememberSaveable { mutableStateOf(false) }
    val problem = remember(error) { error?.let { signInProblemFor(it.exception, name) } }
    val signIn = {
        if (name.isNotBlank()) {
            actions.clearError()
            actions.login(name.trim(), password)
        } else {
            field = SignInField.NAME
        }
    }
    // The code waits for a phone the whole time the TV screen is open. Typing a password stops it
    // (Jellyfin only lets one sign-in happen at a time), so it starts again if that didn't work.
    val wantCode = phoneAllowed && (isTv || phoneCodeShown)
    LaunchedEffect(wantCode, attempts) {
        if (wantCode && phoneCode == null) actions.startPhone()
    }
    val goBack = {
        actions.clearError()
        if (!isTv && phoneCodeShown) {
            actions.stopPhone()
            phoneCodeShown = false
        } else {
            actions.stopPhone()
            actions.close()
        }
    }
    val onProblem = {
        actions.clearError()
        when (problem?.kind) {
            SignInProblemKind.WRONG_PASSWORD -> {
                password = ""
                field = SignInField.PASSWORD
            }

            SignInProblemKind.CANT_REACH, SignInProblemKind.OTHER -> {
                signIn()
            }

            else -> {}
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
            if (isTv) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(36.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(start = BenflixSpace.tvEdge, end = BenflixSpace.tvEdge, top = 64.dp, bottom = 20.dp),
                ) {
                    if (phoneAllowed) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(0.42f),
                        ) {
                            PhoneHalf(serverUrl = serverUrl, code = phoneCode, problem = phoneProblem, onTryAgain = actions.startPhone)
                        }
                        Box(
                            Modifier
                                .width(1.dp)
                                .fillMaxHeight(0.7f)
                                .background(BenflixColors.Raised),
                        )
                    }
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = if (phoneAllowed) Modifier.weight(0.58f) else Modifier.widthIn(max = 520.dp),
                    ) {
                        Text(
                            text = if (phoneAllowed) "Or type it in" else "Sign in to $serverName",
                            color = BenflixColors.Text,
                            fontSize = BenflixType.rowName,
                            fontWeight = FontWeight.ExtraBold,
                        )
                        TvField(
                            label = "Name",
                            value = name,
                            password = false,
                            active = field == SignInField.NAME,
                            onClick = { field = SignInField.NAME },
                        )
                        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            TvField(
                                label = "Password (leave it empty if you don't have one)",
                                value = password,
                                password = true,
                                active = field == SignInField.PASSWORD,
                                onClick = { field = SignInField.PASSWORD },
                                modifier = Modifier.weight(1f),
                            )
                            SignInButton("Sign in", white = true, enabled = name.isNotBlank()) { signIn() }
                        }
                        if (problem != null) {
                            ProblemCard(problem, onClick = onProblem, modifier = Modifier.padding(top = 10.dp))
                        } else {
                            SignInKeyboard(
                                value = if (field == SignInField.NAME) name else password,
                                onValue = {
                                    if (field == SignInField.NAME) name = it else password = it
                                },
                                onDone = {
                                    if (field == SignInField.NAME) field = SignInField.PASSWORD else signIn()
                                },
                                // No talking your password out loud
                                voice = field == SignInField.NAME,
                                key = 35.dp,
                                gap = 5.dp,
                                keyHeight = 34.dp,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                        if (attempts > 2) {
                            SignInButton("Trouble signing in? Show details for Ben", modifier = Modifier.padding(top = 4.dp)) {
                                actions.openDebug()
                            }
                        }
                    }
                }
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier =
                        Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                            // Phones: the buttons stay above the keyboard instead of hiding behind it
                            .imePadding()
                            .verticalScroll(rememberScrollState())
                            .padding(top = 64.dp, bottom = 24.dp, start = 16.dp, end = 16.dp)
                            .widthIn(max = 460.dp),
                ) {
                    if (phoneCodeShown) {
                        Question("Sign in with a code", "No password needed")
                        PhoneCode(code = phoneCode, problem = phoneProblem, onTryAgain = actions.startPhone)
                        Line(
                            "On a TV or phone that's already signed in, open My Benflix, then Quick Connect, and type this code.",
                        )
                        SignInButton("Type it in instead") { goBack() }
                    } else {
                        Question("Sign in to Benflix", "Signing in to $serverName")
                        PhoneField(
                            label = "Name",
                            value = name,
                            onValue = {
                                name = it
                                actions.clearError()
                            },
                            password = false,
                            focusFirst = field == SignInField.NAME,
                            onDone = { field = SignInField.PASSWORD },
                            tag = "sign_in_name",
                        )
                        PhoneField(
                            label = "Password",
                            value = password,
                            onValue = {
                                password = it
                                actions.clearError()
                            },
                            password = true,
                            focusFirst = field == SignInField.PASSWORD,
                            onDone = { signIn() },
                            tag = "sign_in_password",
                        )
                        if (problem != null) {
                            ProblemCard(problem, onClick = onProblem)
                        } else {
                            SignInButton("Sign in", white = true, big = true, enabled = name.isNotBlank()) { signIn() }
                        }
                        if (phoneAllowed) {
                            Line("Already signed in on a TV? Sign in here with a code instead:", BenflixColors.Muted)
                            SignInButton("Sign in with a code", icon = BenflixIcon.PHONE) {
                                actions.clearError()
                                phoneCodeShown = true
                            }
                        }
                        if (attempts > 2) {
                            SignInButton("Trouble signing in? Show details for Ben") { actions.openDebug() }
                        }
                    }
                }
            }
        }
    }
}

/** The TV's left half: the Quick Connect code, where to type it, and a QR code */
@Composable
private fun PhoneHalf(
    serverUrl: String,
    code: String?,
    problem: SignInProblem?,
    onTryAgain: () -> Unit,
) {
    Text(
        text = "Sign in with your phone",
        color = BenflixColors.Text,
        fontSize = BenflixType.pageTitle,
        lineHeight = BenflixType.pageTitle * 1.15f,
        fontWeight = FontWeight.ExtraBold,
    )
    Line("On your phone, open Benflix, then My Benflix › Quick Connect, and type this code:")
    PhoneCode(code = code, problem = problem, onTryAgain = onTryAgain)
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .background(Color.White, RoundedCornerShape(8.dp))
                .padding(6.dp),
        ) {
            QrCode(text = serverUrl.trimEnd('/') + "/web/#/quickconnect", modifier = Modifier.size(96.dp))
        }
        Line("No Benflix on your phone? Scan this with its camera, sign in, and type the code there.", BenflixColors.Muted)
    }
}

/** The six-letter code in boxes, "Waiting for your phone…", or why there's no code */
@Composable
private fun PhoneCode(
    code: String?,
    problem: SignInProblem?,
    onTryAgain: () -> Unit,
) {
    if (problem != null && code == null) {
        ProblemCard(problem.copy(title = "No code right now"), onClick = onTryAgain)
        return
    }
    if (code == null) {
        Text("Getting a code…", color = BenflixColors.Soft, fontSize = BenflixType.lead, modifier = Modifier.padding(vertical = 12.dp))
        return
    }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 4.dp)) {
        code.forEach { c ->
            Box(
                contentAlignment = Alignment.Center,
                modifier =
                    Modifier
                        .size(width = 44.dp, height = 54.dp)
                        .background(BenflixColors.Raised, RoundedCornerShape(8.dp)),
            ) {
                Text(c.toString(), color = BenflixColors.Text, fontSize = BenflixType.pageTitle, fontWeight = FontWeight.ExtraBold)
            }
        }
    }
    Line("Waiting for your phone…", BenflixColors.Muted)
}

/** A name or password box on the TV. The one the keyboard types into has a white line */
@Composable
private fun TvField(
    label: String,
    value: String,
    password: Boolean,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
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
    val shape = RoundedCornerShape(8.dp)
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = if (active) BenflixColors.FocusRow else BenflixColors.Raised,
                contentColor = BenflixColors.Text,
                focusedContainerColor = BenflixColors.FocusRow,
                focusedContentColor = BenflixColors.Text,
            ),
        border = doorFocusBorder(shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) {
            Text(label, color = BenflixColors.Muted, fontSize = BenflixType.small, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                text = shown.ifEmpty { " " },
                color = BenflixColors.Text,
                fontSize = BenflixType.lead,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Box(
                Modifier
                    .padding(top = 2.dp)
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(if (active) BenflixColors.Text else Color.Transparent),
            )
        }
    }
}

/** A name or password box on a phone, typed with the phone's own keyboard */
@Composable
private fun PhoneField(
    label: String,
    value: String,
    onValue: (String) -> Unit,
    password: Boolean,
    focusFirst: Boolean,
    onDone: () -> Unit,
    tag: String,
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(focusFirst) { if (focusFirst) focus.tryRequestFocus() }
    Column(Modifier.fillMaxWidth()) {
        Text(label, color = BenflixColors.Muted, fontSize = BenflixType.small)
        BasicTextField(
            value = value,
            onValueChange = onValue,
            singleLine = true,
            textStyle = TextStyle(color = BenflixColors.Text, fontSize = BenflixType.rowName, fontWeight = FontWeight.Bold),
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
                    .testTag(tag),
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(BenflixColors.Soft),
        )
    }
}

/** Why signing in didn't work, in plain words, with one button */
@Composable
internal fun ProblemCard(
    problem: SignInProblem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(problem) { focus.tryRequestFocus() }
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier =
            modifier
                .fillMaxWidth()
                .background(BenflixColors.Raised, RoundedCornerShape(12.dp))
                .padding(16.dp),
    ) {
        Text(problem.title, color = ERROR_RED, fontSize = BenflixType.lead, fontWeight = FontWeight.Bold)
        Text(problem.line, color = BenflixColors.Soft, fontSize = BenflixType.body, lineHeight = BenflixType.body * 1.35f)
        SignInButton(problem.button, white = true, modifier = Modifier.focusRequester(focus)) { onClick() }
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
                overflow = TextOverflow.Ellipsis,
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
            Text(
                text = label,
                fontSize = if (big) BenflixType.lead else BenflixType.body,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
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
    key: Dp = 44.dp,
    gap: Dp = 6.dp,
    keyHeight: Dp = 40.dp,
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
    // Keys pressed quickly (a real keyboard) arrive before the screen redraws, so each one builds on the last
    // thing typed, not on the answer as last drawn (which dropped and mixed up letters)
    val latest = remember { arrayOf(value) }
    if (latest[0] != value) latest[0] = value
    val type = { text: String ->
        latest[0] = text
        onValue(text)
    }
    androidx.compose.runtime.CompositionLocalProvider(LocalKeyHeight provides keyHeight) {
        Column(
            verticalArrangement = Arrangement.spacedBy(gap),
            modifier =
                modifier.onPreviewKeyEvent { event ->
                    // A real keyboard, the phone remote app's keyboard or the remote's number keys type straight in,
                    // instead of only the keys on screen. Arrows, OK and Back still move around as usual.
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    val native = event.nativeKeyEvent
                    when {
                        native.keyCode == android.view.KeyEvent.KEYCODE_DEL -> {
                            type(latest[0].dropLast(1))
                            true
                        }

                        native.unicodeChar > ' '.code && !native.isCtrlPressed && !native.isAltPressed -> {
                            type(latest[0] + native.unicodeChar.toChar())
                            true
                        }

                        native.keyCode == android.view.KeyEvent.KEYCODE_SPACE -> {
                            type(latest[0] + " ")
                            true
                        }

                        else -> {
                            false
                        }
                    }
                },
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                NUMBERS.forEachIndexed { i, n ->
                    Key(n, key, modifier = if (i == 0) Modifier.focusRequester(first) else Modifier) { type(latest[0] + n) }
                }
                Key("Delete", key * 2 + gap, onLongClick = { type("") }) { type(latest[0].dropLast(1)) }
            }
            letters.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    row.forEach { l ->
                        val typed = if (upper) l.uppercase() else l
                        Key(typed, key) {
                            type(latest[0] + typed)
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
                Key("Space", if (canTalk) key * 3 + gap * 2 else key * 4 + gap * 3) { type(latest[0] + " ") }
                Key(if (qwerty) "A-Z" else "QWERTY", key * 2 + gap) { qwerty = !qwerty }
                Key("Done", key * 2 + gap, white = true) { onDone() }
            }
        }
    }
}

/** How tall the keyboard's keys are (smaller on the two-sided sign-in screen) */
private val LocalKeyHeight = androidx.compose.runtime.compositionLocalOf { 40.dp }

@Composable
private fun Key(
    label: String,
    width: Dp,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    white: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    height: Dp = LocalKeyHeight.current,
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
        modifier = modifier.width(width).height(height),
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
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private val ERROR_RED = Color(0xFFFF8A8A)
private const val LAST_LETTER_MS = 1000L
