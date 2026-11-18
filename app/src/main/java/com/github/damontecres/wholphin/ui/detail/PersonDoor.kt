package com.github.damontecres.wholphin.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.DiscoverItem
import com.github.damontecres.wholphin.ui.theme.BenflixColors
import com.github.damontecres.wholphin.ui.theme.BenflixType

/** One door's share of an actor's work: Movies (amber), Shows (blue) or Not on Benflix (grey) */
sealed interface PersonDoor {
    val key: String
    val title: String
    val colour: Color
    val count: Int

    data class Library(
        override val key: String,
        override val title: String,
        override val colour: Color,
        val items: List<BaseItem>,
    ) : PersonDoor {
        override val count: Int get() = items.size
    }

    data class Ask(
        val items: List<DiscoverItem>,
    ) : PersonDoor {
        override val key = "ask"
        override val title = "Not on Glowfin"
        override val colour = BenflixColors.NotHere
        override val count: Int get() = items.size
    }
}

@Composable
private fun DoorHeading(
    door: PersonDoor,
    note: String?,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier,
    ) {
        Spacer(
            Modifier
                .size(12.dp)
                .background(door.colour, RoundedCornerShape(3.dp)),
        )
        Text(
            text = door.title,
            color = BenflixColors.Text,
            fontSize = BenflixType.rowName,
            fontWeight = FontWeight.ExtraBold,
        )
        if (note != null) {
            Text(text = note, color = BenflixColors.Muted, fontSize = BenflixType.small, fontWeight = FontWeight.SemiBold)
        }
    }
}

private const val COLUMNS = 4
