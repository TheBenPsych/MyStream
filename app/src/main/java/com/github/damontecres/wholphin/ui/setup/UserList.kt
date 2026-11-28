package com.github.damontecres.wholphin.ui.setup

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Border
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.model.JellyfinUser
import com.github.damontecres.wholphin.ui.Cards
import com.github.damontecres.wholphin.ui.PreviewTvSpec
import com.github.damontecres.wholphin.ui.components.BenflixIcon
import com.github.damontecres.wholphin.ui.components.DialogItem
import com.github.damontecres.wholphin.ui.components.DialogPopup
import com.github.damontecres.wholphin.ui.isNotNullOrBlank
import com.github.damontecres.wholphin.ui.theme.BenflixColors
import com.github.damontecres.wholphin.ui.theme.BenflixShapes
import com.github.damontecres.wholphin.ui.theme.BenflixType
import com.github.damontecres.wholphin.ui.theme.Button
import com.github.damontecres.wholphin.ui.theme.WholphinTheme
import com.github.damontecres.wholphin.ui.theme.glowFocus
import com.github.damontecres.wholphin.ui.theme.themeIndex
import com.github.damontecres.wholphin.ui.toServerString
import com.github.damontecres.wholphin.ui.tryRequestFocus
import java.util.UUID

/**
 * Display a list of users plus option to add a new one or switch servers
 * Redesigned to match streaming service style with horizontal scrollable user icons
 */
@Composable
fun UserList(
    users: List<JellyfinUserAndImage>,
    currentUser: JellyfinUser?,
    onSwitchUser: (JellyfinUser) -> Unit,
    onAddUser: () -> Unit,
    onRemoveUser: (JellyfinUser) -> Unit,
    onSwitchServer: () -> Unit,
    modifier: Modifier = Modifier,
    kidsUser: JellyfinUser? = null,
    onKids: (JellyfinUser) -> Unit = {},
    /** Benflix "Big kids" tile (Parental controls), next to Kids */
    showBigKids: Boolean = false,
    onBigKids: (JellyfinUser) -> Unit = {},
    /** Benflix Guest tile: watch without saving anything to the owner's account */
    showGuest: Boolean = false,
    onGuest: (JellyfinUser) -> Unit = {},
    /** A profile picture was picked (null = automatic). Saved to their Jellyfin account so it follows them. */
    onPickPicture: (JellyfinUser, Int?) -> Unit = { user, index -> AvatarChoices.set(user.id, index) },
) {
    var showDeleteDialog by remember { mutableStateOf<JellyfinUserAndImage?>(null) }
    var pickPictureFor by remember { mutableStateOf<JellyfinUserAndImage?>(null) }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        // Horizontal scrollable list of user icons - centered
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            val focusRequester = remember { FocusRequester() }
            val firstFocusRequester = remember { FocusRequester() }
            if (users.isNotEmpty()) {
                LaunchedEffect(Unit) { focusRequester.tryRequestFocus() }
            }
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(24.dp), // Spacing to accommodate 20% scale
                contentPadding = PaddingValues(horizontal = 48.dp, vertical = 12.dp), // Room for the 20% zoom
                modifier =
                    Modifier
                        .wrapContentWidth()
                        .focusRestorer(firstFocusRequester)
                        .focusRequester(focusRequester),
            ) {
                // Focus starts on whoever watched last
                val startIndex = users.indexOfFirst { it.user.id == currentUser?.id }.coerceAtLeast(0)
                itemsIndexed(users) { index, user ->
                    UserIconCard(
                        user = user,
                        isCurrentUser = user.user.id == currentUser?.id,
                        onClick = { onSwitchUser.invoke(user.user) },
                        onLongClick = { showDeleteDialog = user },
                        modifier = if (index == startIndex) Modifier.focusRequester(firstFocusRequester) else Modifier,
                    )
                }
                kidsUser?.let { grownUp ->
                    item {
                        SmallerTile { KidsProfileCard(onClick = { onKids.invoke(grownUp) }) }
                    }
                    if (showBigKids) {
                        item {
                            SmallerTile { KidsProfileCard(onClick = { onBigKids.invoke(grownUp) }, big = true) }
                        }
                    }
                    if (showGuest) {
                        item {
                            SmallerTile { GuestProfileCard(onClick = { onGuest.invoke(grownUp) }) }
                        }
                    }
                }
            }
        }

        // Redesign 2 (su-family): one small line says how to change your picture
        if (users.isNotEmpty()) {
            val isTv =
                com.github.damontecres.wholphin.ui.touch
                    .rememberIsTv()
            Text(
                text = if (isTv) "Hold OK on a picture to change it" else "Press and hold a picture to change it",
                fontSize = BenflixType.small,
                color = com.github.damontecres.wholphin.ui.theme.BenflixColors.Muted,
                textAlign = TextAlign.Center,
            )
        }
        // Small links under the row: add someone, or change server
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
            modifier = Modifier.fillMaxWidth(),
        ) {
            WhoLink(text = "Add someone", onClick = onAddUser)
            WhoLink(text = "Change server", onClick = onSwitchServer)
        }
    }
    showDeleteDialog?.let { user ->
        DialogPopup(
            showDialog = true,
            title = user.user.name ?: user.user.id.toServerString(),
            dialogItems =
                buildList {
                    // "Hold OK on a picture to change it": changing the picture comes first
                    add(
                        DialogItem(
                            stringResource(R.string.change_picture),
                            R.string.fa_user,
                        ) {
                            pickPictureFor = user
                        },
                    )
                    add(
                        DialogItem(
                            stringResource(R.string.switch_user),
                            R.string.fa_arrow_left_arrow_right,
                        ) {
                            onSwitchUser.invoke(user.user)
                        },
                    )
                    if (user.known) {
                        add(
                            DialogItem(
                                stringResource(R.string.delete),
                                BenflixIcon.TRASH.vector,
                                Color.Red.copy(alpha = .8f),
                            ) {
                                onRemoveUser.invoke(user.user)
                            },
                        )
                    }
                },
            onDismissRequest = { showDeleteDialog = null },
            dismissOnClick = true,
            waitToLoad = true,
            properties = DialogProperties(),
        )
    }
    pickPictureFor?.let { user ->
        AvatarPickerDialog(
            userId = user.user.id,
            name = user.user.name,
            onPick = { index -> onPickPicture.invoke(user.user, index) },
            onDismissRequest = { pickPictureFor = null },
        )
    }
}

