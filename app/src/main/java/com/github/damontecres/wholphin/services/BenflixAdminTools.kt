package com.github.damontecres.wholphin.services

import android.content.Context
import androidx.core.content.edit
import com.github.damontecres.wholphin.util.WholphinDispatchers
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.userApi
import org.jellyfin.sdk.model.UUID
import org.jellyfin.sdk.model.api.CreateUserByName
import org.jellyfin.sdk.model.api.UpdateUserPassword
import org.jellyfin.sdk.model.api.UserDto
import timber.log.Timber
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

/** A short note Ben writes on the Server page that shows at the top of everyone's Home */
data class BenflixNote(
    val id: String,
    val text: String,
)

/** Which note: the everyday note on Home, or the welcome note new people see once */
enum class NoteKind(
    val key: String,
) {
    NOTE("benflix.note"),
    WELCOME("benflix.welcome"),
}

/**
 * Benflix "Notes from Ben": the note is saved into every person's Jellyfin display settings
 * (only an admin is allowed to do that), so each person's Benflix can read it back.
 * Each device remembers which note was dismissed.
 */
@Singleton
class BenflixNotes
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val api: ApiClient,
        private val displayPreferencesService: DisplayPreferencesService,
    ) {
        suspend fun read(
            userId: UUID,
            kind: NoteKind = NoteKind.NOTE,
        ): BenflixNote? =
            try {
                displayPreferencesService
                    .getDisplayPreferences(userId)
                    .customPrefs[kind.key]
                    ?.let { decode(it) }
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                Timber.w(ex, "Could not read the note")
                null
            }

        fun isDismissed(
            userId: UUID,
            note: BenflixNote,
            kind: NoteKind = NoteKind.NOTE,
        ): Boolean =
            when (kind) {
                NoteKind.NOTE -> prefs().getString("dismissed_$userId", null) == note.id

                // The welcome note shows once per person, whichever note it was
                NoteKind.WELCOME -> prefs().getBoolean("welcomed_$userId", false)
            }

        fun dismiss(
            userId: UUID,
            note: BenflixNote,
            kind: NoteKind = NoteKind.NOTE,
        ) = prefs().edit {
            when (kind) {
                NoteKind.NOTE -> putString("dismissed_$userId", note.id)
                NoteKind.WELCOME -> putBoolean("welcomed_$userId", true)
            }
        }

        /** Puts a new note on everyone's Home (empty text removes it). Returns how many people got it. */
        suspend fun publish(
            text: String,
            kind: NoteKind = NoteKind.NOTE,
        ): Int =
            withContext(WholphinDispatchers.IO) {
                val users = api.userApi.getUsers().content
                val encoded = text.trim().takeIf { it.isNotEmpty() }?.let { encode(BenflixNote(System.currentTimeMillis().toString(), it)) }
                var count = 0
                users.forEach { user ->
                    try {
                        displayPreferencesService.updateDisplayPreferences(user.id) {
                            if (encoded == null) remove(kind.key) else put(kind.key, encoded)
                        }
                        count++
                    } catch (ex: CancellationException) {
                        throw ex
                    } catch (ex: Exception) {
                        Timber.w(ex, "Could not send the note to %s", user.name)
                    }
                }
                count
            }

        private fun prefs() = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        private fun encode(note: BenflixNote) = note.id + "\n" + note.text

        private fun decode(value: String): BenflixNote? {
            val split = value.indexOf('\n')
            if (split <= 0) return null
            val text = value.substring(split + 1)
            return if (text.isBlank()) null else BenflixNote(value.substring(0, split), text)
        }

        companion object {
            private const val PREFS = "benflix_notes"
        }
    }

/**
 * Benflix "Add a person": make, reset or remove Jellyfin accounts for family and friends
 * right from Benflix (admin only).
 */
@Singleton
class BenflixPeople
    @Inject
    constructor(
        private val api: ApiClient,
    ) {
        suspend fun list(): List<UserDto> =
            withContext(WholphinDispatchers.IO) {
                api.userApi
                    .getUsers()
                    .content
                    .sortedBy { it.name?.lowercase() }
            }

        /** Makes a new account and returns its starting password */
        suspend fun add(name: String): String =
            withContext(WholphinDispatchers.IO) {
                val password = newPassword()
                api.userApi.createUserByName(CreateUserByName(name.trim(), password))
                password
            }

        /** Gives someone a fresh password and returns it */
        suspend fun resetPassword(userId: UUID): String =
            withContext(WholphinDispatchers.IO) {
                val password = newPassword()
                api.userApi.updateUserPassword(userId, UpdateUserPassword(null, null, password, false))
                password
            }

        suspend fun remove(userId: UUID) =
            withContext(WholphinDispatchers.IO) {
                api.userApi.deleteUser(userId)
            }

        /** Easy to read out loud: no 0/O or 1/l mix-ups */
        private fun newPassword(): String {
            val letters = "abcdefghjkmnpqrstuvwxyz23456789"
            val random = SecureRandom()
            return (1..PASSWORD_LENGTH).map { letters[random.nextInt(letters.length)] }.joinToString("")
        }

        companion object {
            private const val PASSWORD_LENGTH = 8
        }
    }
