package com.github.damontecres.wholphin.ui.theme

import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.Interaction
import androidx.compose.foundation.interaction.MutableInteractionSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** For @Preview only: an interaction source that always looks focused */
class PreviewInteractionSource : MutableInteractionSource {
    override val interactions: Flow<Interaction>
        get() = flowOf(FocusInteraction.Focus())

    override suspend fun emit(interaction: Interaction) {
    }

    override fun tryEmit(interaction: Interaction): Boolean = false
}
