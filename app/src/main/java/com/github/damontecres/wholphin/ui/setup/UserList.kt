package com.github.damontecres.wholphin.ui.setup

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.model.JellyfinUser
import com.github.damontecres.wholphin.services.holidayFor
import com.github.damontecres.wholphin.ui.Cards
import com.github.damontecres.wholphin.ui.PreviewTvSpec
import com.github.damontecres.wholphin.ui.components.BenflixIcon
import com.github.damontecres.wholphin.ui.components.DialogItem
import com.github.damontecres.wholphin.ui.components.DialogPopup
import com.github.damontecres.wholphin.ui.isNotNullOrBlank
import com.github.damontecres.wholphin.ui.theme.BenflixColors
import com.github.damontecres.wholphin.ui.theme.BenflixType
import com.github.damontecres.wholphin.ui.theme.WholphinTheme
import com.github.damontecres.wholphin.ui.theme.toColor
import com.github.damontecres.wholphin.ui.toServerString
import com.github.damontecres.wholphin.ui.tryRequestFocus
import java.time.LocalDate
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
                        KidsProfileCard(onClick = { onKids.invoke(grownUp) })
                    }
                    if (showBigKids) {
                        item {
                            KidsProfileCard(onClick = { onBigKids.invoke(grownUp) }, big = true)
                        }
                    }
                    if (showGuest) {
                        item {
                            GuestProfileCard(onClick = { onGuest.invoke(grownUp) })
                        }
                    }
                }
            }
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
                    add(
                        DialogItem(
                            stringResource(R.string.switch_user),
                            R.string.fa_arrow_left_arrow_right,
                        ) {
                            onSwitchUser.invoke(user.user)
                        },
                    )
                    add(
                        DialogItem(
                            stringResource(R.string.change_picture),
                            R.string.fa_user,
                        ) {
                            pickPictureFor = user
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

/** A quiet text link on Who's watching that lights up when focused */
@Composable
private fun WhoLink(
    text: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
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
 * Pick a Benflix profile picture. [onPick] saves it (null = automatic).
 */
@Composable
private fun AvatarPickerDialog(
    userId: UUID,
    name: String?,
    onPick: (Int?) -> Unit,
    onDismissRequest: () -> Unit,
) {
    // Make sure the pick is saved on the device, even if no picture has been drawn yet
    AvatarChoices.init(androidx.compose.ui.platform.LocalContext.current)
    val current = AvatarChoices.get(userId)
    com.github.damontecres.wholphin.ui.touch.Dialog(onDismissRequest = onDismissRequest) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier =
                Modifier
                    .background(Color(0xFF181818), RoundedCornerShape(10.dp))
                    .padding(24.dp),
        ) {
            Text(
                text = stringResource(R.string.choose_picture_for, name.orEmpty()),
                style = MaterialTheme.typography.titleLarge,
            )
            val firstFocus = remember { FocusRequester() }
            LaunchedEffect(Unit) { firstFocus.tryRequestFocus() }
            BenflixAvatars.chunked(4).forEachIndexed { rowIndex, row ->
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    row.forEachIndexed { col, res ->
                        val index = rowIndex * 4 + col
                        Surface(
                            onClick = {
                                onPick.invoke(index)
                                onDismissRequest.invoke()
                            },
                            shape = ClickableSurfaceDefaults.shape(CircleShape),
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
                                            androidx.compose.foundation.BorderStroke(3.dp, BenflixColors.Text),
                                        ),
                                ),
                            modifier =
                                Modifier
                                    .size(72.dp)
                                    .then(if (index == 0) Modifier.focusRequester(firstFocus) else Modifier),
                            scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                        ) {
                            // Shown on their colour, so they see exactly what they will get
                            BenflixAvatarImage(
                                res = res,
                                background = avatarBackgroundFor(userId, res),
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize().clip(CircleShape),
                            )
                        }
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
                                    color = com.github.damontecres.wholphin.ui.theme.BenflixColors.Text,
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
 * The colour behind each picture for someone who has not picked a colour yet (the same colours
 * the pictures used to come in, so nobody's tile changes until they pick one).
 */
private val AvatarStarterColours =
    listOf(
        Color(0xFFE50914),
        Color(0xFF0071EB),
        Color(0xFFE8A92B),
        Color(0xFF1EAA5A),
        Color(0xFF8C3CDC),
        Color(0xFFE5488F),
        Color(0xFF2A2A30),
        Color(0xFF00A0AA),
    )

/**
 * The colour behind someone's picture: the colour they picked ("Its background colour" in setup,
 * or their colour in Settings), otherwise the picture's starter colour.
 */
fun avatarBackgroundFor(
    id: UUID,
    res: Int,
): Color =
    com.github.damontecres.wholphin.ui.theme.ProfileColours
        .get(id)
        ?.toColor()
        ?: AvatarStarterColours.getOrElse(BenflixAvatars.indexOf(res)) { AvatarStarterColours[0] }

/**
 * White or near-black, whichever is easier to see on [background]. White wins unless it is too
 * faint (light colours like gold, green and grey get a dark character instead).
 */
fun avatarInkFor(background: Color): Color = if (1.05f / (background.luminance() + 0.05f) >= 3f) Color.White else Color(0xFF141418)

/**
 * A Benflix profile picture: the character drawn on [background], with a soft shade at the bottom.
 * The Kids rainbow keeps its own colours. Clip [modifier] to the shape you want.
 */
@Composable
fun BenflixAvatarImage(
    res: Int,
    background: Color,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    alpha: Float = 1f,
) {
    if (res == R.drawable.benflix_avatar_kids) {
        Image(
            painter = painterResource(res),
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            alpha = alpha,
            modifier = modifier,
        )
        return
    }
    Box(
        modifier =
            modifier
                .background(background.copy(alpha = background.alpha * alpha))
                .background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = .16f * alpha)),
                    ),
                ),
    ) {
        Image(
            painter = painterResource(res),
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            alpha = alpha,
            colorFilter =
                androidx.compose.ui.graphics.ColorFilter
                    .tint(avatarInkFor(background)),
            modifier = Modifier.fillMaxSize(),
        )
    }
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
            modifier = Modifier.size(cardSize),
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
            Image(
                painter = painterResource(R.drawable.benflix_avatar_kids),
                contentDescription = stringResource(R.string.kids_profile),
                contentScale = ContentScale.Crop,
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
            modifier = Modifier.size(cardSize),
            shape = ClickableSurfaceDefaults.shape(shape = ProfileTileShape),
            colors =
                ClickableSurfaceDefaults.colors(
                    containerColor = Color(0xFF55555C),
                    focusedContainerColor = Color(0xFF6A6A72),
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

/**
 * The outline around the chosen profile: white normally, the holiday's color on a holiday
 */
@Composable
private fun profileBorderColor(): Color {
    val holiday = remember { holidayFor(LocalDate.now()) }
    return holiday?.let { Color(it.color) } ?: MaterialTheme.colorScheme.onSurface
}

/** How many see-through layers make up the profile glow */
private const val GLOW_LAYERS = 3

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
