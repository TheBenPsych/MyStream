package com.github.damontecres.wholphin.ui.search

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.DiscoverItem
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.services.BookTile
import com.github.damontecres.wholphin.ui.cards.DiscoverItemCard
import com.github.damontecres.wholphin.ui.cards.GridCard
import com.github.damontecres.wholphin.ui.cards.ItemRowTitle
import com.github.damontecres.wholphin.ui.components.BenflixTipId
import com.github.damontecres.wholphin.ui.components.BenflixTipLine
import com.github.damontecres.wholphin.ui.components.SearchEditTextBox
import com.github.damontecres.wholphin.ui.components.rememberContextMenu
import com.github.damontecres.wholphin.ui.components.rememberVoiceSearch
import com.github.damontecres.wholphin.ui.data.RowColumn
import com.github.damontecres.wholphin.ui.detail.CardGrid
import com.github.damontecres.wholphin.ui.detail.CardGridItem
import com.github.damontecres.wholphin.ui.detail.GridItemDetails
import com.github.damontecres.wholphin.ui.detail.livetv.ProgramDialog
import com.github.damontecres.wholphin.ui.detail.title.LocalTitleExtras
import com.github.damontecres.wholphin.ui.detail.title.TitleBox
import com.github.damontecres.wholphin.ui.detail.title.TitleExtras
import com.github.damontecres.wholphin.ui.door.DoorArt
import com.github.damontecres.wholphin.ui.door.DoorArtUrl
import com.github.damontecres.wholphin.ui.door.DoorInfo
import com.github.damontecres.wholphin.ui.door.DoorScorePill
import com.github.damontecres.wholphin.ui.door.DoorTab
import com.github.damontecres.wholphin.ui.door.DoorTag
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.nav.LocalTopBarOverlap
import com.github.damontecres.wholphin.ui.nav.RemoteShortcuts
import com.github.damontecres.wholphin.ui.onMain
import com.github.damontecres.wholphin.ui.theme.BenflixColors
import com.github.damontecres.wholphin.ui.theme.BenflixSpace
import com.github.damontecres.wholphin.ui.theme.BenflixType
import com.github.damontecres.wholphin.ui.touch.rememberIsTv
import com.github.damontecres.wholphin.ui.tryRequestFocus
import com.github.damontecres.wholphin.util.WholphinDispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.model.api.BaseItemKind
import timber.log.Timber
import kotlin.time.Duration.Companion.milliseconds

internal const val SEARCH_ROW = 0
internal const val TAB_ROW = SEARCH_ROW + 1
internal const val SEERR_ROW = TAB_ROW + 1
internal const val COMBINED_ROW = SEERR_ROW
internal const val RESULTS_START = SEERR_ROW + 1

/** How long after the last letter the search starts */
private val TYPING_WAIT_TV = 350.milliseconds
private val TYPING_WAIT_PHONE = 250.milliseconds

