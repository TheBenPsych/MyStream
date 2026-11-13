package com.github.damontecres.wholphin.ui.preferences

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.tv.material3.Icon
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.tv.material3.surfaceColorAtElevation
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.preferences.AppPreference
import com.github.damontecres.wholphin.preferences.AppPreferences
import com.github.damontecres.wholphin.preferences.basicPreferences
import com.github.damontecres.wholphin.services.GuestMode
import com.github.damontecres.wholphin.ui.components.BenflixIcon
import com.github.damontecres.wholphin.ui.main.ServerSection
import com.github.damontecres.wholphin.ui.main.ServerToolGroup
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.theme.BenflixColors
import com.github.damontecres.wholphin.ui.theme.BenflixType
import com.github.damontecres.wholphin.ui.touch.rememberIsTv
import com.github.damontecres.wholphin.ui.tryRequestFocus

/** One line in the Settings menu: a section of settings, or a page it opens */
private data class SettingsSection(
    @param:StringRes val title: Int,
    @param:StringRes val hint: Int,
    val icon: ImageVector,
    val group: PreferenceGroup<AppPreferences>? = null,
    val destination: Destination? = null,
    val adminOnly: Boolean = false,
    /** Opens a pop-up instead of a page (Quick Connect) */
    val opensQuickConnect: Boolean = false,
)

private fun hintFor(
    @StringRes title: Int,
): Int =
    when (title) {
        R.string.benflix_settings_profile -> R.string.benflix_settings_hint_profile
        R.string.benflix_settings_watching -> R.string.benflix_settings_hint_watching
        R.string.benflix_settings_picture -> R.string.benflix_settings_hint_picture
        R.string.benflix_settings_look -> R.string.benflix_settings_hint_look
        R.string.benflix_settings_phone -> R.string.benflix_settings_hint_phone
        R.string.benflix_settings_parental -> R.string.benflix_settings_hint_parental
        else -> R.string.benflix_settings_hint_help
    }

private fun iconFor(
    @StringRes title: Int,
): ImageVector =
    // Redesign 2 (approved ss-family): the same line icons as the door rail, one per group
    when (title) {
        R.string.benflix_settings_profile -> BenflixIcon.PERSON.vector
        R.string.benflix_settings_watching -> BenflixIcon.PLAY.vector
        R.string.benflix_settings_picture -> BenflixIcon.TV.vector
        R.string.benflix_settings_look -> BenflixIcon.IMAGE.vector
        R.string.benflix_settings_phone -> BenflixIcon.PHONE.vector
        R.string.benflix_settings_parental -> BenflixIcon.LOCK.vector
        else -> BenflixIcon.HELP.vector
    }

/** The sections, built from the same lists the settings search uses, so nothing can go missing */
private fun buildSections(
    isTv: Boolean,
    guest: Boolean,
): List<SettingsSection> =
    buildList {
        basicPreferences.forEach { group ->
            val guestHidden =
                group.title != R.string.benflix_settings_watching &&
                    group.title != R.string.benflix_settings_picture &&
                    group.title != R.string.benflix_settings_help
            if (guest && guestHidden) {
                // Guest: only Watching and Help (no Quick Connect, Server page, Advanced, profile or parental)
            } else if (group.title == R.string.more) {
                group.preferences.forEach { pref ->
                    when (pref) {
                        AppPreference.QuickConnect -> {
                            add(
                                SettingsSection(
                                    title = R.string.quick_connect,
                                    hint = R.string.quick_connect_summary,
                                    icon = BenflixIcon.SHARE.vector,
                                    opensQuickConnect = true,
                                ),
                            )
                        }

                        AppPreference.ServerPage -> {
                            add(
                                SettingsSection(
                                    title = R.string.benflix_server_page,
                                    hint = R.string.benflix_settings_hint_server,
                                    icon = BenflixIcon.SERVER.vector,
                                    destination = Destination.ServerStatus,
                                    adminOnly = true,
                                ),
                            )
                        }

                        AppPreference.AdvancedSettings -> {
                            add(
                                SettingsSection(
                                    title = R.string.benflix_settings_advanced,
                                    hint = R.string.benflix_settings_hint_advanced,
                                    icon = BenflixIcon.GEAR.vector,
                                    destination = Destination.Settings(PreferenceScreenOption.ADVANCED),
                                    // Approved ss-q-family: the family sees everything except Behind the scenes
                                    adminOnly = true,
                                ),
                            )
                        }

                        else -> {}
                    }
                }
            } else if (isTv && group.title == R.string.benflix_settings_phone) {
                // Mobile data only matters on phones
            } else {
                add(
                    SettingsSection(
                        title = group.title,
                        hint = hintFor(group.title),
                        icon = iconFor(group.title),
                        group = group,
                    ),
                )
            }
        }
    }

