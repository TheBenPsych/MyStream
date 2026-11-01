package com.github.damontecres.wholphin.ui.components

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.ui.theme.BenflixRed
import kotlinx.coroutines.delay

/** The first-time tips Benflix shows, each only once on a device */
enum class BenflixTipId(
    val text: String,
) {
    HOME_TV("Deep in the rows? Press Back to jump to the top, and again for the menu."),
    HOME_PHONE("Scroll down for more rows. The bar at the bottom takes you anywhere."),
    SEARCH("Search can find any movie, show or actor. Not on Benflix yet? You can ask for it."),
    SKIP_INTRO("See Skip intro in the corner? Press OK on it to jump past the opening song."),
}

object BenflixTips {
    private const val PREFS = "benflix_tips"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun seen(
        context: Context,
        tip: BenflixTipId,
    ) = prefs(context).getBoolean(tip.name, false)

    fun markSeen(
        context: Context,
        tip: BenflixTipId,
    ) = prefs(context).edit { putBoolean(tip.name, true) }

    /** "Show tips again" on the How-to page */
    fun reset(context: Context) = prefs(context).edit { clear() }
}

/**
 * A small tip bubble in the corner that shows once, then goes away by itself after a few seconds.
 * Put it inside a Box that fills the screen. It never takes focus, so it never gets in the way.
 */
@Composable
fun BenflixTip(
    tip: BenflixTipId,
    modifier: Modifier = Modifier,
    show: Boolean = true,
    alignment: Alignment = Alignment.BottomEnd,
) {
    val context = LocalContext.current
    var visible by remember(tip) { mutableStateOf(false) }
    LaunchedEffect(tip, show) {
        if (show && !BenflixTips.seen(context, tip)) {
            BenflixTips.markSeen(context, tip)
            delay(SHOW_AFTER_MS)
            visible = true
            delay(SHOW_FOR_MS)
            visible = false
        }
    }
    Box(modifier = modifier.fillMaxSize(), contentAlignment = alignment) {
        AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut()) {
            Text(
                text =
                    buildAnnotatedString {
                        withStyle(SpanStyle(color = BenflixRed, fontWeight = FontWeight.Bold)) {
                            append("Tip  ")
                        }
                        append(tip.text)
                    },
                fontSize = 16.sp,
                color = Color.White,
                modifier =
                    Modifier
                        .padding(24.dp)
                        .widthIn(max = 420.dp)
                        .background(Color(0xE6202024), RoundedCornerShape(12.dp))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
    }
}

private const val SHOW_AFTER_MS = 1_500L
private const val SHOW_FOR_MS = 7_000L
