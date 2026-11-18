package com.github.damontecres.wholphin.ui.components

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.core.graphics.drawable.toBitmap
import androidx.core.net.toUri
import androidx.tv.material3.Text
import timber.log.Timber

/**
 * Benflix: which app plays YouTube trailers. YouTube's web player (the pop-up inside Benflix) stutters
 * and buffers on slower TVs, so people can send trailers to a real YouTube app instead (SmartTube,
 * the YouTube app, NewPipe...). The pick is saved on this device only, because each TV or phone has
 * its own apps. Nothing picked yet: Benflix asks the first time a YouTube trailer is played.
 * Downloaded (Trailarr) trailers always play in Benflix's own player.
 */
object TrailerApps {
    /** Saved value meaning "play in the pop-up inside Benflix" */
    const val INSIDE = "benflix"

    /** Saved value meaning "ask each time" (also: nothing picked yet) */
    const val ASK = ""

    private const val PREFS = "benflix_trailers"
    private const val KEY = "app"

    data class App(
        val name: String,
        val packageName: String,
        val icon: ImageBitmap?,
    )

    // Apps people often use for YouTube on TVs and phones. Some don't say they open YouTube links,
    // so they are looked for by name too (the manifest lists them so Android lets Benflix see them).
    private val KNOWN =
        listOf(
            "com.teamsmart.videomanager.tv", // SmartTube
            "com.liskovsoft.smarttubetv.beta", // SmartTube beta
            "com.liskovsoft.smarttubetv", // SmartTube (old)
            "com.google.android.youtube.tv", // YouTube for Android TV / Google TV
            "com.amazon.firetv.youtube", // YouTube on Fire TV
            "com.google.android.youtube", // YouTube (phone)
            "app.revanced.android.youtube", // YouTube ReVanced
            "org.schabi.newpipe", // NewPipe
        )

    fun saved(context: Context): String =
        try {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, ASK) ?: ASK
        } catch (_: Exception) {
            ASK
        }

    fun save(
        context: Context,
        value: String,
    ) {
        try {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putString(KEY, value) }
        } catch (ex: Exception) {
            Timber.w(ex, "Couldn't save the trailer app")
        }
    }

    private fun isSmartTube(app: App) = app.name.contains("SmartTube", true) || app.packageName.contains("smarttube")

    /** The apps on this device that can play a YouTube video (web browsers left out), SmartTube first */
    fun installed(context: Context): List<App> {
        val pm = context.packageManager
        return try {
            val youtube = Intent(Intent.ACTION_VIEW, "https://www.youtube.com/watch?v=aqz-KE-bpKQ".toUri())
            val anyPage = Intent(Intent.ACTION_VIEW, "https://example.com/".toUri())
            val browsers =
                pm.queryIntentActivities(anyPage, PackageManager.MATCH_ALL).map { it.activityInfo.packageName }.toSet()
            val fromLinks =
                pm
                    .queryIntentActivities(youtube, PackageManager.MATCH_ALL)
                    .map { it.activityInfo.packageName }
                    .filter { it !in browsers || it in KNOWN }
            val byName = KNOWN.filter { pm.getLaunchIntentForPackage(it) != null || leanbackLaunch(pm, it) }
            (fromLinks + byName)
                .distinct()
                .filter { it != context.packageName }
                .mapNotNull { pkg -> appFor(pm, pkg) }
                .sortedWith(
                    compareBy(
                        { !isSmartTube(it) },
                        { KNOWN.indexOf(it.packageName).let { i -> if (i < 0) 99 else i } },
                        { it.name },
                    ),
                )
        } catch (ex: Exception) {
            Timber.w(ex, "Couldn't list YouTube apps")
            emptyList()
        }
    }

    private fun leanbackLaunch(
        pm: PackageManager,
        pkg: String,
    ): Boolean =
        try {
            pm.getLeanbackLaunchIntentForPackage(pkg) != null
        } catch (_: Exception) {
            false
        }

    private fun appFor(
        pm: PackageManager,
        pkg: String,
    ): App? =
        try {
            val info = pm.getApplicationInfo(pkg, 0)
            val icon =
                try {
                    pm.getApplicationIcon(info).toBitmap(96, 96).asImageBitmap()
                } catch (_: Exception) {
                    null
                }
            App(name = pm.getApplicationLabel(info).toString(), packageName = pkg, icon = icon)
        } catch (_: Exception) {
            null
        }

    /** Plays the YouTube link in [packageName]. False when that app isn't there or won't take it */
    fun open(
        context: Context,
        packageName: String,
        url: String,
        videoId: String,
    ): Boolean {
        // A plain watch link is the one every YouTube app understands
        val link = "https://www.youtube.com/watch?v=$videoId"
        val tries =
            listOf(
                Intent(Intent.ACTION_VIEW, link.toUri()).setPackage(packageName),
                Intent(Intent.ACTION_VIEW, url.toUri()).setPackage(packageName),
                Intent(Intent.ACTION_VIEW, "vnd.youtube:$videoId".toUri()).setPackage(packageName),
            )
        for (intent in tries) {
            try {
                context.startActivity(intent)
                return true
            } catch (_: Exception) {
            }
        }
        Timber.w("$packageName couldn't open $url")
        return false
    }
}