/**
 * Benflix Settings. TV: the sections in a menu on the left, the chosen section's settings as cards
 * on the right. Phone: a grouped list of sections; tap one to open it.
 */
@Composable
fun BenflixSettingsPage(
    initialPreferences: AppPreferences,
    modifier: Modifier = Modifier,
    viewModel: PreferencesViewModel = hiltViewModel(),
) {
    val isTv = rememberIsTv()
    val currentUser by viewModel.currentUser.collectAsState()
    val currentUserDto by viewModel.currentUserDto.collectAsState()
    val guest by GuestMode.active.collectAsState()
    // Always false for Guest, even on the owner's admin sign-in
    val isAdmin = !guest && GuestMode.isAdmin(currentUserDto)
    // Benflix: get the Server page ready in the background for the admin
    com.github.damontecres.wholphin.ui.main
        .PreloadServerPage(isAdmin)
    val sections =
        remember(isTv, isAdmin, guest) { buildSections(isTv, guest).filter { isAdmin || !it.adminOnly } }
    val groupSections = sections.filter { it.group != null }
    // Benflix: an update is ready, so Help says "Update ready" (the update is inside Help)
    val updateAlert =
        com.github.damontecres.wholphin.ui.components
            .rememberUpdateAlert()

    // TV starts on the first section; phones start on the list of sections
    var selected by rememberSaveable { mutableIntStateOf(if (isTv) 0 else -1) }
    var query by rememberSaveable { mutableStateOf("") }
    var showSearch by remember { mutableStateOf(false) }

    val userName = currentUser?.name ?: ""
    var showQuickConnect by remember { mutableStateOf(false) }
    if (showQuickConnect) BenflixQuickConnect(onDismiss = { showQuickConnect = false })
    val openSection: (SettingsSection) -> Unit = { section ->
        if (section.opensQuickConnect) {
            if (!GuestMode.isActive) showQuickConnect = true
        } else if (section.destination != null) {
            viewModel.navigationManager.navigateTo(section.destination)
        } else {
            query = ""
            selected = groupSections.indexOf(section)
        }
    }

    if (isTv) {
        val firstFocus = remember { FocusRequester() }
        LaunchedEffect(Unit) { firstFocus.tryRequestFocus() }
        BackHandler(enabled = query.isNotBlank()) { query = "" }
        Row(
            horizontalArrangement = Arrangement.spacedBy(32.dp),
            modifier =
                modifier
                    .fillMaxSize()
                    .benflixLookABackground()
                    .padding(start = 32.dp, end = 32.dp, top = 24.dp),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier =
                    Modifier
                        .width(320.dp)
                        .fillMaxHeight()
                        .focusRestorer(firstFocus)
                        .verticalScroll(rememberScrollState()),
            ) {
                ProfileHeader(userName)
                FindBox(query = query, onClick = { showSearch = true })
                Spacer(Modifier.height(8.dp))
                sections.forEach { section ->
                    val index = groupSections.indexOf(section)
                    MenuRow(
                        title = stringResource(section.title),
                        icon = section.icon,
                        selected = query.isBlank() && index >= 0 && index == selected,
                        badge = if (section.adminOnly) stringResource(R.string.benflix_settings_admin) else null,
                        alert = updateAlert && section.title == R.string.benflix_settings_help,
                        onClick = { openSection(section) },
                        // Moving through the menu shows each section straight away
                        onFocused = { if (index >= 0 && query.isBlank()) selected = index },
                        modifier =
                            Modifier.then(
                                if (index == selected.coerceAtLeast(0)) Modifier.focusRequester(firstFocus) else Modifier,
                            ),
                    )
                }
            }
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxHeight(),
            ) {
                val group = groupSections.getOrNull(selected.coerceAtLeast(0))?.group
                key(selected, query) {
                    Column(Modifier.fillMaxSize()) {
                        if (isAdmin) ServerToolMatches(query, onOpen = { openServerTool(viewModel, it) })
                        PreferencesContent(
                            initialPreferences = initialPreferences,
                            preferenceScreenOption = PreferenceScreenOption.BASIC,
                            hostedGroup = group,
                            hostedQuery = query,
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                        )
                    }
                }
            }
        }
    } else {
        val group = groupSections.getOrNull(selected)?.group
        if (group != null || query.isNotBlank()) {
            BackHandler {
                query = ""
                selected = -1
            }
            key(selected, query) {
                Column(
                    modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background),
                ) {
                    if (isAdmin && group == null) {
                        Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                            ServerToolMatches(query, onOpen = { openServerTool(viewModel, it) })
                        }
                    }
                    PreferencesContent(
                        initialPreferences = initialPreferences,
                        preferenceScreenOption = PreferenceScreenOption.BASIC,
                        hostedGroup = group,
                        hostedQuery = query,
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                    )
                }
            }
        } else {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier =
                    modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
            ) {
                Text(
                    text = stringResource(R.string.settings),
                    fontWeight = FontWeight.Bold,
                    fontSize = BenflixType.pageTitle,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                FindBox(query = "", onClick = { showSearch = true })
                // Your profile on its own card, then everyday sections, then help and admin pages
                val profile = sections.filter { it.title == R.string.benflix_settings_profile }
                val everyday =
                    sections.filter {
                        it.group != null && it.title != R.string.benflix_settings_profile &&
                            it.title != R.string.benflix_settings_help
                    }
                val rest = sections - profile.toSet() - everyday.toSet()
                listOf(profile, everyday, rest).filter { it.isNotEmpty() }.forEach { cardSections ->
                    Card {
                        cardSections.forEach { section ->
                            MenuRow(
                                title =
                                    if (section.title == R.string.benflix_settings_profile && userName.isNotBlank()) {
                                        userName
                                    } else {
                                        stringResource(section.title)
                                    },
                                icon = section.icon,
                                selected = false,
                                supporting = stringResource(section.hint),
                                chevron = true,
                                alert = updateAlert && section.title == R.string.benflix_settings_help,
                                onClick = { openSection(section) },
                            )
                        }
                    }
                }
            }
        }
    }

    if (showSearch) {
        StringInputDialog(
            input =
                StringInput(
                    title = stringResource(R.string.benflix_settings_search),
                    value = query,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    onSubmit = {},
                ),
            onSave = {
                showSearch = false
                query = it.trim()
            },
            onDismissRequest = { showSearch = false },
        )
    }
}