/**
 * Kids, Big kids and Guest: a little smaller than the grown-ups, pushed down so every name sits on
 * the same line as the grown-ups' names.
 */
@Composable
private fun SmallerTile(content: @Composable () -> Unit) {
    val full = LocalProfileTileSize.current
    val small = full * SMALL_TILE
    androidx.compose.runtime.CompositionLocalProvider(LocalProfileTileSize provides small) {
        // The gap under a picture grows with its size (a tenth of it), so that part moves too
        Box(Modifier.padding(top = (full - small) * 1.1f)) { content() }
    }
}

/** How big the Kids, Big kids and Guest pictures are next to the grown-ups' */
private const val SMALL_TILE = 0.78f

/** A quiet text link on Who's watching that lights up when focused */
@Composable
private fun WhoLink(
    text: String,
    onClick: () -> Unit,
) {
    Surface(
        border =
            com.github.damontecres.wholphin.ui.theme
                .benflixFocusBorder(BenflixShapes.Pill),
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(BenflixShapes.Pill),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = Color.Transparent,
                contentColor = com.github.damontecres.wholphin.ui.theme.BenflixColors.Muted,
                focusedContainerColor = com.github.damontecres.wholphin.ui.theme.BenflixColors.FocusRow,
                focusedContentColor = com.github.damontecres.wholphin.ui.theme.BenflixColors.Text,
            ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
    ) {
        Text(
            text = text,
            fontSize = BenflixType.body,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

/**
 * Glowfin (ui-5): pick your sea friend, then its colour. The big preview shows what you'll get. OK on an animal
 * saves it; the colour dots change the colour straight away ([onPickColour], saved with your settings so it
 * follows you). Null = automatic.
 */
@Composable
fun AvatarPickerDialog(
    userId: UUID,
    name: String?,
    onPick: (Int?) -> Unit,
    onDismissRequest: () -> Unit,
    onPickColour: ((com.github.damontecres.wholphin.preferences.BenflixAccent) -> Unit)? = null,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    // Make sure the pick is saved on the device, even if no picture has been drawn yet
    AvatarChoices.init(context)
    com.github.damontecres.wholphin.ui.theme.ProfileColours
        .init(context)
    val current = AvatarChoices.get(userId)
    val starter = benflixAvatarFor(userId, name)
    var preview by remember { mutableStateOf(current ?: BenflixAvatars.indexOf(starter).coerceAtLeast(0)) }
    val colour = seaColourIndex(userId, BenflixAvatars.getOrElse(preview) { BenflixAvatars[0] })
    com.github.damontecres.wholphin.ui.touch.Dialog(onDismissRequest = onDismissRequest) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(28.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier =
                Modifier
                    .background(
                        com.github.damontecres.wholphin.ui.theme.Glowfin.Deep,
                        BenflixShapes.Panel,
                    ).padding(24.dp),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    text = "Pick your sea friend",
                    style = MaterialTheme.typography.titleLarge,
                )
                val firstFocus = remember { FocusRequester() }
                LaunchedEffect(Unit) { firstFocus.tryRequestFocus() }
                SeaFriend.entries.chunked(4).forEachIndexed { rowIndex, row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        row.forEachIndexed { col, friend ->
                            val index = rowIndex * 4 + col
                            Surface(
                                onClick = {
                                    onPick.invoke(index)
                                    onDismissRequest.invoke()
                                },
                                shape = ClickableSurfaceDefaults.shape(CircleShape),
                                colors =
                                    ClickableSurfaceDefaults.colors(
                                        containerColor = Color.Transparent,
                                        focusedContainerColor = Color.Transparent,
                                    ),
                                border =
                                    ClickableSurfaceDefaults.border(
                                        border =
                                            androidx.tv.material3.Border(
                                                androidx.compose.foundation.BorderStroke(
                                                    3.dp,
                                                    if (index == current) Color.White else Color.Transparent,
                                                ),
                                            ),
                                        focusedBorder =
                                            androidx.tv.material3.Border(
                                                androidx.compose.foundation.BorderStroke(3.dp, BenflixColors.Focus),
                                            ),
                                    ),
                                modifier =
                                    Modifier
                                        .glowFocus(CircleShape, spread = 10.dp)
                                        .size(68.dp)
                                        .onFocusChanged { if (it.isFocused) preview = index }
                                        .then(if (index == (current ?: 0)) Modifier.focusRequester(firstFocus) else Modifier),
                                scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                            ) {
                                SeaFriendImage(
                                    friend = friend,
                                    colour = colour,
                                    contentDescription = friend.title,
                                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                                )
                            }
                        }
                    }
                }
                Text(
                    text = "Colour",
                    style = MaterialTheme.typography.bodyMedium,
                    color = com.github.damontecres.wholphin.ui.theme.Glowfin.Dim,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    com.github.damontecres.wholphin.ui.theme.ProfileThemeChoices.forEachIndexed { i, accent ->
                        val (a, b) = SeaColours[i]
                        Surface(
                            onClick = {
                                com.github.damontecres.wholphin.ui.theme.ProfileColours
                                    .set(context, userId, accent)
                                onPickColour?.invoke(accent)
                            },
                            shape = ClickableSurfaceDefaults.shape(CircleShape),
                            colors =
                                ClickableSurfaceDefaults.colors(
                                    containerColor = Color.Transparent,
                                    focusedContainerColor = Color.Transparent,
                                ),
                            border =
                                ClickableSurfaceDefaults.border(
                                    border =
                                        androidx.tv.material3.Border(
                                            androidx.compose.foundation.BorderStroke(
                                                2.dp,
                                                if (i == colour) Color.White else Color.Transparent,
                                            ),
                                        ),
                                    focusedBorder =
                                        androidx.tv.material3.Border(
                                            androidx.compose.foundation.BorderStroke(3.dp, BenflixColors.Focus),
                                        ),
                                ),
                            scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                            modifier = Modifier.size(32.dp),
                        ) {
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .padding(4.dp)
                                    .background(
                                        androidx.compose.ui.graphics.Brush
                                            .linearGradient(listOf(a, b)),
                                        CircleShape,
                                    ),
                            )
                        }
                    }
                }
                Button(onClick = {
                    onPick.invoke(null)
                    onDismissRequest.invoke()
                }, scale = ButtonDefaults.scale(focusedScale = 1f)) {
                    Text(stringResource(R.string.use_automatic_picture))
                }
            }
            // The big preview: the animal the remote is on, in your colour
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SeaFriendImage(
                    friend = SeaFriend.entries.getOrElse(preview) { SeaFriend.FISH },
                    colour = colour,
                    contentDescription = null,
                    modifier = Modifier.size(150.dp).clip(CircleShape),
                )
                Text(
                    text = name.orEmpty(),
                    style = MaterialTheme.typography.titleLarge,
                    fontFamily = com.github.damontecres.wholphin.ui.theme.GlowfinDisplayFont,
                )
            }
        }
    }
}

