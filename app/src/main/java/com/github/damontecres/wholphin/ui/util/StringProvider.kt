package com.github.damontecres.wholphin.ui.util

import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Flexible way to provide a string for the UI
 */
@Serializable
sealed interface StringProvider {
    @Composable
    @NonRestartableComposable
    fun getString(): String
}

/**
 * Provides a string literal
 */
@Serializable
data class StringStringProvider(
    val str: String,
) : StringProvider {
    @Composable
    @NonRestartableComposable
    override fun getString(): String = str
}

/**
 * Provide an empty string literal
 */
@Serializable
data object EmptyStringProvider : StringProvider {
    @Composable
    @NonRestartableComposable
    override fun getString(): String = ""
}

/**
 * Provides a string resource
 */
@Serializable
data class ResStringProvider(
    @Serializable(with = StringResNameSerializer::class)
    @param:StringRes val stringResId: Int,
) : StringProvider {
    @Composable
    @NonRestartableComposable
    override fun getString() = safeStringResource(stringResId)
}

/**
 * Provides a string resource with format arguments
 */
@Serializable
data class ResArgStringProvider(
    @Serializable(with = StringResNameSerializer::class)
    @param:StringRes val stringResId: Int,
    val args: Array<String>,
) : StringProvider {
    constructor(stringResId: Int, arg: String) : this(stringResId, arrayOf(arg))

    @Composable
    @NonRestartableComposable
    override fun getString() = safeStringResource(stringResId, *args)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ResArgStringProvider) return false

        if (stringResId != other.stringResId) return false
        if (!args.contentEquals(other.args)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = stringResId
        result = 31 * result + args.contentHashCode()
        return result
    }
}

/**
 * Provides a string resource with another [StringProvider] format argument
 */
@Serializable
data class ResProviderStringProvider(
    @Serializable(with = StringResNameSerializer::class)
    @param:StringRes val stringResId: Int,
    val argProvider: StringProvider,
) : StringProvider {
    @Composable
    @NonRestartableComposable
    override fun getString(): String = safeStringResource(stringResId, argProvider.getString())
}

@Serializable
data class ConcatStringProvider(
    val separator: String,
    val providers: List<StringProvider>,
) : StringProvider {
    @Composable
    @NonRestartableComposable
    override fun getString(): String = providers.map { it.getString() }.joinToString(separator)
}

/**
 * Benflix: like stringResource, but a text that no longer exists shows as blank instead of crashing.
 * Resource numbers change with every app build, so a number saved by an older Benflix (in the
 * saved Home or the screen history Android keeps) can point at nothing after an update. Older
 * TVs crashed on that ("String resource ID #0x... not found") the first time they opened after updating.
 */
@Composable
@NonRestartableComposable
fun safeStringResource(
    @StringRes id: Int,
    vararg args: Any,
): String {
    LocalConfiguration.current // read again when the language changes, like stringResource
    val resources = LocalContext.current.resources
    return try {
        if (args.isEmpty()) resources.getString(id) else resources.getString(id, *args)
    } catch (ex: Resources.NotFoundException) {
        ""
    } catch (ex: java.util.IllegalFormatException) {
        ""
    }
}

/**
 * Benflix: saves a string resource by its name ("home_continue_watching"), not its number, because
 * the numbers change with every build. Without this, a saved Home from the last version showed
 * the wrong row names, or crashed older TVs, after an update.
 */
object StringResNameSerializer : KSerializer<Int> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("benflix.StringResName", PrimitiveKind.STRING)

    private val resources: Resources?
        get() =
            try {
                com.github.damontecres.wholphin.WholphinApplication.instance.resources
            } catch (ex: Throwable) {
                null
            }

    override fun serialize(
        encoder: Encoder,
        value: Int,
    ) {
        val name =
            try {
                resources?.getResourceEntryName(value)
            } catch (ex: Resources.NotFoundException) {
                null
            }
        encoder.encodeString(name ?: "")
    }

    override fun deserialize(decoder: Decoder): Int {
        val name = decoder.decodeString()
        if (name.isEmpty()) return 0
        val app =
            try {
                com.github.damontecres.wholphin.WholphinApplication.instance
            } catch (ex: Throwable) {
                return 0
            }
        @Suppress("DiscouragedApi")
        return app.resources.getIdentifier(name, "string", app.packageName)
    }
}