/** Approved ss-family: the "Find a setting" box at the top of the list (for Ben it also finds Server tools) */
@Composable
private fun FindBox(
    query: String,
    onClick: () -> Unit,
) {
    androidx.tv.material3.Surface(
        onClick = onClick,
        shape =
            androidx.tv.material3.ClickableSurfaceDefaults
                .shape(RoundedCornerShape(10.dp)),
        colors =
            androidx.tv.material3.ClickableSurfaceDefaults.colors(
                containerColor = BenflixColors.Raised,
                contentColor = BenflixColors.Soft,
                focusedContainerColor = BenflixColors.FocusRow,
                focusedContentColor = BenflixColors.Text,
            ),
        border =
            androidx.tv.material3.ClickableSurfaceDefaults.border(
                border =
                    androidx.tv.material3.Border(
                        androidx.compose.foundation.BorderStroke(1.dp, BenflixColors.Line),
                        shape = RoundedCornerShape(10.dp),
                    ),
                focusedBorder =
                    androidx.tv.material3.Border(
                        androidx.compose.foundation.BorderStroke(2.5.dp, BenflixColors.Text),
                        shape = RoundedCornerShape(10.dp),
                    ),
            ),
        scale =
            androidx.tv.material3.ClickableSurfaceDefaults
                .scale(focusedScale = 1f),
        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Icon(imageVector = BenflixIcon.SEARCH.vector, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Text(
                text = query.ifBlank { stringResource(R.string.benflix_settings_search) },
                fontSize = BenflixType.body,
                color = if (query.isBlank()) BenflixColors.Muted else BenflixColors.Text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** One Server tool "Find a setting" can find for Ben, and where it lives on the Server page */
private data class ServerToolEntry(
    val title: String,
    val words: String,
    val section: com.github.damontecres.wholphin.ui.main.ServerSection,
    val group: com.github.damontecres.wholphin.ui.main.ServerToolGroup? = null,
)

private val serverToolEntries: List<ServerToolEntry> =
    run {
        listOf(
            ServerToolEntry("Who's watching now", "watching streams stop pause message", ServerSection.NOW),
            ServerToolEntry("Disk space", "disks full storage", ServerSection.LIBRARY),
            ServerToolEntry("People and accounts", "users add person password account", ServerSection.PEOPLE),
            ServerToolEntry("Scan for new stuff", "scan library refresh new files", ServerSection.TOOLS, ServerToolGroup.LIBRARY),
            ServerToolEntry("Library checkup", "missing episodes low quality checkup", ServerSection.TOOLS, ServerToolGroup.LIBRARY),
            ServerToolEntry("Fix posters", "poster pictures art blurry", ServerSection.TOOLS, ServerToolGroup.LIBRARY),
            ServerToolEntry("Downloads", "stuck download find another sonarr radarr", ServerSection.TOOLS, ServerToolGroup.DOWNLOADS),
            ServerToolEntry("Messages", "message note send pop-up", ServerSection.TOOLS, ServerToolGroup.PEOPLE),
            ServerToolEntry("Benflix versions", "versions update out of date", ServerSection.TOOLS, ServerToolGroup.PEOPLE),
            ServerToolEntry("Kids ratings", "kids ratings age rating allowed", ServerSection.TOOLS, ServerToolGroup.PEOPLE),
            ServerToolEntry("Track names", "audio subtitle tracks tidy labels languages", ServerSection.TOOLS, ServerToolGroup.FIXES),
            ServerToolEntry("Speed check", "speed internet playback stutter", ServerSection.TOOLS, ServerToolGroup.FIXES),
            ServerToolEntry("Setup command", "setup command tower helper terminal", ServerSection.TOOLS, ServerToolGroup.TOWER),
            ServerToolEntry("Restart Jellyfin", "restart reboot jellyfin apps", ServerSection.TOOLS, ServerToolGroup.TOWER),
            ServerToolEntry("Server chores", "chores tasks scheduled", ServerSection.TOOLS, ServerToolGroup.TOWER),
            ServerToolEntry("Weather town", "weather town home", ServerSection.TOOLS, ServerToolGroup.TOWER),
            ServerToolEntry("Book lists", "books lists nyt bestsellers", ServerSection.TOOLS, ServerToolGroup.TOWER),
        )
    }

private fun openServerTool(
    viewModel: PreferencesViewModel,
    entry: ServerToolEntry,
) {
    com.github.damontecres.wholphin.ui.main.ServerPageJump.section = entry.section
    com.github.damontecres.wholphin.ui.main.ServerPageJump.group = entry.group
    viewModel.navigationManager.navigateTo(Destination.ServerStatus)
}

/** Approved ss-q-find: for Ben, "Find a setting" also finds Server tools (shown above the matching settings) */
@Composable
private fun ServerToolMatches(
    query: String,
    onOpen: (ServerToolEntry) -> Unit,
) {
    val q = query.trim().lowercase()
    if (q.length < 2) return
    val found =
        serverToolEntries.filter { e ->
            e.title.lowercase().contains(q) || e.words.split(" ").any { it.startsWith(q) }
        }
    if (found.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(bottom = 8.dp)) {
        Text(
            text = "On the Server page",
            fontSize = BenflixType.small,
            fontWeight = FontWeight.SemiBold,
            color = BenflixColors.Muted,
            modifier = Modifier.padding(start = 8.dp, bottom = 2.dp),
        )
        found.take(MAX_SERVER_MATCHES).forEach { e ->
            MenuRow(
                title = e.title,
                icon = BenflixIcon.SERVER.vector,
                selected = false,
                supporting = "Server › " + (e.group?.title ?: e.section.title),
                chevron = true,
                onClick = { onOpen(e) },
            )
        }
    }
}

private const val MAX_SERVER_MATCHES = 4

@Composable
private fun ProfileHeader(name: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.padding(start = 8.dp, top = 4.dp, bottom = 12.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier =
                Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.primary),
        ) {
            Text(
                text = name.take(1).uppercase(),
                fontSize = BenflixType.rowName,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }
        Column {
            Text(
                text = stringResource(R.string.settings),
                fontWeight = FontWeight.Bold,
                fontSize = BenflixType.rowName,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (name.isNotBlank()) {
                Text(
                    text = name,
                    fontSize = BenflixType.small,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)),
    ) {
        content()
    }
}

@Composable
private fun MenuRow(
    title: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    badge: String? = null,
    chevron: Boolean = false,
    alert: Boolean = false,
    onFocused: () -> Unit = {},
) {
    // Benflix look A: flat rows; the section you're on is filled with the profile colour
    val accent = com.github.damontecres.wholphin.ui.theme.BenflixRed
    ListItem(
        selected = selected,
        onClick = onClick,
        scale =
            androidx.tv.material3.ListItemDefaults
                .scale(focusedScale = 1f),
        shape =
            androidx.tv.material3.ListItemDefaults
                .shape(RoundedCornerShape(10.dp)),
        border =
            androidx.tv.material3.ListItemDefaults.border(
                focusedSelectedBorder =
                    androidx.tv.material3.Border(
                        androidx.compose.foundation.BorderStroke(3.dp, Color.White),
                        shape = RoundedCornerShape(10.dp),
                    ),
            ),
        colors =
            androidx.tv.material3.ListItemDefaults.colors(
                selectedContainerColor = accent,
                selectedContentColor = Color.White,
                focusedSelectedContainerColor = accent,
                focusedSelectedContentColor = Color.White,
            ),
        headlineContent = {
            Text(
                text = title,
                fontSize = BenflixType.lead,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent =
            supporting?.let {
                {
                    Text(text = it, fontSize = BenflixType.small, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            },
        leadingContent = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
            )
        },
        trailingContent =
            when {
                alert -> {
                    {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Notices redesign: words instead of a red "!"
                            Text(text = "Update ready", fontSize = BenflixType.tag, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (chevron) {
                                Icon(
                                    imageVector = BenflixIcon.CHEVRON.vector,
                                    contentDescription = null,
                                    modifier = Modifier.padding(start = 6.dp).size(20.dp),
                                )
                            }
                        }
                    }
                }

                badge != null -> {
                    {
                        Text(text = badge, fontSize = BenflixType.tag, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                chevron -> {
                    {
                        Icon(
                            imageVector = BenflixIcon.CHEVRON.vector,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }

                else -> {
                    null
                }
            },
        modifier =
            modifier
                .fillMaxWidth()
                .onFocusChanged { if (it.isFocused) onFocused() },
    )
}