/**
 * Profile pictures picked in Benflix by profile id. This is this device's copy; ProfilePictureSync
 * also saves each pick to the person's Jellyfin account so it follows them to other devices.
 */
object AvatarChoices {
    private val choices = androidx.compose.runtime.mutableStateMapOf<UUID, Int>()
    private var prefs: android.content.SharedPreferences? = null

    @Synchronized
    fun init(context: android.content.Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences("benflix_avatars", android.content.Context.MODE_PRIVATE)
        prefs = p
        p.all.forEach { (key, value) ->
            val id = runCatching { UUID.fromString(key) }.getOrNull()
            if (id != null && value is Int) choices[id] = value
        }
    }

    fun get(id: UUID): Int? = choices[id]

    fun set(
        id: UUID,
        index: Int?,
    ) {
        if (index == null) {
            choices.remove(id)
            prefs?.edit()?.remove(id.toString())?.apply()
        } else {
            choices[id] = index
            prefs?.edit()?.putInt(id.toString(), index)?.apply()
        }
    }
}

@Composable
private fun UserIconCard(
    user: JellyfinUserAndImage,
    isCurrentUser: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    // The tile is their colour (the one they picked, or their picture's starter colour)
    AvatarChoices.init(androidx.compose.ui.platform.LocalContext.current)
    val userColor =
        avatarBackgroundFor(
            user.user.id,
            AvatarChoices.get(user.user.id)?.let { BenflixAvatars.getOrNull(it) } ?: benflixAvatarFor(user.user.id, user.user.name),
        )

    // Track image loading errors
    var imageError by remember { mutableStateOf(false) }

    // Card dimensions - circular card
    val cardSize = LocalProfileTileSize.current
    // A picture picked in Benflix wins, and shows right away when it changes
    AvatarChoices.init(androidx.compose.ui.platform.LocalContext.current)
    val chosen = AvatarChoices.get(user.user.id)?.let { BenflixAvatars.getOrNull(it) }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(cardSize * 0.1f + 10.dp),
    ) {
        // Rounded square tile with colored background, like a streaming service profile
        Surface(
            onClick = onClick,
            onLongClick = onLongClick,
            interactionSource = interactionSource,
            modifier =
                Modifier
                    .glowFocus(ProfileTileShape, spread = 14.dp)
                    .size(cardSize),
            shape = ClickableSurfaceDefaults.shape(shape = ProfileTileShape),
            colors =
                ClickableSurfaceDefaults.colors(
                    containerColor = userColor.copy(alpha = if (isCurrentUser) 1f else 0.85f),
                    focusedContainerColor = userColor,
                ),
            border =
                ClickableSurfaceDefaults.border(
                    focusedBorder =
                        Border(
                            border =
                                BorderStroke(
                                    width = 3.dp,
                                    color = com.github.damontecres.wholphin.ui.theme.BenflixColors.Focus,
                                ),
                            shape = ProfileTileShape,
                        ),
                ),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                if (chosen == null && user.imageUrl.isNotNullOrBlank() && !imageError) {
                    AsyncImage(
                        model = user.imageUrl,
                        contentDescription = user.user.name,
                        contentScale = ContentScale.Crop,
                        onError = { imageError = true },
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .clip(ProfileTileShape),
                    )
                } else {
                    // Benflix picture for profiles without their own image, on their colour
                    val res = chosen ?: benflixAvatarFor(user.user.id, user.user.name)
                    BenflixAvatarImage(
                        res = res,
                        background = avatarBackgroundFor(user.user.id, res),
                        contentDescription = user.user.name,
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .clip(ProfileTileShape),
                    )
                }
            }
        }

        // Username below the card
        Text(
            text = user.user.name ?: user.user.id.toString(),
            style = MaterialTheme.typography.bodyLarge.copy(shadow = ReadableTextShadow),
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier =
                Modifier
                    .width(cardSize)
                    .padding(horizontal = 4.dp),
        )
    }
}

