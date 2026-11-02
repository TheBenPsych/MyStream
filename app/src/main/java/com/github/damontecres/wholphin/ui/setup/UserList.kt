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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
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
import androidx.compose.ui.graphics.Color
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
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.model.JellyfinUser
import com.github.damontecres.wholphin.services.holidayFor
import com.github.damontecres.wholphin.ui.Cards
import com.github.damontecres.wholphin.ui.FontAwesome
import com.github.damontecres.wholphin.ui.PreviewTvSpec
import com.github.damontecres.wholphin.ui.components.DialogItem
import com.github.damontecres.wholphin.ui.components.DialogPopup
import com.github.damontecres.wholphin.ui.isNotNullOrBlank
import com.github.damontecres.wholphin.ui.theme.WholphinTheme
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
                contentPadding = PaddingValues(horizontal = 48.dp, vertical = 16.dp), // Increased padding to accommodate 20% scale
                modifier =
                    Modifier
                        .wrapContentWidth()
                        .focusRestorer(firstFocusRequester)
                        .focusRequester(focusRequester),
            ) {
                itemsIndexed(users) { index, user ->
                    UserIconCard(
                        user = user,
                        isCurrentUser = user.user.id == currentUser?.id,
                        onClick = { onSwitchUser.invoke(user.user) },
                        onLongClick = { showDeleteDialog = user },
                        modifier = if (index == 0) Modifier.focusRequester(firstFocusRequester) else Modifier,
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
                }
                // Add User card - always rightmost
                item {
                    AddUserCard(
                        onClick = { onAddUser.invoke() },
                    )
                }
            }
        }

        // Switch servers button below user list - centered
        Row(
            horizontalArrangement = Arrangement.Center,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
        ) {
            Button(
                onClick = { onSwitchServer.invoke() },
                modifier = Modifier.width(200.dp), // Fixed width for consistency
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.fa_arrow_left_arrow_right),
                        fontFamily = FontAwesome,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                    Text(
                        text = stringResource(R.string.switch_servers),
                        textAlign = TextAlign.Center,
                    )
                }
            }
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
                                Icons.Default.Delete,
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
            onDismissRequest = { pickPictureFor = null },
        )
    }
}

/**
 * Pick a Benflix profile picture. Saved on this device.
 */
@Composable
private fun AvatarPickerDialog(
    userId: UUID,
    name: String?,
    onDismissRequest: () -> Unit,
) {
    // Make sure the pick is saved on the device, even if no picture has been drawn yet
    AvatarChoices.init(androidx.compose.ui.platform.LocalContext.current)
    val current = AvatarChoices.get(userId)
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismissRequest) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier =
                Modifier
                    .background(Color(0xFF181818), RoundedCornerShape(12.dp))
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
                                AvatarChoices.set(userId, index)
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
                                            androidx.compose.foundation.BorderStroke(3.dp, Color.White),
                                        ),
                                ),
                            modifier =
                                Modifier
                                    .size(72.dp)
                                    .then(if (index == 0) Modifier.focusRequester(firstFocus) else Modifier),
                        ) {
                            Image(
                                painter = painterResource(res),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize().clip(CircleShape),
                            )
                        }
                    }
                }
            }
            Button(onClick = {
                AvatarChoices.set(userId, null)
                onDismissRequest.invoke()
            }) {
                Text(stringResource(R.string.use_automatic_picture))
            }
        }
    }
}

/**
 * Profile pictures picked in Benflix, kept on this device by profile id
 */
object AvatarChoices {
    private val choices = androidx.compose.runtime.mutableStateMapOf<UUID, Int>()
    private var prefs: android.content.SharedPreferences? = null

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
    // Generate unique color for this user
    val userColor = rememberIdColor(user.user.id)

    // Track image loading errors
    var imageError by remember { mutableStateOf(false) }

    // Card dimensions - circular card
    val cardSize = Cards.serverUserCircle

    // A picture picked in Benflix wins, and shows right away when it changes
    AvatarChoices.init(androidx.compose.ui.platform.LocalContext.current)
    val chosen = AvatarChoices.get(user.user.id)?.let { BenflixAvatars.getOrNull(it) }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        // Rounded square tile with colored background, like a streaming service profile
        Surface(
            onClick = onClick,
            onLongClick = onLongClick,
            interactionSource = interactionSource,
            modifier = Modifier.size(cardSize),
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
                                    color = profileBorderColor(),
                                ),
                            shape = ProfileTileShape,
                        ),
                ),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.2f),
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
                    // Benflix picture for profiles without their own image
                    Image(
                        painter = painterResource(chosen ?: benflixAvatarFor(user.user.id, user.user.name)),
                        contentDescription = user.user.name,
                        contentScale = ContentScale.Crop,
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
    val userColor = rememberIdColor(id, alpha)
    AvatarChoices.init(androidx.compose.ui.platform.LocalContext.current)
    // A picture picked in Benflix wins over the automatic one and the Jellyfin one
    val chosen = AvatarChoices.get(id)?.let { BenflixAvatars.getOrNull(it) }
    Box(
        modifier =
            modifier.background(
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
            Image(
                painter = painterResource(chosen ?: benflixAvatarFor(id, name)),
                contentDescription = name,
                contentScale = ContentScale.Crop,
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

/**
 * Add User card component - displays a + icon in a circle
 */
@Composable
private fun AddUserCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }

    // Use a neutral gray color for the add user card
    val addUserColor = MaterialTheme.colorScheme.surfaceVariant

    // Card dimensions - circular card (same as user cards)
    val cardSize = Cards.height2x3 * 0.75f // ~120dp

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp), // Increased to accommodate 20% scale
    ) {
        // Rounded square tile with colored background, like a streaming service profile
        Surface(
            onClick = onClick,
            interactionSource = interactionSource,
            modifier = Modifier.size(cardSize),
            shape = ClickableSurfaceDefaults.shape(shape = ProfileTileShape),
            colors =
                ClickableSurfaceDefaults.colors(
                    containerColor = addUserColor.copy(alpha = 0.4f),
                    focusedContainerColor = addUserColor.copy(alpha = 0.6f),
                ),
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
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.2f),
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(R.string.add_user),
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(cardSize * 0.4f), // Size of the + icon
                )
            }
        }

        // "Add User" text below the card
        Text(
            text = stringResource(R.string.add_user),
            style =
                MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    shadow = ReadableTextShadow,
                ),
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

private val ProfileTileShape = RoundedCornerShape(10.dp)

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
    val cardSize = Cards.serverUserCircle
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp),
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
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.2f),
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

/**
 * The outline around the chosen profile: white normally, the holiday's color on a holiday
 */
@Composable
private fun profileBorderColor(): Color {
    val holiday = remember { holidayFor(LocalDate.now()) }
    return holiday?.let { Color(it.color) } ?: MaterialTheme.colorScheme.onSurface
}

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
