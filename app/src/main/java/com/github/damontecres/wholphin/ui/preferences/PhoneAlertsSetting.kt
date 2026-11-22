package com.github.damontecres.wholphin.ui.preferences

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.services.KidsMode
import com.github.damontecres.wholphin.services.PhoneAlerts
import com.github.damontecres.wholphin.ui.findActivity
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class PhoneAlertsViewModel
    @Inject
    constructor(
        val serverRepository: ServerRepository,
    ) : ViewModel()

/**
 * Glowfin (phones, Settings > Phones and TVs): which phone notifications this person gets. Each press moves
 * to the next choice: My requests and new episodes, Only my requests, Only new episodes, Off.
 * Kids only have New episodes or Off.
 */
@Composable
fun PhoneAlertsSetting(
    title: String,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    viewModel: PhoneAlertsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val userId = viewModel.serverRepository.currentUser?.id
    val kids = KidsMode.isActive
    var choice by remember(userId) {
        mutableStateOf(
            PhoneAlerts.choice(context, userId).let { if (kids && it.requests) PhoneAlerts.Choice.EPISODES else it },
        )
    }
    val allowed = PhoneAlerts.allowed(context)
    ClickPreference(
        title = title,
        value = choice.words,
        summary =
            if (choice != PhoneAlerts.Choice.OFF && !allowed) {
                "Android has notifications turned off for Glowfin. Press to allow them."
            } else {
                "They only tell you. Nothing to press."
            },
        onClick = {
            if (choice != PhoneAlerts.Choice.OFF && !allowed) {
                context.findActivity()?.let { PhoneAlerts.ask(it) }
            } else {
                choice = PhoneAlerts.next(choice, kids)
                PhoneAlerts.setChoice(context, userId, choice)
                if (choice != PhoneAlerts.Choice.OFF) context.findActivity()?.let { PhoneAlerts.ask(it) }
            }
        },
        modifier = modifier,
        interactionSource = interactionSource,
    )
}