@Composable
fun UserIconCardImage(
    id: UUID,
    name: String?,
    imageUrl: String?,
    modifier: Modifier = Modifier,
    alpha: Float = 1f,
) {
    var imageError by remember { mutableStateOf(false) }
    AvatarChoices.init(androidx.compose.ui.platform.LocalContext.current)
    // A picture picked in Benflix wins over the automatic one and the Jellyfin one
    val chosen = AvatarChoices.get(id)?.let { BenflixAvatars.getOrNull(it) }
    val userColor = avatarBackgroundFor(id, chosen ?: benflixAvatarFor(id, name)).let { it.copy(alpha = it.alpha * alpha) }
    Box(
        modifier =
            modifier
                // Always a round circle, even if the space it gets is narrower than it is tall
                .aspectRatio(1f)
                .background(
                    color = userColor,
                    shape = CircleShape,
                ),
        contentAlignment = Alignment.Center,
    ) {
        if (chosen == null && imageUrl.isNotNullOrBlank() && !imageError) {
            AsyncImage(
                model = imageUrl,
                contentDescription = name,
                contentScale = ContentScale.Crop,
                onError = { imageError = true },
                modifier =
                    Modifier
                        .fillMaxSize()
                        .clip(CircleShape),
            )
        } else {
            val res = chosen ?: benflixAvatarFor(id, name)
            BenflixAvatarImage(
                res = res,
                background = avatarBackgroundFor(id, res),
                contentDescription = name,
                alpha = alpha,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .clip(CircleShape),
            )
        }
    }
}

