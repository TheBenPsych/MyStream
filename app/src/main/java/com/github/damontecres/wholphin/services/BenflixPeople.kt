package com.github.damontecres.wholphin.services

import com.github.damontecres.wholphin.util.WholphinDispatchers
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.userApi
import org.jellyfin.sdk.model.UUID
import org.jellyfin.sdk.model.api.CreateUserByName
import org.jellyfin.sdk.model.api.UpdateUserPassword
import org.jellyfin.sdk.model.api.UserDto
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

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
