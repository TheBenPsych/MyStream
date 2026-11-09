package com.github.damontecres.wholphin.ui.main

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import com.github.damontecres.wholphin.services.Release
import com.github.damontecres.wholphin.util.Version
import org.jellyfin.sdk.model.api.DeviceInfoDto
import java.util.UUID

/** Benflix: one TV or phone with Benflix, and the version it had the last time it connected */
data class AppVersionRow(
    val key: String,
    val userId: UUID?,
    val userName: String,
    val deviceName: String,
    /** The version text the app sent, like "1.0.312" */
    val versionText: String?,
    val lastSeen: Long?,
    /** How many updates behind the newest build (0 = up to date, null = can't tell) */
    val behind: Int?,
    /** True when it's older than the oldest build the update history still lists */
    val behindAtLeast: Boolean = false,
) {
    val outOfDate get() = (behind ?: 0) > 0
}

/** Benflix: what the "Benflix versions" part of the People section shows */
data class AppVersions(
    val latest: String?,
    val rows: List<AppVersionRow>,
)

/** Benflix: updates behind at which a device counts as "way behind" (red instead of gold) */
private const val WAY_BEHIND = 10

/** Benflix: the message the "Message everyone out of date" button starts with */
const val UPDATE_MESSAGE =
    "There's a new Benflix update! Go to Settings > Help > Install update to get it."

/**
 * Benflix: turns Jellyfin's device list into one row per person per Benflix TV or phone.
 * Jellyfin remembers each device's app name, version and last connection even after it disconnects.
 * The web version is skipped because it's always the newest (Tower serves it).
 */
internal fun appVersionRows(
    devices: List<DeviceInfoDto>,
    latest: Release?,
): List<AppVersionRow> {
    // Every build the update history knows about, newest first
    val known =
        latest
            ?.changes
            ?.map { it.version }
            .orEmpty()
            .plus(listOfNotNull(latest?.version))
            .distinct()
    val oldestKnown = known.reduceOrNull { a, b -> if (a.isLessThan(b)) a else b }
    return devices
        .filter { d ->
            val id = d.id.orEmpty()
            val isBenflix = id.startsWith("benflix-") || d.appName.equals("Benflix", ignoreCase = true)
            isBenflix && !id.startsWith("benflix-web-")
        }.distinctBy { (it.id ?: "") + "|" + it.lastUserId }
        .map { d ->
            val theirs = Version.tryFromString(d.appVersion)
            val newest = latest?.version
            val behind =
                when {
                    theirs == null || newest == null -> null
                    !newest.isGreaterThan(theirs) -> 0
                    else -> known.count { it.isGreaterThan(theirs) }.coerceAtLeast(1)
                }
            AppVersionRow(
                key = "version-${d.id}-${d.lastUserId}",
                userId = d.lastUserId,
                userName = d.lastUserName?.takeIf { it.isNotBlank() } ?: "Someone",
                deviceName = d.customName?.takeIf { it.isNotBlank() } ?: d.name?.takeIf { it.isNotBlank() } ?: "A device",
                versionText = d.appVersion?.takeIf { it.isNotBlank() },
                lastSeen =
                    d.dateLastActivity
                        ?.atZone(java.time.ZoneId.systemDefault())
                        ?.toInstant()
                        ?.toEpochMilli(),
                behind = behind,
                behindAtLeast = theirs != null && oldestKnown != null && theirs.isLessThan(oldestKnown),
            )
        }.sortedWith(
            // Out of date first (furthest behind at the top), then the most recently used
            compareByDescending<AppVersionRow> { it.behind ?: 0 }.thenByDescending { it.lastSeen ?: 0L },
        )
}

/** Benflix: "today", "yesterday", "3 days ago" or a date */
private fun seenText(at: Long?): String {
    if (at == null || at <= 0) return "Last on: not known"
    val minutes = ((System.currentTimeMillis() - at) / 60_000).toInt()
    return "Last on " +
        when {
            minutes < 2 -> "just now"
            minutes < 60 -> "$minutes min ago"
            minutes < 120 -> "1 hour ago"
            minutes < 60 * 24 -> "${minutes / 60} hours ago"
            minutes < 60 * 48 -> "yesterday"
            else -> "${minutes / (60 * 24)} days ago"
        }
}

private fun behindText(row: AppVersionRow): String =
    when (val b = row.behind) {
        null -> "Can't tell if it's up to date"
        0 -> "Up to date"
        else -> (if (row.behindAtLeast) "$b+" else "$b") + " update" + (if (b == 1 && !row.behindAtLeast) "" else "s") + " behind"
    }

/**
 * Benflix: "Benflix versions" in the People section. Each person's TVs and phones with the version they had the
 * last time they connected, so Ben can message anyone who's out of date (or everyone, after a big change).
 */
fun LazyListScope.versionItems(
    versions: AppVersions?,
    failed: Boolean,
    onLoad: () -> Unit,
    onMessage: (ids: Set<UUID>, title: String, prefill: String) -> Unit,
    sectionTitle: @Composable (String) -> Unit,
) {
    item { sectionTitle("Benflix versions") }
    if (versions == null) {
        item {
            ServerActionRow(
                headline = if (failed) "Couldn't read the devices. Press to try again." else "Checking…",
                onClick = onLoad,
            )
        }
        return
    }
    val rows = versions.rows
    val old = rows.filter { it.outOfDate }
    item(key = "versions-summary") {
        ServerActionRow(
            headline =
                when {
                    rows.isEmpty() -> "No Benflix TVs or phones have connected yet"
                    old.isEmpty() -> "Everyone is up to date"
                    else -> "${old.size} of ${rows.size} Benflix device" + (if (rows.size == 1) " is" else "s are") + " out of date"
                },
            supporting =
                (versions.latest?.let { "The newest version is $it. " } ?: "Couldn't check the newest version right now. ") +
                    "Each device shows the version it had the last time it connected.",
            leading = { Dot(if (old.isEmpty()) Light.GOOD else Light.WARN) },
            onClick = onLoad,
            actions =
                buildList {
                    val ids = old.mapNotNull { it.userId }.toSet()
                    if (ids.isNotEmpty()) {
                        add("Message everyone out of date" to { onMessage(ids, "Message everyone out of date", UPDATE_MESSAGE) })
                    }
                    add("Check again" to onLoad)
                },
        )
    }
    items(rows, key = { it.key }) { row ->
        val light =
            when {
                row.behind == null -> Light.UNKNOWN
                row.behind == 0 -> Light.GOOD
                row.behind >= WAY_BEHIND || row.behindAtLeast -> Light.BAD
                else -> Light.WARN
            }
        ServerActionRow(
            headline = "${row.userName} · ${row.deviceName}",
            supporting =
                listOf(
                    "Version " + (row.versionText ?: "not known"),
                    behindText(row),
                    seenText(row.lastSeen),
                ).joinToString(" · "),
            leading = { Dot(light) },
            onClick = {
                row.userId?.let { id ->
                    onMessage(setOf(id), "Message to ${row.userName}", if (row.outOfDate) UPDATE_MESSAGE else "")
                }
            },
            actions =
                if (row.userId == null) {
                    emptyList()
                } else {
                    listOf(
                        "Message" to {
                            onMessage(setOf(row.userId), "Message to ${row.userName}", if (row.outOfDate) UPDATE_MESSAGE else "")
                        },
                    )
                },
        )
    }
}
