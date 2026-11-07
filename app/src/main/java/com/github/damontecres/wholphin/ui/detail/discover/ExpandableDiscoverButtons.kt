package com.github.damontecres.wholphin.ui.detail.discover

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.FocusState
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.model.SeerrAvailability
import com.github.damontecres.wholphin.data.model.Trailer
import com.github.damontecres.wholphin.services.KidsMode
import com.github.damontecres.wholphin.ui.components.ExpandableFaButton
import com.github.damontecres.wholphin.ui.components.ExpandablePlayButton
import com.github.damontecres.wholphin.ui.components.TrailerButton
import com.github.damontecres.wholphin.ui.tryRequestFocus
import kotlin.time.Duration

@Composable
fun ExpandableDiscoverButtons(
    canRequest: Boolean,
    canCancel: Boolean,
    availability: SeerrAvailability,
    trailers: List<Trailer>?,
    requestOnClick: () -> Unit,
    cancelOnClick: () -> Unit,
    goToOnClick: () -> Unit,
    moreOnClick: () -> Unit,
    trailerOnClick: (Trailer) -> Unit,
    buttonOnFocusChanged: (FocusState) -> Unit,
    modifier: Modifier = Modifier,
    pendingOnClick: () -> Unit = {},
    isAdmin: Boolean = true,
    // Benflix: Ben couldn't find it, so the Ask button turns into a "Couldn't find it" label
    cantFind: Boolean = false,
) {
    val blocked =
        cantFind && availability != SeerrAvailability.AVAILABLE && availability != SeerrAvailability.PARTIALLY_AVAILABLE
    // Benflix: Kids can't ask for things or cancel, so the Ask button is just a label for them
    val kids by KidsMode.active.collectAsState()
    val noAsking = kids && availability == SeerrAvailability.UNKNOWN && !blocked
    val firstFocus = remember { FocusRequester() }
    // Benflix look A: flat buttons on the request pages too
    androidx.compose.runtime.CompositionLocalProvider(
        com.github.damontecres.wholphin.ui.components.LocalFlatButtons provides true,
    ) {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(8.dp),
            modifier =
                modifier
                    .focusGroup()
                    .focusRestorer(firstFocus),
        ) {
            val baseText =
                when (availability) {
                    // Benflix redesign: one white "Ask for it" for everyone
                    SeerrAvailability.UNKNOWN -> R.string.benflix_ask_for_it

                    // Benflix look A: say where the ask is, in plain words
                    SeerrAvailability.PENDING -> if (isAdmin) R.string.pending else R.string.benflix_asked_waiting

                    SeerrAvailability.PROCESSING -> R.string.benflix_on_its_way

                    SeerrAvailability.PARTIALLY_AVAILABLE,
                    SeerrAvailability.AVAILABLE,
                    -> R.string.benflix_watch_now

                    // TODO
                    SeerrAvailability.DELETED -> R.string.delete

                    // TODO Blocklist
                    SeerrAvailability.BLOCKLISTED -> R.string.unavailable
                }
            val text =
                when {
                    blocked -> R.string.benflix_cant_find
                    noAsking -> R.string.unavailable
                    else -> baseText
                }
            val baseIcon =
                when (availability) {
                    SeerrAvailability.UNKNOWN -> R.string.fa_download

                    SeerrAvailability.PENDING,
                    SeerrAvailability.PROCESSING,
                    -> R.string.fa_clock

                    SeerrAvailability.PARTIALLY_AVAILABLE,
                    SeerrAvailability.AVAILABLE,
                    -> R.string.fa_play

                    SeerrAvailability.DELETED -> R.string.fa_video

                    // TODO
                    SeerrAvailability.BLOCKLISTED -> R.string.fa_xmark
                }
            val icon = if (blocked || noAsking) R.string.fa_xmark else baseIcon
            item("first") {
                ExpandableFaButton(
                    title = text,
                    iconStringRes = icon,
                    white = !blocked && !noAsking && availability == SeerrAvailability.UNKNOWN && canRequest,
                    // Benflix look A: the main button stands out when it does something
                    primary =
                        !blocked && !noAsking &&
                            (
                                (availability == SeerrAvailability.UNKNOWN && canRequest) ||
                                    availability == SeerrAvailability.AVAILABLE ||
                                    availability == SeerrAvailability.PARTIALLY_AVAILABLE
                            ),
                    enabled =
                        when (availability) {
                            SeerrAvailability.UNKNOWN -> canRequest || blocked || noAsking

                            SeerrAvailability.PENDING,
                            SeerrAvailability.PROCESSING,
                            SeerrAvailability.PARTIALLY_AVAILABLE,
                            SeerrAvailability.AVAILABLE,
                            -> true

                            // Benflix: stays focusable on TV so the page still has a first button; pressing does nothing
                            SeerrAvailability.BLOCKLISTED,
                            SeerrAvailability.DELETED,
                            -> blocked
                        },
                    onClick = {
                        if (blocked || noAsking) return@ExpandableFaButton
                        when (availability) {
                            SeerrAvailability.UNKNOWN -> {
                                requestOnClick.invoke()
                            }

                            SeerrAvailability.PENDING,
                            SeerrAvailability.PROCESSING,
                            -> {
                                // Benflix: "Pending" is just a label now; pressing it used to reopen the request form
                            }

                            SeerrAvailability.PARTIALLY_AVAILABLE,
                            SeerrAvailability.AVAILABLE,
                            -> {
                                goToOnClick.invoke()
                            }

                            SeerrAvailability.DELETED,
                            SeerrAvailability.BLOCKLISTED,
                            -> {
                                // TODO
                            }
                        }
                    },
                    modifier =
                        Modifier
                            .focusRequester(firstFocus)
                            .onFocusChanged(buttonOnFocusChanged),
                )
            }
            // Benflix: no asking for the missing seasons once Ben couldn't find them
            if (availability == SeerrAvailability.PARTIALLY_AVAILABLE && !cantFind && !kids) {
                item("request_partial") {
                    ExpandableFaButton(
                        title = R.string.request,
                        iconStringRes = R.string.fa_download,
                        onClick = {
                            requestOnClick.invoke()
                        },
                        enabled = availability == SeerrAvailability.PARTIALLY_AVAILABLE,
                        modifier =
                            Modifier
                                .onFocusChanged(buttonOnFocusChanged),
                    )
                }
            }

            if (canCancel && !kids) {
                item("cancel") {
                    ExpandablePlayButton(
                        title = R.string.benflix_cancel_my_ask,
                        icon = Icons.Default.Delete,
                        onClick = {
                            firstFocus.tryRequestFocus()
                            cancelOnClick.invoke()
                        },
                        resume = Duration.ZERO,
                        enabled = canCancel,
                        modifier =
                            Modifier
                                .onFocusChanged(buttonOnFocusChanged),
                    )
                }
            }

            if (trailers != null) {
                item("trailers") {
                    TrailerButton(
                        trailers = trailers,
                        trailerOnClick = trailerOnClick,
                        modifier = Modifier.onFocusChanged(buttonOnFocusChanged),
                    )
                }
            }

            // More button
            // No functionality yet
//        item("more") {
//            ExpandablePlayButton(
//                R.string.more,
//                Duration.ZERO,
//                Icons.Default.MoreVert,
//                { moreOnClick.invoke() },
//                Modifier
//                    .onFocusChanged(buttonOnFocusChanged),
//            )
//        }
        }
    }
}
