package com.github.damontecres.wholphin.ui.main

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.ui.cards.ItemRowTitle
import java.util.UUID

/**
 * A studio or franchise shown as a big tile under the banner, like Marvel or Pixar
 */
data class StudioBrand(
    val name: String,
    /** Parts of the studio names on the server that belong to this brand */
    val matches: List<String>,
    val colors: List<Color>,
)

data class StudioTile(
    val brand: StudioBrand,
    val studioIds: List<UUID>,
    val count: Int,
)

val StudioBrands =
    listOf(
        StudioBrand("Marvel", listOf("marvel"), listOf(Color(0xFFEC1D24), Color(0xFF7A0B10))),
        StudioBrand("Pixar", listOf("pixar"), listOf(Color(0xFF2A7FD4), Color(0xFF0D2E57))),
        StudioBrand(
            "Disney",
            listOf("walt disney", "disney animation", "disney pictures", "disney television"),
            listOf(Color(0xFF1A3D8F), Color(0xFF0A1636)),
        ),
        StudioBrand("Star Wars", listOf("lucasfilm"), listOf(Color(0xFF2B2B2B), Color(0xFF000000))),
        StudioBrand("DreamWorks", listOf("dreamworks"), listOf(Color(0xFF3FA9F5), Color(0xFF0F4A7A))),
        StudioBrand("Illumination", listOf("illumination"), listOf(Color(0xFFFFD21F), Color(0xFF9A6A00))),
        StudioBrand("Studio Ghibli", listOf("ghibli"), listOf(Color(0xFF4FA36B), Color(0xFF1C4A2C))),
        StudioBrand("Warner Bros.", listOf("warner bros"), listOf(Color(0xFF1E5AA8), Color(0xFF0B2246))),
        StudioBrand("Universal", listOf("universal"), listOf(Color(0xFF3A6EA5), Color(0xFF111E36))),
        StudioBrand("Paramount", listOf("paramount"), listOf(Color(0xFF2D7BE5), Color(0xFF0A2A5C))),
        StudioBrand("Sony", listOf("columbia pictures", "sony pictures"), listOf(Color(0xFF5B5B6B), Color(0xFF1E1E26))),
        StudioBrand("HBO", listOf("hbo"), listOf(Color(0xFF6C2BD9), Color(0xFF220A4F))),
        StudioBrand("Netflix", listOf("netflix"), listOf(Color(0xFFE50914), Color(0xFF3A0205))),
        StudioBrand("A24", listOf("a24"), listOf(Color(0xFF444444), Color(0xFF111111))),
        StudioBrand("Lionsgate", listOf("lionsgate"), listOf(Color(0xFFB8860B), Color(0xFF3D2C04))),
        StudioBrand("20th Century", listOf("20th century"), listOf(Color(0xFFC8A24A), Color(0xFF3F2F0C))),
        StudioBrand("MGM", listOf("mgm"), listOf(Color(0xFFC9A227), Color(0xFF3A2E07))),
        StudioBrand("New Line", listOf("new line"), listOf(Color(0xFF4A6FA5), Color(0xFF141F33))),
        StudioBrand("Legendary", listOf("legendary"), listOf(Color(0xFF8C8C8C), Color(0xFF232323))),
        StudioBrand("Blumhouse", listOf("blumhouse"), listOf(Color(0xFF8E1B1B), Color(0xFF1F0505))),
        StudioBrand("Searchlight", listOf("searchlight"), listOf(Color(0xFFB59A5A), Color(0xFF2E2614))),
        StudioBrand("Focus Features", listOf("focus features"), listOf(Color(0xFF2E2E2E), Color(0xFF0A0A0A))),
        StudioBrand("Miramax", listOf("miramax"), listOf(Color(0xFF1D3557), Color(0xFF09111C))),
        StudioBrand("Touchstone", listOf("touchstone"), listOf(Color(0xFF2B6CB0), Color(0xFF0B1E33))),
        StudioBrand("Amazon", listOf("amazon"), listOf(Color(0xFF00A8E1), Color(0xFF00374A))),
        StudioBrand("Apple TV+", listOf("apple"), listOf(Color(0xFF6E6E73), Color(0xFF1C1C1E))),
        StudioBrand("Hulu", listOf("hulu"), listOf(Color(0xFF1CE783), Color(0xFF07462A))),
        StudioBrand("ABC", listOf("abc"), listOf(Color(0xFF2B2B2B), Color(0xFF000000))),
        StudioBrand("NBC", listOf("nbc"), listOf(Color(0xFFF37021), Color(0xFF5C2508))),
        StudioBrand("CBS", listOf("cbs"), listOf(Color(0xFF0A5BD3), Color(0xFF041E47))),
        StudioBrand("FOX", listOf("fox"), listOf(Color(0xFF1F3A68), Color(0xFF0A1325))),
        StudioBrand("AMC", listOf("amc"), listOf(Color(0xFF3A3A3A), Color(0xFF0E0E0E))),
        StudioBrand("FX", listOf("fx productions"), listOf(Color(0xFF111111), Color(0xFF000000))),
        StudioBrand("Showtime", listOf("showtime"), listOf(Color(0xFFD10A11), Color(0xFF420305))),
        StudioBrand("Starz", listOf("starz"), listOf(Color(0xFF1A1A1A), Color(0xFF000000))),
        StudioBrand("Nickelodeon", listOf("nickelodeon"), listOf(Color(0xFFFF6A00), Color(0xFF5A2500))),
        StudioBrand("Cartoon Network", listOf("cartoon network"), listOf(Color(0xFF222222), Color(0xFF000000))),
        StudioBrand("Adult Swim", listOf("williams street"), listOf(Color(0xFF1A1A1A), Color(0xFF000000))),
        StudioBrand("Comedy Central", listOf("comedy central"), listOf(Color(0xFFFFCC00), Color(0xFF5C4A00))),
        StudioBrand("BBC", listOf("bbc"), listOf(Color(0xFF333333), Color(0xFF0B0B0B))),
        StudioBrand("PBS", listOf("pbs"), listOf(Color(0xFF2638C4), Color(0xFF0B1240))),
        StudioBrand("National Geographic", listOf("national geographic"), listOf(Color(0xFFFFCE00), Color(0xFF4D3E00))),
        StudioBrand("Discovery", listOf("discovery"), listOf(Color(0xFF0072CE), Color(0xFF00213D))),
        StudioBrand("MTV", listOf("mtv"), listOf(Color(0xFF6B00B6), Color(0xFF1F0035))),
        StudioBrand("Sony Animation", listOf("sony pictures animation"), listOf(Color(0xFF5B5B6B), Color(0xFF1E1E26))),
        StudioBrand("Lucasfilm Animation", listOf("lucasfilm animation"), listOf(Color(0xFF2B2B2B), Color(0xFF000000))),
        StudioBrand("Laika", listOf("laika"), listOf(Color(0xFFE07A1F), Color(0xFF4A2508))),
        StudioBrand("Aardman", listOf("aardman"), listOf(Color(0xFF6DBE45), Color(0xFF22401A))),
        StudioBrand("Toho", listOf("toho"), listOf(Color(0xFFB71C1C), Color(0xFF3D0909))),
    )

/**
 * Row of big studio tiles, under the library rows
 */
@Composable
fun StudioRow(
    tiles: List<StudioTile>,
    onClick: (StudioTile) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        ItemRowTitle(stringResource(R.string.studios_row))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
        ) {
            items(tiles, key = { it.brand.name }) { tile ->
                StudioTileCard(tile = tile, onClick = { onClick(tile) })
            }
        }
    }
}

@Composable
private fun StudioTileCard(
    tile: StudioTile,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(10.dp)
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.08f),
        border =
            ClickableSurfaceDefaults.border(
                border = Border(BorderStroke(1.dp, Color.White.copy(alpha = .25f)), shape = shape),
                focusedBorder = Border(BorderStroke(3.dp, Color.White), shape = shape),
            ),
        modifier = modifier.size(180.dp, 96.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(tile.brand.colors)),
        ) {
            Text(
                text = tile.brand.name,
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        }
    }
}
