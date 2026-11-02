package com.github.damontecres.wholphin.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.services.BenflixAnimeTracks
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.MediaStreamType

/** Benflix: whether an anime has the original Japanese with subtitles, an English dub, or both */
object SubDub {
    private val ENGLISH = setOf("eng", "en", "english")

    /** "SUB", "DUB" or "SUB | DUB" for an anime movie or episode (with its media streams), else null */
    fun label(item: BaseItemDto?): String? {
        val streams = item?.mediaStreams.orEmpty()
        val audio = streams.filter { it.type == MediaStreamType.AUDIO }
        if (audio.isEmpty()) return null
        val sub =
            audio.any { BenflixAnimeTracks.isJapanese(it.language) } &&
                streams.any { it.type == MediaStreamType.SUBTITLE }
        val dub = audio.any { it.language?.lowercase() in ENGLISH }
        return when {
            sub && dub -> "SUB | DUB"
            dub -> "DUB"
            sub -> "SUB"
            else -> null
        }
    }
}

@Composable
fun SubDubTag(
    label: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = label,
        color = Color.White,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        modifier =
            modifier
                .border(1.dp, Color.White.copy(alpha = .7f), RoundedCornerShape(4.dp))
                .padding(horizontal = 6.dp, vertical = 1.dp),
    )
}

/**
 * Benflix: ENDED or RETURNING on a show's page, from Jellyfin's show status
 * ("Ended" / "Continuing"). Shows nothing when the status isn't known.
 */
@Composable
fun ShowStatusTag(
    status: String?,
    modifier: Modifier = Modifier,
) {
    val (label, color) =
        when {
            status.equals("Ended", true) -> "ENDED" to Color.White.copy(alpha = .7f)
            status.equals("Continuing", true) -> "RETURNING" to Color(0xFF46D369)
            else -> return
        }
    Text(
        text = label,
        color = color,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        modifier =
            modifier
                .border(1.dp, color, RoundedCornerShape(4.dp))
                .padding(horizontal = 6.dp, vertical = 1.dp),
    )
}
