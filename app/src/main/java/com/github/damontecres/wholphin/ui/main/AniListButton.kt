package com.github.damontecres.wholphin.ui.main

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.services.AniListSync
import com.github.damontecres.wholphin.services.MalSync
import com.github.damontecres.wholphin.services.ResumeTimer
import com.github.damontecres.wholphin.ui.launchIO
import com.github.damontecres.wholphin.ui.touch.Dialog
import dagger.hilt.android.lifecycle.HiltViewModel
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class AniListButtonViewModel
    @Inject
    constructor(
        val sync: AniListSync,
    ) : ViewModel() {
        fun refresh() {
            viewModelScope.launchIO { sync.refresh() }
        }

        fun signOut() {
            viewModelScope.launchIO { sync.signOut() }
        }

        /** Opens AniList's sign-in page; TVs without a web browser are told to use their phone */
        fun signIn(context: Context) {
            val intent = sync.signInIntent()
            try {
                if (intent.resolveActivity(context.packageManager) != null) {
                    // Coming back from the browser isn't "away" (no Who's watching)
                    ResumeTimer.expectedTrip = true
                    context.startActivity(intent)
                    return
                }
            } catch (ex: Exception) {
                ResumeTimer.expectedTrip = false
                Timber.w(ex, "Could not open the AniList sign-in page")
            }
            Toast
                .makeText(
                    context,
                    "Sign in on your phone instead: Benflix > My Benflix > AniList. This TV picks it up by itself.",
                    Toast.LENGTH_LONG,
                ).show()
        }
    }

/**
 * Benflix: the AniList button on My Benflix. Signed in, finished anime episodes are ticked off
 * on the person's AniList list by themselves.
 */
@Composable
fun AniListButton(viewModel: AniListButtonViewModel = hiltViewModel()) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { viewModel.refresh() }
    val name by viewModel.sync.name.collectAsState()
    var showSignOut by remember { mutableStateOf(false) }
    Button(
        onClick = {
            if (name == null) viewModel.signIn(context) else showSignOut = true
        },
        scale = ButtonDefaults.scale(focusedScale = 1f),
    ) {
        Text(if (name == null) "AniList" else "AniList: $name")
    }
    if (showSignOut) {
        AniListSignOutDialog(name.orEmpty(), onDismiss = { showSignOut = false }, onSignOut = viewModel::signOut)
    }
}

@HiltViewModel
class MalButtonViewModel
    @Inject
    constructor(
        val sync: MalSync,
    ) : ViewModel() {
        fun refresh() {
            viewModelScope.launchIO { sync.refresh() }
        }

        fun signOut() {
            viewModelScope.launchIO { sync.signOut() }
        }

        /** Opens MyAnimeList's sign-in page; TVs without a web browser are told to use their phone */
        fun signIn(context: Context) {
            val intent = sync.signInIntent()
            try {
                if (intent.resolveActivity(context.packageManager) != null) {
                    ResumeTimer.expectedTrip = true
                    context.startActivity(intent)
                    return
                }
            } catch (ex: Exception) {
                ResumeTimer.expectedTrip = false
                Timber.w(ex, "Could not open the MyAnimeList sign-in page")
            }
            Toast
                .makeText(
                    context,
                    "Sign in on your phone instead: Benflix > My Benflix > MyAnimeList. This TV picks it up by itself.",
                    Toast.LENGTH_LONG,
                ).show()
        }
    }

/**
 * Benflix: the MyAnimeList button on My Benflix. Signed in, finished anime episodes are ticked off
 * on the person's MyAnimeList list by themselves (with or without AniList).
 */
@Composable
fun MalButton(viewModel: MalButtonViewModel = hiltViewModel()) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { viewModel.refresh() }
    val name by viewModel.sync.name.collectAsState()
    var showSignOut by remember { mutableStateOf(false) }
    Button(
        onClick = {
            if (name == null) viewModel.signIn(context) else showSignOut = true
        },
        scale = ButtonDefaults.scale(focusedScale = 1f),
    ) {
        Text(if (name == null) "MyAnimeList" else "MyAnimeList: $name")
    }
    if (showSignOut) {
        AniListSignOutDialog(name.orEmpty(), onDismiss = { showSignOut = false }, onSignOut = viewModel::signOut, site = "MyAnimeList")
    }
}

/** Benflix: the MyAnimeList row in Settings > Profile, with what it does */
@Composable
fun MalSetting(
    title: String,
    modifier: Modifier = Modifier,
    interactionSource: androidx.compose.foundation.interaction.MutableInteractionSource =
        remember {
            androidx.compose.foundation.interaction
                .MutableInteractionSource()
        },
    viewModel: MalButtonViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { viewModel.refresh() }
    val name by viewModel.sync.name.collectAsState()
    var showSignOut by remember { mutableStateOf(false) }
    com.github.damontecres.wholphin.ui.preferences.ClickPreference(
        title = title,
        onClick = { if (name == null) viewModel.signIn(context) else showSignOut = true },
        modifier = modifier,
        summary =
            if (name == null) {
                stringResource(R.string.benflix_mal_tracking_off)
            } else {
                stringResource(R.string.benflix_mal_tracking_on, name.orEmpty())
            },
        onLongClick = null,
        interactionSource = interactionSource,
    )
    if (showSignOut) {
        AniListSignOutDialog(name.orEmpty(), onDismiss = { showSignOut = false }, onSignOut = viewModel::signOut, site = "MyAnimeList")
    }
}

/** Benflix: the AniList row in Settings > Profile, with what it does */
@Composable
fun AniListSetting(
    title: String,
    modifier: Modifier = Modifier,
    interactionSource: androidx.compose.foundation.interaction.MutableInteractionSource =
        remember {
            androidx.compose.foundation.interaction
                .MutableInteractionSource()
        },
    viewModel: AniListButtonViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { viewModel.refresh() }
    val name by viewModel.sync.name.collectAsState()
    var showSignOut by remember { mutableStateOf(false) }
    com.github.damontecres.wholphin.ui.preferences.ClickPreference(
        title = title,
        onClick = { if (name == null) viewModel.signIn(context) else showSignOut = true },
        modifier = modifier,
        summary =
            if (name == null) {
                stringResource(R.string.benflix_anilist_tracking_off)
            } else {
                stringResource(R.string.benflix_anilist_tracking_on, name.orEmpty())
            },
        onLongClick = null,
        interactionSource = interactionSource,
    )
    if (showSignOut) {
        AniListSignOutDialog(name.orEmpty(), onDismiss = { showSignOut = false }, onSignOut = viewModel::signOut)
    }
}

@Composable
private fun AniListSignOutDialog(
    name: String,
    onDismiss: () -> Unit,
    onSignOut: () -> Unit,
    site: String = "AniList",
) {
    run {
        Dialog(onDismissRequest = onDismiss) {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier =
                    Modifier
                        .widthIn(max = 420.dp)
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
                        .padding(24.dp),
            ) {
                Text(
                    text = "Signed in to $site as $name",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text =
                        "Anime episodes you finish on Benflix are ticked off on your $site list. " +
                            "Signing out stops this on all your TVs and phones.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = .8f),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = onDismiss, scale = ButtonDefaults.scale(focusedScale = 1f)) { Text("Keep") }
                    Button(
                        onClick = {
                            onSignOut()
                            onDismiss()
                        },
                        scale = ButtonDefaults.scale(focusedScale = 1f),
                    ) { Text("Sign out", color = Color.Unspecified) }
                }
            }
        }
    }
}
