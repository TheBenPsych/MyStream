package com.github.damontecres.wholphin.ui.games

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.ui.games.party.PartyJoinScreen
import com.github.damontecres.wholphin.ui.games.puzzles.BlitzScreen
import com.github.damontecres.wholphin.ui.games.puzzles.CloseUpScreen
import com.github.damontecres.wholphin.ui.games.puzzles.LocalPuzzleAccent
import com.github.damontecres.wholphin.ui.games.puzzles.MovieGridScreen
import com.github.damontecres.wholphin.ui.games.puzzles.PosterPuzzleScreen
import com.github.damontecres.wholphin.ui.games.puzzles.QuoteItScreen
import com.github.damontecres.wholphin.ui.games.puzzles.SceneBySceneScreen
import com.github.damontecres.wholphin.ui.games.puzzles.TimelineScreen
import com.github.damontecres.wholphin.ui.games.puzzles.WhichSeasonScreen
import com.github.damontecres.wholphin.ui.games.puzzles.WhoPlayedScreen
import com.github.damontecres.wholphin.ui.games.puzzles.WhosThatActorScreen
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/** Opens one of the puzzle games by its MovieGame name */
@Composable
fun MovieGamePlayPage(
    name: String,
    modifier: Modifier = Modifier,
) {
    val game = MovieGame.entries.firstOrNull { it.name == name }
    // Each game glows in its own colours (the same as its tile)
    CompositionLocalProvider(LocalPuzzleAccent provides (game?.colors ?: LocalPuzzleAccent.current)) {
        MovieGamePlayContent(game, modifier)
    }
}

@Composable
private fun MovieGamePlayContent(
    game: MovieGame?,
    modifier: Modifier,
) {
    when (game) {
        MovieGame.SCENES -> {
            SceneBySceneScreen(modifier)
        }

        MovieGame.POSTER -> {
            PosterPuzzleScreen(modifier)
        }

        MovieGame.GRID -> {
            MovieGridScreen(modifier)
        }

        MovieGame.TIMELINE -> {
            TimelineScreen(modifier)
        }

        MovieGame.ACTOR -> {
            WhosThatActorScreen(modifier)
        }

        MovieGame.QUOTE -> {
            QuoteItScreen(modifier)
        }

        MovieGame.CLOSEUP -> {
            CloseUpScreen(modifier)
        }

        MovieGame.ROLE -> {
            WhoPlayedScreen(modifier)
        }

        MovieGame.BLITZ -> {
            BlitzScreen(modifier)
        }

        MovieGame.SEASON -> {
            WhichSeasonScreen(modifier)
        }

        else -> {}
    }
}

@HiltViewModel
class PartyJoinViewModel
    @Inject
    constructor(
        private val serverRepository: ServerRepository,
        val navigationManager: NavigationManager,
    ) : ViewModel() {
        val phoneName: String get() =
            serverRepository.currentUser?.name?.take(20) ?: android.os.Build.MODEL
                .take(20)
    }

/** A phone joining a party game on a TV */
@Composable
fun PartyJoinPage(
    modifier: Modifier = Modifier,
    viewModel: PartyJoinViewModel = hiltViewModel(),
) {
    PartyJoinScreen(
        phoneName = viewModel.phoneName,
        onLeave = { viewModel.navigationManager.goBack() },
        modifier = modifier,
    )
}
