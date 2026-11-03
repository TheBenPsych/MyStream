package com.github.damontecres.wholphin.games

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import java.io.File
import java.util.Locale

/**
 * Looks at this TV or phone's chip, works out roughly how fast it is, and says which consoles run well.
 * The score is "best CPU core speed": how strong one core of that design is, times its top GHz.
 */
class DevicePower private constructor(
    val chip: String,
    val cpuName: String,
    val cores: Int,
    val maxGhz: Double,
    val ramGb: Double,
    val is64Bit: Boolean,
    val score: Double,
) {
    enum class Fit {
        GOOD,
        MAY_BE_SLOW,
        TOO_MUCH,
    }

    fun fit(need: PowerNeed): Fit =
        when (need) {
            PowerNeed.LIGHT -> {
                Fit.GOOD
            }

            PowerNeed.MEDIUM -> {
                when {
                    score >= 1.7 -> Fit.GOOD
                    score >= 1.2 -> Fit.MAY_BE_SLOW
                    else -> Fit.TOO_MUCH
                }
            }

            PowerNeed.HEAVY -> {
                when {
                    score >= 4.5 && ramGb >= 2.5 -> Fit.GOOD
                    score >= 2.4 -> Fit.MAY_BE_SLOW
                    else -> Fit.TOO_MUCH
                }
            }
        }

    /** "MediaTek MT8696 · 4 cores up to 2.0 GHz (Cortex-A55) · 2 GB memory" */
    val summary: String
        get() {
            val parts = mutableListOf<String>()
            if (chip.isNotBlank()) parts.add(chip)
            val cpu =
                buildString {
                    append("$cores cores")
                    if (maxGhz > 0) append(" up to %.1f GHz".format(Locale.US, maxGhz))
                    if (cpuName.isNotBlank()) append(" ($cpuName)")
                }
            parts.add(cpu)
            if (ramGb > 0) parts.add("%.0f GB memory".format(Locale.US, ramGb.coerceAtLeast(1.0)))
            if (!is64Bit) parts.add("32-bit")
            return parts.joinToString(" · ")
        }

    companion object {
        @Volatile
        private var cached: DevicePower? = null

        /** ARM's CPU part numbers (from /proc/cpuinfo) to a name and a rough per-GHz strength */
        private val CPU_PARTS =
            mapOf(
                0xd03 to ("Cortex-A53" to 1.0),
                0xd04 to ("Cortex-A35" to 0.9),
                0xd05 to ("Cortex-A55" to 1.25),
                0xd07 to ("Cortex-A57" to 2.4),
                0xd08 to ("Cortex-A72" to 2.7),
                0xd09 to ("Cortex-A73" to 2.9),
                0xd0a to ("Cortex-A75" to 3.4),
                0xd0b to ("Cortex-A76" to 4.0),
                0xd0d to ("Cortex-A77" to 4.5),
                0xd41 to ("Cortex-A78" to 4.9),
                0xd44 to ("Cortex-X1" to 5.6),
                0xd46 to ("Cortex-A510" to 1.4),
                0xd47 to ("Cortex-A710" to 5.0),
                0xd48 to ("Cortex-X2" to 6.2),
                0xd4d to ("Cortex-A715" to 5.4),
                0xd4e to ("Cortex-X3" to 6.8),
                0xd80 to ("Cortex-A520" to 1.5),
                0xd81 to ("Cortex-A720" to 5.8),
                0xd82 to ("Cortex-X4" to 7.6),
                0xd85 to ("Cortex-X925" to 8.5),
                0xd87 to ("Cortex-A725" to 6.2),
            )

        fun get(context: Context): DevicePower = cached ?: detect(context).also { cached = it }

        private fun detect(context: Context): DevicePower {
            val chip =
                buildString {
                    if (Build.VERSION.SDK_INT >= 31) {
                        val maker = Build.SOC_MANUFACTURER.takeUnless { it.isBlank() || it == Build.UNKNOWN }
                        val model = Build.SOC_MODEL.takeUnless { it.isBlank() || it == Build.UNKNOWN }
                        if (model != null) append(listOfNotNull(maker, model).joinToString(" "))
                    }
                    if (isEmpty()) append(Build.HARDWARE.orEmpty().uppercase(Locale.US))
                }
            val cores = Runtime.getRuntime().availableProcessors()
            // Fastest core: highest max frequency, and the part number of that core
            var best = 0.0
            var bestGhz = 0.0
            var bestName = ""
            val parts = cpuParts()
            for (i in 0 until cores) {
                val khz =
                    try {
                        File("/sys/devices/system/cpu/cpu$i/cpufreq/cpuinfo_max_freq").readText().trim().toLong()
                    } catch (_: Exception) {
                        0L
                    }
                val ghz = khz / 1_000_000.0
                val (name, strength) = CPU_PARTS[parts.getOrNull(i) ?: parts.lastOrNull() ?: -1] ?: ("" to 1.3)
                val s = strength * (if (ghz > 0) ghz else 1.5)
                if (s > best) {
                    best = s
                    bestName = name
                }
                if (ghz > bestGhz) bestGhz = ghz
            }
            if (best == 0.0) best = 1.3 * 1.5
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val mem = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
            val is64 = android.os.Process.is64Bit()
            // 32-bit Android (like the Chromecast with Google TV) runs emulators a bit slower
            val score = if (is64) best else best * 0.85
            return DevicePower(
                chip = chip,
                cpuName = bestName,
                cores = cores,
                maxGhz = bestGhz,
                ramGb = mem.totalMem / 1_073_741_824.0,
                is64Bit = is64,
                score = score,
            )
        }

        /** The "CPU part" of each core, in order */
        private fun cpuParts(): List<Int> =
            try {
                File("/proc/cpuinfo")
                    .readLines()
                    .filter { it.startsWith("CPU part") }
                    .mapNotNull {
                        it
                            .substringAfter(':')
                            .trim()
                            .removePrefix("0x")
                            .toIntOrNull(16)
                    }
            } catch (_: Exception) {
                emptyList()
            }

        /** The emulator files to download for this device's processor type */
        fun coreAbi(): String {
            val abis = if (android.os.Process.is64Bit()) Build.SUPPORTED_64_BIT_ABIS else Build.SUPPORTED_32_BIT_ABIS
            return abis.firstOrNull() ?: Build.SUPPORTED_ABIS.first()
        }
    }
}
