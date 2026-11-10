package com.github.damontecres.wholphin.ui.detail.title

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.Person
import com.github.damontecres.wholphin.services.AniListFacts
import com.github.damontecres.wholphin.services.MalFacts
import com.github.damontecres.wholphin.services.ParentsGuideLine
import com.github.damontecres.wholphin.ui.cards.PersonCard
import com.github.damontecres.wholphin.ui.detail.ParentsGuideBlock
import com.github.damontecres.wholphin.ui.theme.BenflixColors
import com.github.damontecres.wholphin.ui.theme.BenflixType

/** The Cast tab: one row of faces on TV, 3 across on phones */
@Composable
fun TitleCastRow(
    scope: TitleTabScope,
    people: List<Person>,
    onClick: (Person) -> Unit,
) {
    val list = remember(people) { people.distinctBy { it.id } }
    if (list.isEmpty()) return
    if (scope.wide) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = scope.side),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            itemsIndexed(list, key = { _, it -> it.id.toString() }) { _, p ->
                PersonCard(person = p, onClick = { onClick(p) }, onLongClick = {}, modifier = Modifier.width(100.dp))
            }
        }
    } else {
        TitleGrid(scope, list.size) { index, width ->
            val p = list[index]
            PersonCard(person = p, onClick = { onClick(p) }, onLongClick = {}, modifier = Modifier.width(width))
        }
    }
}

/** The Did you know tab: every fact about this title, one under the other */
@Composable
fun TitleFacts(
    scope: TitleTabScope,
    facts: List<String>,
    accent: Color,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier =
            Modifier
                .padding(horizontal = scope.side)
                .then(if (scope.wide) Modifier.widthIn(max = 900.dp) else Modifier.fillMaxWidth()),
    ) {
        facts.distinct().forEach { fact ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    Modifier
                        .padding(top = 3.dp)
                        .width(3.dp)
                        .height(18.dp)
                        .background(accent, RoundedCornerShape(2.dp)),
                )
                Text(fact, color = BenflixColors.Soft, fontSize = BenflixType.body, lineHeight = BenflixType.body * 1.4f)
            }
        }
    }
}

/** The Parents' guide tab */
@Composable
fun TitleParentsGuide(
    scope: TitleTabScope,
    lines: List<ParentsGuideLine>,
) {
    ParentsGuideBlock(
        lines,
        Modifier
            .padding(horizontal = scope.side)
            .then(if (scope.wide) Modifier.widthIn(max = 700.dp) else Modifier),
    )
}

/** A short label + words pair list, for small "Details" lists inside a tab */
@Composable
fun TitleFactList(
    scope: TitleTabScope,
    facts: List<Pair<String, String>>,
) {
    if (facts.isEmpty()) return
    Column(Modifier.padding(horizontal = scope.side)) {
        facts.forEach { (label, value) ->
            Row(Modifier.padding(vertical = 3.dp)) {
                Text(
                    text = label,
                    color = BenflixColors.Muted,
                    fontSize = BenflixType.small,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.width(120.dp),
                )
                Text(text = value, color = BenflixColors.Soft, fontSize = BenflixType.small)
            }
        }
    }
}

/**
 * The scores for the slim strip: movies and shows get IMDb and Rotten Tomatoes, anime AniList and
 * MyAnimeList (falling back to IMDb). Sites without a score are left out.
 */
@Composable
fun rememberScoreParts(item: BaseItem): List<StripPart> {
    val aniList by AniListFacts.cards.collectAsState()
    val mal by MalFacts.scores.collectAsState()
    val facts = aniList[item.id]
    val malScore = facts?.malId?.let { mal[it] }
    return remember(item, facts, malScore) {
        buildList {
            if (facts != null) {
                facts.scoreText?.let { add(StripPart("AniList", it)) }
                malScore?.let { add(StripPart("MAL", MalFacts.text(it))) }
            }
            if (isEmpty()) {
                item.data.communityRating?.takeIf { it > 0f }?.let {
                    add(StripPart("IMDb", String.format(java.util.Locale.US, "%.1f", it)))
                }
                item.data.criticRating
                    ?.takeIf { it > 0f }
                    ?.let { add(StripPart("Rotten Tomatoes", "${it.toInt()}%")) }
            }
        }
    }
}
