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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
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
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.touch.rememberIsTv
import com.github.damontecres.wholphin.ui.tryRequestFocus
import com.github.damontecres.wholphin.ui.theme.BenflixType

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
    when (title) {
        R.string.benflix_settings_profile -> Icons.Default.Person
        R.string.benflix_settings_watching -> Icons.Default.PlayArrow
        R.string.benflix_settings_picture -> Icons.Default.Settings
        R.string.benflix_settings_look -> Icons.Default.Star
        R.string.benflix_settings_phone -> Icons.Default.Phone
        R.string.benflix_settings_parental -> Icons.Default.Lock
        else -> Icons.Default.Info
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
                                    icon = Icons.Default.Share,
                                    opensQuickConnect = true,
                                ),
                            )
                        }

                        AppPreference.ServerPage -> {
                            add(
                                SettingsSection(
                                    title = R.string.benflix_server_page,
                                    hint = R.string.benflix_settings_hint_server,
                                    icon = Icons.AutoMirrored.Filled.List,
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
                                    icon = Icons.Default.Build,
                                    destination = Destination.Settings(PreferenceScreenOption.ADVANCED),
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
    // Benflix: an update is ready, so Help shows a red "!" (the update is inside Help)
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
                MenuRow(
                    title = stringResource(R.string.benflix_settings_search),
                    icon = Icons.Default.Search,
                    selected = query.isNotBlank(),
                    supporting = query.ifBlank { null },
                    onClick = { showSearch = true },
                )
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
                    PreferencesContent(
                        initialPreferences = initialPreferences,
                        preferenceScreenOption = PreferenceScreenOption.BASIC,
                        hostedGroup = group,
                        hostedQuery = query,
                        modifier = Modifier.fillMaxSize(),
                    )
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
                PreferencesContent(
                    initialPreferences = initialPreferences,
                    preferenceScreenOption = PreferenceScreenOption.BASIC,
                    hostedGroup = group,
                    hostedQuery = query,
                    modifier =
                        modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background),
                )
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
                Card {
                    MenuRow(
                        title = stringResource(R.string.benflix_settings_search),
                        icon = Icons.Default.Search,
                        selected = false,
                        onClick = { showSearch = true },
                    )
                }
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
            )
        },
        supportingContent =
            supporting?.let {
                {
                    Text(text = it, fontSize = BenflixType.small, maxLines = 1)
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
                            com.github.damontecres.wholphin.ui.components
                                .UpdateBang(size = 18.dp)
                            if (chevron) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
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
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
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