@PreviewTvSpec
@Composable
fun UserIconCardImagePreview() {
    WholphinTheme {
        UserIconCardImage(
            id = UUID.randomUUID(),
            name = "A smith",
            imageUrl = null,
            modifier = Modifier.size(24.dp),
        )
    }
}

/** Benflix redesign: round pictures (w-row) */
private val ProfileTileShape = CircleShape

/**
 * How big the Who's watching tiles are. The screen makes them smaller when space is short
 * (small TVs, holiday greetings) so the names under them always fit.
 */
val LocalProfileTileSize = androidx.compose.runtime.compositionLocalOf { Cards.serverUserCircle }

internal val BenflixAvatars =
    listOf(
        R.drawable.benflix_avatar_0,
        R.drawable.benflix_avatar_1,
        R.drawable.benflix_avatar_2,
        R.drawable.benflix_avatar_3,
        R.drawable.benflix_avatar_4,
        R.drawable.benflix_avatar_5,
        R.drawable.benflix_avatar_6,
        R.drawable.benflix_avatar_7,
    )

/**
 * Which of the 8 Glowfin colours ([SeaColours]) someone's picture is: the colour they picked (in setup or
 * Settings), otherwise each animal's own starter colour.
 */
fun seaColourIndex(
    id: UUID,
    res: Int,
): Int =
    com.github.damontecres.wholphin.ui.theme.ProfileColours
        .get(id)
        ?.themeIndex()
        ?: BenflixAvatars.indexOf(res).coerceIn(0, SeaColours.lastIndex)

/** The main colour of someone's picture (the tile around it uses it too) */
fun avatarBackgroundFor(
    id: UUID,
    res: Int,
): Color = SeaColours[seaColourIndex(id, res)].first

/**
 * A Glowfin profile picture: one of the Storybook sea friends ([SeaFriend]) in the colour [background]
 * stands for (from [avatarBackgroundFor]). Kids always get the rainbow starfish. Clip [modifier] to a circle.
 */
