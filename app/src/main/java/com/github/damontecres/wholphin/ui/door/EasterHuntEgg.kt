package com.github.damontecres.wholphin.ui.door

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import com.github.damontecres.wholphin.ui.components.BenflixNotices
import com.github.damontecres.wholphin.ui.nav.focusRing
import com.github.damontecres.wholphin.ui.theme.BenflixColors
import com.github.damontecres.wholphin.ui.theme.BenflixDoor
import com.github.damontecres.wholphin.ui.theme.EasterHunt
import com.github.damontecres.wholphin.ui.theme.eaHuntEgg

/**
 * Easter Sunday egg hunt (approved e1-q-day): this door's hidden egg, peeking out of a tuft of grass. It sits at
 * the bottom of the door's side menu on TVs and wide screens, and at the end of the section chips on phones.
 * Press it (OK on the remote, or a tap) to find it; find all six and the golden egg cracks open on Who's watching.
 * Nothing shows on any other day, once it's found, or with the Easter look switched off.
 */
@Composable
fun EasterHuntEgg(
    door: BenflixDoor,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    EasterHunt.init(LocalContext.current)
    if (!EasterHunt.hiddenOn(door)) return
    val shape = RoundedCornerShape(12.dp)
    Surface(
        onClick = { BenflixNotices.pill(EasterHunt.message(door, EasterHunt.find(door))) },
        shape = ClickableSurfaceDefaults.shape(shape),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = Color.Transparent,
                focusedContainerColor = BenflixColors.FocusRow,
            ),
        border = focusRing(shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.15f),
        modifier = modifier.size(size),
    ) {
        Box(Modifier.fillMaxSize().padding(4.dp)) {
            Image(
                painter = painterResource(door.eaHuntEgg()),
                contentDescription = "A hidden Easter egg",
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
