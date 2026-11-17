package com.github.damontecres.wholphin.ui.setup

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.github.damontecres.wholphin.ui.theme.EasterHunt
import com.github.damontecres.wholphin.ui.theme.EasterSeason

/**
 * Benflix Easter: the spring morning room behind Who's watching (Monday to Easter Sunday). The egg tree on the
 * table, the pet rabbit on the floor, and people walking past the window. Drawn by the web version
 * (server/web/js/easter.js) and recorded as 36-second loops by tools/easter/record.js.
 *
 * Easter Sunday: the "day" room (a bitten chocolate bunny, eggs in the grass, the golden egg still shut). Once all
 * six hidden eggs are found, the golden egg cracks open the first time ("crack", played once), then stays open
 * ("found").
 */
@Composable
fun EasterRoom(modifier: Modifier = Modifier) {
    EasterHunt.init(LocalContext.current)
    val sunday = remember { EasterSeason.isSunday() }
    var stage by remember {
        mutableStateOf(
            when {
                !sunday -> "week"
                !EasterHunt.all -> "day"
                EasterHunt.cracked() -> "found"
                else -> "crack"
            },
        )
    }
    key(stage) {
        if (stage == "crack") {
            LoopingRoom(
                EasterVideos,
                { tall -> "crack-${if (tall) "tall" else "wide"}" },
                modifier,
                onEnd = {
                    EasterHunt.setCracked()
                    stage = "found"
                },
            )
        } else {
            LoopingRoom(EasterVideos, { tall -> "$stage-${if (tall) "tall" else "wide"}" }, modifier)
        }
    }
}

/** Downloads and keeps the Easter room videos (extras/scenes/easter in the repo: week, day, crack and found) */
object EasterVideos : RoomVideos("easter", version = 1)
