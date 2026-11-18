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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.preferences.AppPreference
import com.github.damontecres.wholphin.preferences.AppPreferences
import com.github.damontecres.wholphin.preferences.advancedPreferences
import com.github.damontecres.wholphin.preferences.basicPreferences
import com.github.damontecres.wholphin.services.GuestMode
import com.github.damontecres.wholphin.ui.components.BenflixIcon
import com.github.damontecres.wholphin.ui.main.ServerSection
import com.github.damontecres.wholphin.ui.main.ServerToolGroup
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.theme.Glowfin
import com.github.damontecres.wholphin.ui.touch.rememberIsTv
import com.github.damontecres.wholphin.ui.tryRequestFocus

/** One line in the Settings list: a group of settings (Behind the scenes shows all of its groups) */
private data class SettingsSection(
    @param:StringRes val title: Int,
    @param:StringRes val hint: Int,
    val group: PreferenceGroup<AppPreferences>? = null,
    val groups: List<PreferenceGroup<AppPreferences>>? = null,
    val adminOnly: Boolean = false,
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

/**
 * Glowfin (S1): the 8 groups in Ben's order (You, Watching, Picture and sound, Look, Family, Phones and TVs,
 * Help, Behind the scenes), built from the same lists the settings search uses, so nothing can go missing.
 */
private fun buildSections(guest: Boolean): List<SettingsSection> =
    buildList {
        basicPreferences.forEach { group ->
            val guestHidden =
                group.title != R.string.benflix_settings_watching &&
                    group.title != R.string.benflix_settings_picture &&
                    group.title != R.string.benflix_settings_help
            if (guest && guestHidden) {
                // Guest: only Watching, Picture and sound and Help
            } else if (group.title == R.string.more) {
                if (AppPreference.AdvancedSettings in group.preferences) {
                    add(
                        SettingsSection(
                            title = R.string.benflix_settings_advanced,
                            hint = R.string.benflix_settings_hint_advanced,
                            groups = advancedPreferences,
                            // Approved ss-q-family: the family sees everything except Behind the scenes
                            adminOnly = true,
                        ),
                    )
                }
            } else {
                add(SettingsSection(title = group.title, hint = hintFor(group.title), group = group))
            }
        }
    }

/**
 * Glowfin Settings (Ben's pick S1). TV: the groups in a plain list on the left with Find a setting on top,
 * the lit group's settings on the right. Phone: the groups list; tap one to open its page.
 */
@Composable
fun BenflixSettingsPage(
    initialPreferences: AppPreferences,
    modifier: Modifier = Modifier,
    viewModel: PreferencesViewModel = hiltViewModel(),
) {
    val isTv = rememberIsTv()
    val currentUserDto by viewModel.currentUserDto.collectAsState()
    val guest by GuestMode.active.collectAsState()
    // Always false for Guest, even on the owner's admin sign-in
    val isAdmin = !guest && GuestMode.isAdmin(currentUserDto)
    // Get the Server page ready in the background for the admin
    com.github.damontecres.wholphin.ui.main
        .PreloadServerPage(isAdmin)
    val sections =
        remember(isAdmin, guest) { buildSections(guest).filter { isAdmin || !it.adminOnly } }
    // An update is ready, so Help says "Update ready" (the update is inside Help)
    val updateAlert =
        com.github.damontecres.wholphin.ui.components
            .rememberUpdateAlert()

    // TV starts on the first group; phones start on the list of groups
    var selected by rememberSaveable { mutableIntStateOf(if (isTv) 0 else -1) }
    var query by rememberSaveable { mutableStateOf("") }
    var showSearch by remember { mutableStateOf(false) }

    @Composable
    fun Page(
        section: SettingsSection?,
        pageModifier: Modifier,
    ) {
        key(section?.title, query) {
            Column(pageModifier) {
                if (isAdmin && (query.isNotBlank())) {
                    Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        ServerToolMatches(query, onOpen = { openServerTool(viewModel, it) })
                    }
                }
                PreferencesContent(
                    initialPreferences = initialPreferences,
                    preferenceScreenOption = PreferenceScreenOption.BASIC,
                    hostedGroup = if (query.isBlank()) section?.group else null,
                    hostedGroups = if (query.isBlank()) section?.groups else null,
                    hostedTitle = if (query.isBlank()) section?.title else null,
                    hostedQuery = query.ifBlank { null },
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
            }
        }
    }

    if (isTv) {
        val firstFocus = remember { FocusRequester() }
        LaunchedEffect(Unit) { firstFocus.tryRequestFocus() }
        BackHandler(enabled = query.isNotBlank()) { query = "" }
        Row(
            horizontalArrangement = Arrangement.spacedBy(28.dp),
            modifier =
                modifier
                    .fillMaxSize()
                    .benflixLookABackground(),
        ) {
            GlowfinSideListPanel {
                Column(
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .focusRestorer(firstFocus)
                            .verticalScroll(rememberScrollState()),
                ) {
                    GlowfinPageHeading(stringResource(R.string.settings))
                    FindBox(query = query, onClick = { showSearch = true })
                    Spacer(Modifier.height(10.dp))
                    sections.forEachIndexed { index, section ->
                        GlowfinListRow(
                            label = stringResource(section.title),
                            selected = query.isBlank() && index == selected,
                            labelColor = if (section.adminOnly) GlowfinAdminYellow else Glowfin.Text,
                            note =
                                if (updateAlert && section.title == R.string.benflix_settings_help) "Update ready" else null,
                            onClick = {
                                query = ""
                                selected = index
                            },
                            // Moving through the list shows each group straight away
                            onFocused = { if (query.isBlank()) selected = index },
                            modifier =
                                if (index == selected.coerceAtLeast(0)) Modifier.focusRequester(firstFocus) else Modifier,
                        )
                    }
                }
            }
            Page(
                section = sections.getOrNull(selected.coerceAtLeast(0)),
                pageModifier =
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(end = 32.dp, top = 12.dp),
            )
        }
    } else {
        val section = sections.getOrNull(selected)
        if (section != null || query.isNotBlank()) {
            BackHandler {
                query = ""
                selected = -1
            }
            Page(
                section = section,
                pageModifier =
                    modifier
                        .fillMaxSize()
                        .benflixLookABackground(),
            )
        } else {
            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier =
                    modifier
                        .fillMaxSize()
                        .benflixLookABackground()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
            ) {
                GlowfinPageHeading(stringResource(R.string.settings))
                FindBox(query = "", onClick = { showSearch = true })
                Spacer(Modifier.height(10.dp))
                sections.forEachIndexed { index, section ->
                    GlowfinListRow(
                        label = stringResource(section.title),
                        selected = false,
                        labelColor = if (section.adminOnly) GlowfinAdminYellow else Glowfin.Text,
                        supporting = stringResource(section.hint),
                        chevron = true,
                        note = if (updateAlert && section.title == R.string.benflix_settings_help) "Update ready" else null,
                        onClick = { selected = index },
                    )
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp)
                            .height(1.dp)
                            .background(Glowfin.Line),
                    )
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
    val shape = RoundedCornerShape(99.dp)
    androidx.tv.material3.Surface(
        onClick = onClick,
        shape =
            androidx.tv.material3.ClickableSurfaceDefaults
                .shape(shape),
        colors =
            androidx.tv.material3.ClickableSurfaceDefaults.colors(
                containerColor = Glowfin.Raise,
                contentColor = Glowfin.Dim,
                focusedContainerColor = Glowfin.FocusRow,
                focusedContentColor = Glowfin.Text,
            ),
        border =
            androidx.tv.material3.ClickableSurfaceDefaults.border(
                border =
                    androidx.tv.material3.Border(
                        androidx.compose.foundation.BorderStroke(1.dp, Glowfin.Line),
                        shape = shape,
                    ),
                focusedBorder =
                    androidx.tv.material3.Border(
                        androidx.compose.foundation.BorderStroke(2.dp, Glowfin.Aqua),
                        shape = shape,
                    ),
            ),
        scale =
            androidx.tv.material3.ClickableSurfaceDefaults
                .scale(focusedScale = 1f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
        ) {
            Icon(imageVector = BenflixIcon.SEARCH.vector, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                text = query.ifBlank { stringResource(R.string.benflix_settings_search) },
                fontSize = 15.sp,
                color = if (query.isBlank()) Glowfin.Dim else Glowfin.Text,
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
            ServerToolEntry("Glowfin versions", "versions update out of date", ServerSection.TOOLS, ServerToolGroup.PEOPLE),
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
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = Glowfin.Dim,
            modifier = Modifier.padding(start = 8.dp, bottom = 2.dp),
        )
        found.take(MAX_SERVER_MATCHES).forEach { e ->
            GlowfinListRow(
                label = e.title,
                selected = false,
                supporting = "Server › " + (e.group?.title ?: e.section.title),
                chevron = true,
                onClick = { onOpen(e) },
            )
        }
    }
}

private const val MAX_SERVER_MATCHES = 4