@Composable
fun BenflixAvatarImage(
    res: Int,
    background: Color,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    alpha: Float = 1f,
) {
    val kids = res == R.drawable.benflix_avatar_kids
    val friend = if (kids) SeaFriend.STARFISH else SeaFriend.entries.getOrElse(BenflixAvatars.indexOf(res)) { SeaFriend.FISH }
    val colour =
        if (kids) {
            SEA_KIDS
        } else {
            SeaColours.indexOfFirst { it.first == background.copy(alpha = 1f) }.coerceAtLeast(0)
        }
    SeaFriendImage(
        friend = friend,
        colour = colour,
        contentDescription = contentDescription,
        alpha = alpha,
        modifier = modifier,
    )
}

/**
 * The Benflix profile picture for a user without their own image. Each user always gets the same one,
 * and a profile named Kids gets the rainbow.
 */
fun benflixAvatarFor(
    id: UUID,
    name: String?,
): Int =
    if (name?.trim().equals("kids", ignoreCase = true)) {
        R.drawable.benflix_avatar_kids
    } else {
        BenflixAvatars[Math.floorMod(id.hashCode(), BenflixAvatars.size)]
    }

/**
 * The built-in Kids profile tile: rainbow picture and "Kids"
 */
@Composable
private fun KidsProfileCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    big: Boolean = false,
) {
    val cardSize = LocalProfileTileSize.current
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(cardSize * 0.1f + 10.dp),
    ) {
        Surface(
            onClick = onClick,
            modifier = Modifier.glowFocus(ProfileTileShape, spread = 14.dp).size(cardSize),
            shape = ClickableSurfaceDefaults.shape(shape = ProfileTileShape),
            border =
                ClickableSurfaceDefaults.border(
                    focusedBorder =
                        Border(
                            border =
                                BorderStroke(
                                    width = 3.dp,
                                    color = profileBorderColor(),
                                ),
                            shape = ProfileTileShape,
                        ),
                ),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        ) {
            SeaFriendImage(
                friend = SeaFriend.STARFISH,
                colour = SEA_KIDS,
                contentDescription = stringResource(R.string.kids_profile),
                modifier =
                    Modifier
                        .fillMaxSize()
                        .clip(ProfileTileShape)
                        .then(
                            if (big) {
                                Modifier.border(BorderStroke(4.dp, Color(0xFF3AA6B0)), ProfileTileShape)
                            } else {
                                Modifier
                            },
                        ),
            )
        }
        Text(
            text = if (big) "Big kids" else stringResource(R.string.kids_profile),
            style = MaterialTheme.typography.bodyLarge.copy(shadow = ReadableTextShadow),
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier =
                Modifier
                    .width(cardSize)
                    .padding(horizontal = 4.dp),
        )
    }
}

/** Benflix Guest tile: a plain grey tile with a question mark */
@Composable
private fun GuestProfileCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cardSize = LocalProfileTileSize.current
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(cardSize * 0.1f + 10.dp),
    ) {
        Surface(
            onClick = onClick,
            modifier = Modifier.glowFocus(ProfileTileShape, spread = 14.dp).size(cardSize),
            shape = ClickableSurfaceDefaults.shape(shape = ProfileTileShape),
            colors =
                ClickableSurfaceDefaults.colors(
                    containerColor = Color(0xFF2D3354),
                    focusedContainerColor = Color(0xFF5A6488),
                ),
            border =
                ClickableSurfaceDefaults.border(
                    focusedBorder =
                        Border(
                            border = BorderStroke(width = 3.dp, color = profileBorderColor()),
                            shape = ProfileTileShape,
                        ),
                ),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Text(text = "?", fontSize = 56.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
        Text(
            text = "Guest",
            style = MaterialTheme.typography.bodyLarge.copy(shadow = ReadableTextShadow),
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier =
                Modifier
                    .width(cardSize)
                    .padding(horizontal = 4.dp),
        )
    }
}

/** The outline around the chosen profile */
@Composable
private fun profileBorderColor(): Color = BenflixColors.Focus

/** A strong dark shadow so names stay easy to read on top of the moving backgrounds */
val ReadableTextShadow =
    androidx.compose.ui.graphics.Shadow(
        color =
            androidx.compose.ui.graphics.Color.Black
                .copy(alpha = .9f),
        offset =
            androidx.compose.ui.geometry
                .Offset(0f, 2f),
        blurRadius = 16f,
    )