/** What the "Play YouTube trailers in" choices look like, shared by the first-time question and Settings */
@Composable
private fun AppLabel(
    name: String,
    icon: ImageBitmap?,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            Image(bitmap = icon, contentDescription = null, modifier = Modifier.size(32.dp))
        } else {
            androidx.compose.foundation.layout
                .Box(Modifier.width(32.dp))
        }
        Text(name)
    }
}

/**
 * Asked the first time a YouTube trailer plays (and again if the picked app was removed). The pick is
 * remembered; Settings > Watching > YouTube trailers changes it.
 */
@Composable
internal fun TrailerAppQuestion(
    request: YouTubeTrailer.Request,
    onDone: () -> Unit,
) {
    val context = LocalContext.current
    val apps = remember(request) { TrailerApps.installed(context) }
    val items =
        buildList<DialogItemEntry> {
            apps.forEach { app ->
                add(
                    DialogItem(
                        headlineContent = { AppLabel(app.name, app.icon) },
                        supportingContent = { Text(if (app == apps.first()) "Smoothest on most TVs" else "Plays in this app") },
                        onClick = {
                            TrailerApps.save(context, app.packageName)
                            onDone()
                            if (!TrailerApps.open(context, app.packageName, request.url, request.videoId)) {
                                YouTubeTrailer.current = request
                            }
                        },
                    ),
                )
            }
            add(
                DialogItem(
                    headlineContent = { AppLabel("Inside Benflix", null) },
                    supportingContent = { Text("A pop-up player. Can stutter on slower TVs") },
                    onClick = {
                        TrailerApps.save(context, TrailerApps.INSIDE)
                        onDone()
                        YouTubeTrailer.current = request
                    },
                ),
            )
        }
    DialogPopup(
        showDialog = true,
        title = "Play YouTube trailers in…",
        dialogItems = items,
        onDismissRequest = onDone,
        dismissOnClick = false,
        waitToLoad = false,
        header = {
            Text("Benflix remembers your pick. Change it any time in Settings > Watching > YouTube trailers.")
        },
    )
}

/** Settings > Watching: "YouTube trailers" — Ask each time, Inside Benflix, or one of this device's YouTube apps */
@Composable
fun TrailerAppSetting(
    title: String,
    modifier: Modifier = Modifier,
    interactionSource: androidx.compose.foundation.interaction.MutableInteractionSource,
) {
    val context = LocalContext.current
    val apps = remember { TrailerApps.installed(context) }
    var saved by remember { mutableStateOf(TrailerApps.saved(context)) }
    val choices =
        remember(apps) {
            listOf(
                TrailerApps.App("Ask me the first time", TrailerApps.ASK, null),
                TrailerApps.App("Inside Benflix (pop-up)", TrailerApps.INSIDE, null),
            ) + apps
        }
    val selected = choices.indexOfFirst { it.packageName == saved }.takeIf { it >= 0 } ?: 0
    com.github.damontecres.wholphin.ui.preferences.ChoicePreference(
        title = title,
        summary =
            if (apps.isEmpty() && saved != TrailerApps.INSIDE) {
                "No YouTube app on this device, so trailers play inside Benflix"
            } else {
                choices[selected].name
            },
        possibleValues = choices,
        selectedIndex = selected,
        onValueChange = { index ->
            val value = choices.getOrNull(index)?.packageName ?: TrailerApps.ASK
            TrailerApps.save(context, value)
            saved = value
        },
        valueDisplay = { _, item -> AppLabel(item.name, item.icon) },
        interactionSource = interactionSource,
        modifier = modifier,
    )
}
