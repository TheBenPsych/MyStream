package com.github.damontecres.wholphin.ui.main.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.serialization.Serializable
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.CollectionType
import org.jellyfin.sdk.model.serializer.UUIDSerializer
import java.util.UUID

// Benflix: the old Wholphin "Customize home" screens were removed. These few pieces are still used elsewhere.

enum class MoveDirection {
    UP,
    DOWN,
}

@Immutable
@Serializable
data class Library(
    @Serializable(UUIDSerializer::class) val itemId: UUID,
    val name: String,
    val type: BaseItemKind,
    val collectionType: CollectionType,
    val isRecordingFolder: Boolean,
)

@Composable
@NonRestartableComposable
fun TitleText(
    title: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Start,
        modifier =
            modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 4.dp),
    )
}
