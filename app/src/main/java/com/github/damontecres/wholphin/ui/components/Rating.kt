package com.github.damontecres.wholphin.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.LocalTextStyle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.ui.PreviewTvSpec
import com.github.damontecres.wholphin.ui.isNotNullOrBlank
import com.github.damontecres.wholphin.ui.theme.WholphinTheme
import java.util.Locale

enum class StarRatingPrecision {
    FULL,
    HALF,
    QUARTER,
    ;

    companion object {
        fun fromFloat(value: Float): StarRatingPrecision =
            if (value <= .25f) {
                QUARTER
            } else if (value <= .5f) {
                HALF
            } else {
                FULL
            }
    }
}

val FilledStarColor = Color(0xFFFFC700)
val EmptyStarColor = Color(0xFF5D4A0A)

@Composable
fun SimpleStarRating(
    communityRating: Float?,
    modifier: Modifier = Modifier,
) = CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
    SimpleStarRating(
        text = communityRating?.let { String.format(Locale.getDefault(), "%.1f", it) },
        modifier = modifier,
    )
}

@Composable
fun SimpleStarRating(
    text: String?,
    modifier: Modifier = Modifier,
    starColor: Color = FilledStarColor,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier,
    ) {
        if (text.isNotNullOrBlank()) {
            Text(
                text = text,
                maxLines = 1,
                modifier = Modifier,
            )
            val height = with(LocalDensity.current) { LocalTextStyle.current.fontSize.toDp() }
            Icon(
                imageVector = BenflixIcon.STAR.vector,
                tint = starColor,
                contentDescription = null,
                modifier = Modifier.height(height),
            )
        }
    }
}

@Composable
fun TomatoRating(
    rating: Float?,
    modifier: Modifier = Modifier,
    threshold: Float = 60f,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier,
    ) {
        if (rating != null) {
            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
                Text(
                    text = rating.toInt().toString() + "%",
                    maxLines = 1,
                    modifier = Modifier,
                )
            }
            val height = with(LocalDensity.current) { LocalTextStyle.current.fontSize.toDp() * .8f }
            Icon(
                painter =
                    painterResource(
                        if (rating >= threshold) {
                            R.drawable.ic_rotten_tomatoes_fresh
                        } else {
                            R.drawable.ic_rotten_tomatoes_rotten
                        },
                    ),
                contentDescription = null,
                modifier = Modifier.height(height),
                tint = Color.Unspecified,
            )
        }
    }
}

@PreviewTvSpec
@Composable
private fun SimpleStarRatingPreview() {
    WholphinTheme {
        Column {
            SimpleStarRating(7.5f, Modifier.height(32.dp))
            TomatoRating(75f)
            SimpleStarRating(7.5f, Modifier.height(20.dp))
            SimpleStarRating("", Modifier.height(32.dp))
            TomatoRating(75f)
            TomatoRating(40f)
            TomatoRating(3f)
        }
    }
}