@Composable
fun SearchPage(
    initialQuery: String,
    userPreferences: UserPreferences,
    modifier: Modifier = Modifier,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val state by viewModel.state.collectAsState()
    val foundGames by viewModel.games.collectAsState()
    val musicAsk by viewModel.musicAsk.collectAsState()
    val musicNote by viewModel.musicNote.collectAsState()
    LaunchedEffect(musicNote) {
        musicNote?.let {
            com.github.damontecres.wholphin.ui.components.BenflixNotices
                .pill(it)
            viewModel.musicNote.value = null
        }
    }
    val programDialogState by viewModel.programDialogState.collectAsState()

    // Start with current preferences, but collect updates when view options change
    val prefs =
        viewModel.userPreferencesService.flow
            .collectAsState(userPreferences)
            .value.appPreferences.interfacePreferences.searchPreferences
    // Benflix redesign: results are always the rows (one combined search)
    val combinedMode = true
    val voiceSearchButtonVisible = prefs.showVoiceSearchButton

    var query by rememberSaveable { mutableStateOf(initialQuery) }
    val focusRequesters =
        remember(state.includedSearchableTypes.size) { List(RESULTS_START + state.includedSearchableTypes.size) { FocusRequester() } }

    val seerrActive by viewModel.seerrActive.collectAsState()
    var showViewOptions by rememberSaveable { mutableStateOf(false) }
    var showFilterTypeDialog by rememberSaveable { mutableStateOf(false) }
    var searchClicked by rememberSaveable(query) { mutableStateOf(false) }
    var immediateSearchQuery by rememberSaveable { mutableStateOf<String?>(null) }
    var showProgramDialog by remember { mutableStateOf(false) }

    // Landing on a finished name shows its results (the box keeps what was typed)
    var trying by remember { mutableStateOf<String?>(null) }

    val position by viewModel.position.collectAsState()

    fun setPosition(pos: RowColumn) {
        Timber.v("pos=%s", pos)
        viewModel.position.value = pos
    }

    val contextMenu = rememberContextMenu(userPreferences, viewModel)

    LifecycleResumeEffect(Unit) {
        onPauseOrDispose {
            viewModel.voiceInputManager.stopListening()
        }
    }

    val isTv = rememberIsTv()

    fun triggerImmediateSearch(searchQuery: String) {
        immediateSearchQuery = searchQuery
        searchClicked = true
        viewModel.search(searchQuery, combinedMode)
    }

    LaunchedEffect(query, combinedMode) {
        when {
            immediateSearchQuery == query -> {
                immediateSearchQuery = null
            }

            else -> {
                // Search speed (Ben 2026-11-21): this wait was 750. Older searches are stopped when a new letter
                // comes, so a short wait is safe; TVs wait a touch longer because remote typing is slower
                delay(if (isTv) TYPING_WAIT_TV else TYPING_WAIT_PHONE)
                viewModel.search(query, combinedMode, keepShowing = true)
            }
        }
    }
    val searchContext = LocalContext.current
    val onClickItem = { _: Int, item: BaseItem ->
        Timber.v("Clicked %s, type=%s", item.id, item.type)
        RecentSearches.add(searchContext, viewModel.recentAccount, query)
        if (item.type == BaseItemKind.TV_PROGRAM || item.type == BaseItemKind.PROGRAM || item.type == BaseItemKind.LIVE_TV_PROGRAM) {
            viewModel.fetchProgramForDialog(item.id)
            showProgramDialog = true
        } else {
            viewModel.navigationManager.navigateTo(item.destination())
        }
    }
    val onLongClickItem = { rowIndex: Int, index: Int, item: BaseItem ->
        setPosition(RowColumn(rowIndex, index))
        contextMenu.showContextMenu(index, item)
    }
    val onPlayItem = { _: Int, item: BaseItem ->
        viewModel.navigationManager.navigateTo(Destination.Playback(item))
    }

    val onClickDiscover = { _: Int, item: DiscoverItem ->
        RecentSearches.add(searchContext, viewModel.recentAccount, query)
        val dest =
            if (item.jellyfinItemId != null && item.type.baseItemKind != null) {
                Destination.MediaItem(
                    itemId = item.jellyfinItemId,
                    type = item.type.baseItemKind,
                )
            } else {
                Destination.DiscoveredItem(item)
            }
        viewModel.navigationManager.navigateTo(dest)
    }

    val positionCallback = { _: Int, _: Int -> }

    LaunchedEffect(
        searchClicked,
        state,
        combinedMode,
        seerrActive,
    ) {
        if (!searchClicked || position.row > TAB_ROW) return@LaunchedEffect
        // Trying a finished name on TV: focus stays on the name
        if (trying != null) return@LaunchedEffect

        withContext(WholphinDispatchers.IO) {
            // Pressing a recent search or a finished name: once the results are in, go to the first one
            val result = state.combinedResults
            if (result is SearchResult.Success || result is SearchResult.SuccessSeerr) {
                onMain { focusRequesters.getOrNull(COMBINED_ROW)?.tryRequestFocus() }
            }
        }
    }

    // ---- Glowfin new look (Ben picked 2026-11-22) ----
    val suggestions by viewModel.suggestions.collectAsState()
    val foundBooks by viewModel.books.collectAsState()
    LaunchedEffect(trying) {
        val t = trying ?: return@LaunchedEffect
        delay(300.milliseconds)
        viewModel.search(t, combinedMode, preview = true, keepShowing = true)
    }
    val pickSuggestion = { name: String ->
        trying = null
        query = name
        RecentSearches.add(searchContext, viewModel.recentAccount, name)
        triggerImmediateSearch(name)
    }
    var recent by remember(viewModel.recentAccount, query.isBlank()) {
        mutableStateOf(RecentSearches.get(searchContext, viewModel.recentAccount))
    }
    val removeRecent = { text: String ->
        RecentSearches.remove(searchContext, viewModel.recentAccount, text)
        recent = RecentSearches.get(searchContext, viewModel.recentAccount)
    }
    // Browse genres and moods: moods, genres, channel types, consoles and Watch in order, on their own page
    var browsing by rememberSaveable { mutableStateOf(false) }

    // The kind pills beside the box (Everything, Movies 6, ...); the picked one shows only that kind
    var kind by rememberSaveable { mutableStateOf<String?>(null) }
    var kinds by remember { mutableStateOf<List<SearchKind>>(emptyList()) }
    var kindQuery by rememberSaveable { mutableStateOf(query) }
    LaunchedEffect(query) {
        if (kindQuery != query) {
            kindQuery = query
            kind = null
        }
    }

    // Back from a title lands on the same result, with the letters still tucked away
    var restoreKey by remember {
        mutableStateOf(viewModel.lastOpened?.takeIf { viewModel.resultsWereFocused && query.isNotBlank() })
    }
    LaunchedEffect(Unit) {
        delay(RESTORE_GIVE_UP_MS)
        restoreKey = null
    }
    // TV: in the results, the letters tuck away and the result you're on fills the screen
    var inResults by remember { mutableStateOf(restoreKey != null) }
    val tucked = isTv && inResults && query.isNotBlank() && !browsing
    var focused by remember { mutableStateOf<SearchFocus?>(null) }
    var shown by remember { mutableStateOf<SearchFocus?>(null) }
    LaunchedEffect(Unit) {
        // Only once you stop moving (like Home), so moving along a row stays smooth on the weak TVs
        snapshotFlow { focused }.collectLatest { f ->
            if (f == null) return@collectLatest
            if (shown != null) delay(PREVIEW_DELAY_MS)
            shown = f
        }
    }

    val letters = remember { LetterLineState() }
    var focusLetters by remember { mutableIntStateOf(0) }
    LaunchedEffect(focusLetters) {
        if (focusLetters == 0) return@LaunchedEffect
        delay(LETTERS_BACK_MS)
        letters.focusLast()
    }
    val untuck: () -> Unit = {
        inResults = false
        focusLetters += 1
    }
    // The mic is the first key (left out when this device can't listen or it's turned off in the view options)
    val voice =
        rememberVoiceSearch(
            onSpeechResult = { spoken ->
                trying = null
                query = spoken
                triggerImmediateSearch(spoken)
            },
            voiceInputManager = viewModel.voiceInputManager,
        )?.takeIf { voiceSearchButtonVisible }
    // The remote's own search/mic button talks too
    val latestVoice by rememberUpdatedState(voice)
    DisposableEffect(Unit) {
        RemoteShortcuts.onSearchKeyHere = { latestVoice?.invoke() }
        onDispose { RemoteShortcuts.onSearchKeyHere = null }
    }

    // Ben's rule: Search opens on the letters. Coming from the side menu the focus is handed to the page as a whole,
    // which picks the key nearest the top left (the mic), so a key focused just as the page gets the focus moves on
    // to the A (or the key you were last on).
    val pageFocus = remember { longArrayOf(0L, 0L) } // [0] = 1 while the page has the focus, [1] = when it arrived
    var pageHasFocus by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (restoreKey != null) {
            // Back from a title: the row puts the focus back on the card. If it can't, the letters come back.
            delay(RESTORE_GIVE_UP_MS)
            if (!pageHasFocus && isTv) untuck()
            return@LaunchedEffect
        }
        delay(FIRST_FOCUS_MS)
        if (isTv) letters.focusLast() else focusRequesters.getOrNull(position.row)?.tryRequestFocus()
    }
    val typed = { text: String ->
        trying = null
        query = text
    }

    val searchBox: @Composable (Modifier) -> Unit = { boxModifier ->
        // Phones: type straight into the box with the phone's own keyboard
        val textFieldFocusRequester = remember { FocusRequester() }
        Row(
            horizontalArrangement = Arrangement.spacedBy(0.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier =
                boxModifier
                    .focusRequester(focusRequesters[SEARCH_ROW])
                    .focusProperties { onEnter = { textFieldFocusRequester.tryRequestFocus() } }
                    .focusGroup(),
        ) {
            SearchEditTextBox(
                value = query,
                onValueChange = typed,
                onSearchClick = {
                    keyboardController?.hide()
                    triggerImmediateSearch(query)
                },
                modifier =
                    Modifier
                        .weight(1f)
                        .focusRequester(textFieldFocusRequester),
            )
            if (voice != null) {
                MicPill(onClick = voice, modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
    val results: @Composable (Modifier) -> Unit = { resultsModifier ->
        Box(modifier = resultsModifier) {
            if (query.isBlank()) return@Box
            SearchCombinedResults(
                result = state.combinedResults,
                askFor = (state.seerrResults as? SearchResult.SuccessSeerr)?.items.orEmpty(),
                didYouMean = state.didYouMean,
                plainWords = state.plainWords,
                games = foundGames,
                books = foundBooks,
                onClickBook = {
                    RecentSearches.add(searchContext, viewModel.recentAccount, query)
                    viewModel.navigationManager.navigateTo(Destination.BookTitle(it.id))
                },
                musicAsk = musicAsk,
                onAskMusic = { item, kind -> viewModel.askForMusic(item, kind) },
                query = query,
                onClickGame = { game ->
                    RecentSearches.add(searchContext, viewModel.recentAccount, query)
                    val dest = game.destination
                    if (dest != null) {
                        viewModel.navigationManager.navigateTo(dest)
                    } else {
                        com.github.damontecres.wholphin.ui.games.PendingGameOpen.id = game.romId
                        viewModel.navigationManager.navigateTo(Destination.Games)
                    }
                },
                focusRequester = focusRequesters[COMBINED_ROW],
                onClickItem = onClickItem,
                onLongClickItem = { index, item ->
                    onLongClickItem(COMBINED_ROW, index, item)
                },
                onPlayItem = onPlayItem,
                onClickPosition = { setPosition(it) },
                onClickDiscover = onClickDiscover,
                positionCallback = positionCallback,
                kind = kind,
                onKinds = { kinds = it },
                onFocusResult = { focused = it },
                restoreKey = restoreKey,
                onRestored = { restoreKey = null },
                onOpened = {
                    viewModel.lastOpened = it
                    viewModel.resultsWereFocused = isTv
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
    val kindPills: @Composable (Modifier) -> Unit = { pillsModifier ->
        if (kinds.size > 1 || kind != null) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(if (isTv) 8.dp else 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                contentPadding = PaddingValues(horizontal = if (isTv) 0.dp else 16.dp),
                modifier = pillsModifier,
            ) {
                item(key = "all") {
                    DoorTab(label = "Everything", selected = kind == null, wide = isTv, onClick = { kind = null })
                }
                items(kinds, key = { it.key }) { k ->
                    DoorTab(
                        label = "${k.label} ${k.count}",
                        selected = kind == k.key,
                        wide = isTv,
                        onClick = { kind = k.key },
                    )
                }
            }
        }
    }
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .onFocusChanged {
                    if (it.hasFocus && pageFocus[0] == 0L) pageFocus[1] = System.currentTimeMillis()
                    pageFocus[0] = if (it.hasFocus) 1L else 0L
                    pageHasFocus = it.hasFocus
                }.onPreviewKeyEvent { event ->
                    // TV: a keyboard, or the remote's number buttons, type straight into the box
                    if (!isTv || browsing || event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    val native = event.nativeKeyEvent
                    val c = native.unicodeChar.toChar()
                    when {
                        native.keyCode == android.view.KeyEvent.KEYCODE_DEL -> {
                            typed(query.dropLast(1))
                            true
                        }

                        native.unicodeChar != 0 && (c.isLetterOrDigit() || (c == ' ' && query.isNotEmpty())) -> {
                            typed(query + c.lowercaseChar())
                            true
                        }

                        else -> {
                            false
                        }
                    }
                },
    ) {
        if (browsing) {
            BackHandler { browsing = false }
            val browseFocus = remember { FocusRequester() }
            LaunchedEffect(Unit) {
                delay(FIRST_FOCUS_MS)
                browseFocus.tryRequestFocus()
            }
            Column(Modifier.fillMaxSize().padding(top = LocalTopBarOverlap.current)) {
                Text(
                    text = "Browse genres and moods",
                    color = BenflixColors.Text,
                    fontSize = BenflixType.rowName,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.padding(start = 24.dp, bottom = 6.dp),
                )
                SearchStart(
                    navigationManager = viewModel.navigationManager,
                    account = viewModel.recentAccount,
                    onClickRecent = {},
                    showRecent = false,
                    showPopular = false,
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .focusRequester(browseFocus)
                            .focusGroup(),
                )
            }
        } else if (isTv) {
            BackHandler(tucked) { untuck() }
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val overlap = LocalTopBarOverlap.current
                val topHeight = (maxHeight * TOP_SHARE).coerceIn(overlap + 180.dp, overlap + 230.dp)
                val linesTop = BOX_TOP + PILL_HEIGHT + 10.dp
                val resultsTop = if (tucked) topHeight else linesTop + KEY_HEIGHT + 8.dp + LINE_HEIGHT + 14.dp
                var brightArt by remember { mutableStateOf(false) }
                val top = shown
                if (tucked && top != null) {
                    // The result you're on fills the screen, like Home
                    if (top.item != null) {
                        DoorArt(item = top.item, pageHeight = maxHeight, onBright = { brightArt = it })
                        DoorScorePill(
                            item = top.item,
                            modifier = Modifier.align(Alignment.TopEnd).padding(end = BenflixSpace.tvEdge, top = overlap + 2.dp),
                        )
                        DoorInfo(
                            item = top.item,
                            easy = false,
                            brightArt = brightArt,
                            modifier =
                                Modifier
                                    .padding(start = BenflixSpace.tvEdge, end = BenflixSpace.tvEdge, top = overlap + 2.dp)
                                    .widthIn(max = 560.dp),
                        )
                    } else {
                        DoorArtUrl(url = top.artUrl, onBright = { brightArt = it })
                        SearchTopWords(
                            focus = top,
                            modifier =
                                Modifier
                                    .padding(start = BenflixSpace.tvEdge, end = BenflixSpace.tvEdge, top = overlap + 2.dp)
                                    .widthIn(max = 560.dp),
                        )
                    }
                }
                // The box (and, in the results, the kind pills beside it) in the header's own row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(start = BenflixSpace.tvEdge, end = CLOCK_ROOM, top = BOX_TOP)
                            .onPreviewKeyEvent { event ->
                                // Up from the box or the kind pills brings the letters back
                                if (tucked && event.type == KeyEventType.KeyDown && event.key == Key.DirectionUp) {
                                    untuck()
                                    true
                                } else {
                                    false
                                }
                            },
                ) {
                    if (tucked) {
                        SearchPill(query = query, small = true, onClick = untuck, modifier = Modifier.width(SMALL_BOX))
                        kindPills(Modifier.weight(1f))
                    } else {
                        SearchPill(query = query, modifier = Modifier.widthIn(max = BIG_BOX).fillMaxWidth())
                    }
                }
                if (!tucked) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(start = BenflixSpace.tvEdge, end = BenflixSpace.tvEdge, top = linesTop)
                                .onFocusChanged { if (it.hasFocus) inResults = false },
                    ) {
                        LetterLine(
                            query = query,
                            onQueryChange = typed,
                            onMic = voice,
                            state = letters,
                            onKeyFocused = { index ->
                                // Handed over from the side menu: go on to the A (or the key you were last on)
                                if (System.currentTimeMillis() - pageFocus[1] < FOCUS_ARRIVED_MS && index != letters.target()) {
                                    letters.focusLast()
                                }
                            },
                        )
                        if (query.isBlank()) {
                            RecentLine(
                                recent = recent,
                                onPick = { text ->
                                    query = text
                                    RecentSearches.add(searchContext, viewModel.recentAccount, text)
                                    triggerImmediateSearch(text)
                                },
                                onRemove = removeRecent,
                                onBrowse = { browsing = true },
                                modifier = Modifier.height(LINE_HEIGHT),
                            )
                        } else {
                            FinishedNamesLine(
                                query = query,
                                names = suggestions.take(MAX_NAMES),
                                onPick = pickSuggestion,
                                onTry = { trying = it },
                                modifier = Modifier.height(LINE_HEIGHT),
                            )
                        }
                    }
                    if (query.isBlank()) {
                        Text(
                            text = "Results show here as you type",
                            color = BenflixColors.Text.copy(alpha = .55f),
                            fontSize = BenflixType.lead,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(start = BenflixSpace.tvEdge, top = resultsTop + 40.dp),
                        )
                        PhoneTypeHint(Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = 18.dp))
                    }
                }
                results(
                    Modifier
                        .fillMaxSize()
                        .padding(top = resultsTop)
                        .onFocusChanged { if (it.hasFocus) inResults = true },
                )
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize().padding(top = LocalTopBarOverlap.current),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                searchBox(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp))
                BenflixTipLine(tip = BenflixTipId.SEARCH, modifier = Modifier.padding(horizontal = 16.dp))
                if (query.isBlank()) {
                    // Opens nearly empty: just your recent searches and the Browse pill
                    RecentLine(
                        recent = recent,
                        onPick = { text ->
                            query = text
                            RecentSearches.add(searchContext, viewModel.recentAccount, text)
                            keyboardController?.hide()
                            triggerImmediateSearch(text)
                        },
                        onRemove = removeRecent,
                        onBrowse = { browsing = true },
                        edge = 16.dp,
                        modifier = Modifier.height(LINE_HEIGHT),
                    )
                } else {
                    if (suggestions.isNotEmpty()) {
                        SuggestionChips(
                            query = query,
                            suggestions = suggestions,
                            onPick = { name ->
                                keyboardController?.hide()
                                pickSuggestion(name)
                            },
                        )
                    }
                    kindPills(Modifier.fillMaxWidth())
                }
                results(Modifier.weight(1f).fillMaxWidth())
            }
        }
        contextMenu.Compose()
    }
    if (showViewOptions) {
        SearchViewOptionsDialog(
            combinedResults = combinedMode,
            onCombinedResultsChange = viewModel::setCombinedResults,
            voiceSearchButtonVisible = voiceSearchButtonVisible,
            onVoiceSearchButtonVisibleChange = viewModel::setVoiceSearchButtonVisible,
            onClickFilterTypes = { showFilterTypeDialog = true },
            onDismissRequest = { showViewOptions = false },
        )
    }

    if (showFilterTypeDialog) {
        SearchTypeOptionsDialog(
            onDismissRequest = { showFilterTypeDialog = false },
            searchableTypes = state.possibleSearchableTypes,
            excludedSearchableTypes = state.excludedSearchableTypes,
            discoverAvailable = seerrActive,
            discoverEnabled = state.discoverEnabled,
            onClick = viewModel::onClickExcludeSearchableType,
            onClickDiscover = viewModel::onClickExcludeDiscover,
        )
    }

    if (showProgramDialog) {
        val onDismissRequest = { showProgramDialog = false }
        ProgramDialog(
            state = programDialogState.loading,
            canRecord = true,
            onDismissRequest = onDismissRequest,
            onWatch = {
                onDismissRequest.invoke()
                val channelId = it.data.channelId
                if (channelId != null) {
                    viewModel.navigationManager.navigateTo(
                        Destination.Playback(
                            itemId = channelId,
                            positionMs = 0L,
                        ),
                    )
                } else {
                    com.github.damontecres.wholphin.ui.components.BenflixNotices
                        .pill("Program has no channel ID")
                }
            },
            onRecord = { program, series ->
                viewModel.record(
                    programId = program.id,
                    series = series,
                )
                onDismissRequest.invoke()
            },
            onCancelRecord = { program, series ->
                viewModel.cancelRecording(
                    series = series,
                    timerId = if (series) program.data.seriesTimerId else program.data.timerId,
                )
                onDismissRequest.invoke()
            },
        )
    }
}

/** TV: what the top says about a result that isn't a library title (something to ask for, a game, a book) */
@Composable
private fun SearchTopWords(
    focus: SearchFocus,
    modifier: Modifier = Modifier,
) {
    val extras = remember(focus.logoUrl) { TitleExtras(logoUrl = focus.logoUrl) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = modifier) {
        CompositionLocalProvider(LocalTitleExtras provides extras) {
            TitleBox(title = focus.name, wide = true, height = 60.dp, width = 300.dp)
        }
        if (focus.facts.isNotBlank()) {
            Text(
                text = focus.facts,
                color = BenflixColors.Text,
                fontSize = BenflixType.body,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (focus.ask) {
            DoorTag(
                text = if (focus.asked) "Asked for, on its way" else "Not on Glowfin yet · OK to ask for it",
                yellow = !focus.asked,
            )
        }
        val story = focus.story
        if (!story.isNullOrBlank()) {
            Text(
                text = story,
                color = BenflixColors.Text.copy(alpha = .92f),
                fontSize = BenflixType.small,
                lineHeight = BenflixType.small * 1.35f,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 440.dp),
            )
        }
    }
}

@Composable
fun SearchCombinedResults(
    result: SearchResult,
    focusRequester: FocusRequester,
    onClickItem: (Int, BaseItem) -> Unit,
    onLongClickItem: (Int, BaseItem) -> Unit,
    onPlayItem: (Int, BaseItem) -> Unit,
    onClickPosition: (RowColumn) -> Unit,
    onClickDiscover: (Int, DiscoverItem) -> Unit,
    positionCallback: (columns: Int, position: Int) -> Unit,
    modifier: Modifier = Modifier,
    /** Benflix redesign: the library results show as door columns, with these titles in Ask for it */
    askFor: List<DiscoverItem>? = null,
    didYouMean: String? = null,
    games: List<SearchGame> = emptyList(),
    onClickGame: (SearchGame) -> Unit = {},
    plainWords: String? = null,
    musicAsk: List<SearchMusicAsk> = emptyList(),
    onAskMusic: (SearchMusicAsk, String) -> Unit = { _, _ -> },
    query: String = "",
    books: List<BookTile> = emptyList(),
    onClickBook: (BookTile) -> Unit = {},
    kind: String? = null,
    onKinds: (List<SearchKind>) -> Unit = {},
    onFocusResult: (SearchFocus) -> Unit = {},
    restoreKey: String? = null,
    onRestored: () -> Unit = {},
    onOpened: (String) -> Unit = {},
) {
    if (askFor != null &&
        result is SearchResult.Success &&
        (result.items.isNotEmpty() || askFor.isNotEmpty() || games.isNotEmpty() || musicAsk.isNotEmpty() || books.isNotEmpty())
    ) {
        val items = remember(result) { result.items.filterNotNull() }
        SearchDoorResults(
            items = items,
            askFor = askFor,
            focusRequester = focusRequester,
            onClickItem = { onClickItem(items.indexOf(it), it) },
            onClickAsk = { onClickDiscover(0, it) },
            didYouMean = didYouMean,
            games = games,
            onClickGame = onClickGame,
            plainWords = plainWords,
            musicAsk = musicAsk,
            onAskMusic = onAskMusic,
            query = query,
            books = books,
            onClickBook = onClickBook,
            kind = kind,
            onKinds = onKinds,
            onFocusResult = onFocusResult,
            restoreKey = restoreKey,
            onRestored = onRestored,
            onOpened = onOpened,
            modifier = modifier,
        )
        return
    }
    when (result) {
        SearchResult.NoQuery -> {}

        SearchResult.Searching -> {
            SearchResultPlaceholder(
                title = stringResource(R.string.results),
                message = stringResource(R.string.searching),
                modifier = modifier.padding(16.dp),
            )
        }

        is SearchResult.Error -> {
            SearchResultPlaceholder(
                title = stringResource(R.string.results),
                message = stringResource(R.string.search_failed),
                messageColor = MaterialTheme.colorScheme.error,
                modifier = modifier.padding(16.dp),
            )
        }

        is SearchResult.Success -> {
            if (result.items.isEmpty()) {
                SearchResultPlaceholder(
                    title = stringResource(R.string.results),
                    message = stringResource(R.string.no_results),
                    modifier = modifier.padding(16.dp),
                )
            } else {
                SearchGrid(
                    items = result.items,
                    focusRequester = focusRequester,
                    onClickItem = onClickItem,
                    onLongClickItem = onLongClickItem,
                    onPlayItem = onPlayItem,
                    onClickPosition = onClickPosition,
                    positionCallback = positionCallback,
                    cardContent = { details ->
                        GridCard(
                            item = details.item,
                            onClick = details.onClick,
                            onLongClick = details.onLongClick,
                            modifier = details.mod,
                            fillWidth = details.widthPx,
                        )
                    },
                    modifier = modifier,
                )
            }
        }

        is SearchResult.SuccessSeerr -> {
            if (result.items.isEmpty()) {
                SearchResultPlaceholder(
                    title = stringResource(R.string.results),
                    message = stringResource(R.string.no_results),
                    modifier = modifier.padding(16.dp),
                )
            } else {
                SearchGrid(
                    items = result.items,
                    focusRequester = focusRequester,
                    onClickItem = onClickDiscover,
                    onLongClickItem = { _, _ -> },
                    onPlayItem = { _, _ -> },
                    onClickPosition = onClickPosition,
                    positionCallback = positionCallback,
                    cardContent = { details ->
                        DiscoverItemCard(
                            item = details.item,
                            onClick = details.onClick,
                            onLongClick = details.onLongClick,
                            modifier = details.mod,
                        )
                    },
                    modifier = modifier,
                )
            }
        }
    }
}

@Composable
private fun <T : CardGridItem> SearchGrid(
    items: List<T?>,
    focusRequester: FocusRequester,
    onClickItem: (Int, T) -> Unit,
    onLongClickItem: (Int, T) -> Unit,
    onPlayItem: (Int, T) -> Unit,
    onClickPosition: (RowColumn) -> Unit,
    cardContent: @Composable (GridItemDetails<T>) -> Unit,
    positionCallback: (columns: Int, position: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(0.dp),
        modifier = modifier,
    ) {
        ItemRowTitle(stringResource(R.string.results))

        CardGrid(
            pager = items,
            onClickItem = { index, item ->
                onClickPosition.invoke(RowColumn(COMBINED_ROW, index))
                onClickItem.invoke(index, item)
            },
            onLongClickItem = onLongClickItem,
            onClickPlay = { index, item ->
                onClickPosition.invoke(RowColumn(COMBINED_ROW, index))
                onPlayItem.invoke(index, item)
            },
            letterPosition = { -1 },
            gridFocusRequester = focusRequester,
            showJumpButtons = false,
            showLetterButtons = false,
            positionCallback = positionCallback,
            modifier = Modifier.fillMaxSize(),
            cardContent = cardContent,
        )
    }
}

@Composable
fun SearchResultPlaceholder(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    messageColor: Color = MaterialTheme.colorScheme.onBackground,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.padding(bottom = 32.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = message,
            style = MaterialTheme.typography.titleMedium,
            color = messageColor,
        )
    }
}

/** A focus that reaches a key this soon after arriving on the Search page was handed over, not moved there */
private const val FOCUS_ARRIVED_MS = 400L

/** Glowfin new look (Search, TV), at the TCL's 960 by 540 */
private val BOX_TOP = 22.dp
private val BIG_BOX = 500.dp
private val SMALL_BOX = 190.dp

/** Room left at the top right for the header's clock */
private val CLOCK_ROOM = 184.dp
private const val TOP_SHARE = 0.52f
private const val MAX_NAMES = 6
private const val PREVIEW_DELAY_MS = 250L
private const val FIRST_FOCUS_MS = 60L
private const val LETTERS_BACK_MS = 50L
private const val RESTORE_GIVE_UP_MS = 1_500L
