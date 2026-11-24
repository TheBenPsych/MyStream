package com.github.damontecres.wholphin.ui.discover

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The round Search pill at the top asks the tab you're on to open its own search (Movies and Shows search
 * Seerr, Anime searches anime, Music and Books search theirs). Each tab only answers presses made while it's open.
 */
internal object RequestsSearch {
    private val _presses = MutableStateFlow(0)
    val presses: StateFlow<Int> = _presses

    fun ask() {
        _presses.value += 1
    }
}

/** Runs [open] when Search on the top line is pressed while this tab is showing */
@Composable
internal fun OnRequestsSearch(open: () -> Unit) {
    val presses by RequestsSearch.presses.collectAsState()
    val seen = remember { RequestsSearch.presses.value }
    LaunchedEffect(presses) {
        if (presses != seen) open()
    }
}

/**
 * Glowfin R1 "Waiting for you": the sea friend of the person who asked, so Ben can tell who it is at a
 * glance. Uses the picture they picked when it's known on this device, otherwise their usual one.
 */
@Composable
internal fun WhoAskedFace(
    name: String,
    size: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    com.github.damontecres.wholphin.ui.setup.AvatarChoices
        .init(context)
    val people by com.github.damontecres.wholphin.services.RequestFor.people
        .collectAsState()
    val id =
        remember(name, people) {
            people
                .firstOrNull { it.name.equals(name, ignoreCase = true) }
                ?.id
                ?.let(::jellyfinUuid)
                ?: java.util.UUID.nameUUIDFromBytes(name.lowercase().toByteArray())
        }
    val res =
        com.github.damontecres.wholphin.ui.setup.AvatarChoices
            .get(id)
            ?.let {
                com.github.damontecres.wholphin.ui.setup.BenflixAvatars
                    .getOrNull(it)
            } ?: com.github.damontecres.wholphin.ui.setup
            .benflixAvatarFor(id, name)
    com.github.damontecres.wholphin.ui.setup.BenflixAvatarImage(
        res = res,
        background =
            com.github.damontecres.wholphin.ui.setup
                .avatarBackgroundFor(id, res),
        contentDescription = name,
        modifier =
            modifier
                .size(size)
                .clip(androidx.compose.foundation.shape.CircleShape),
    )
}

/** Jellyfin writes ids without dashes; turn one back into a UUID */
private fun jellyfinUuid(raw: String): java.util.UUID? {
    val hex = raw.replace("-", "")
    if (hex.length != 32) return null
    return runCatching {
        java.util.UUID.fromString(
            "${hex.substring(0, 8)}-${hex.substring(8, 12)}-${hex.substring(12, 16)}-${hex.substring(16, 20)}-${hex.substring(20)}",
        )
    }.getOrNull()
}
