package com.github.damontecres.wholphin.ui.setup

import org.jellyfin.sdk.api.client.exception.InvalidStatusException
import java.io.IOException

/**
 * Benflix redesign 2 (su-p-refused): when signing in doesn't work, say why in plain words, with one
 * button for what to do next. Jellyfin's own messages ("Error 401") looked like a Benflix bug.
 */
enum class SignInProblemKind { WRONG_PASSWORD, CANT_REACH, TOO_MANY, OTHER }

data class SignInProblem(
    val kind: SignInProblemKind,
    val title: String,
    val line: String,
    /** The one button */
    val button: String,
)

/**
 * What went wrong, for the person signing in as [name] (blank when we don't know yet).
 *
 * - 401: Jellyfin didn't accept the name and password.
 * - 403: Jellyfin refused the sign-in. Almost always the "max active sessions" limit on the account
 *   (Dashboard > Users > the person > Maximum number of simultaneous user sessions).
 * - No answer at all, or the server is broken (5xx): the Tower is off or still starting.
 */
fun signInProblemFor(
    error: Throwable?,
    name: String?,
): SignInProblem {
    val status = generateSequence(error) { it.cause }.filterIsInstance<InvalidStatusException>().firstOrNull()?.status
    val offline = generateSequence(error) { it.cause }.any { it is IOException }
    val who = name?.trim().orEmpty()
    return when {
        status == 401 -> {
            SignInProblem(
                kind = SignInProblemKind.WRONG_PASSWORD,
                title = "Wrong password",
                line = "Check the name and password and try again. Capital letters count.",
                button = "Type it again",
            )
        }

        status == 403 -> {
            SignInProblem(
                kind = SignInProblemKind.TOO_MANY,
                title = "On too many TVs",
                line = tooManyLine(who),
                button = "OK",
            )
        }

        (status != null && status >= 500) || (status == null && offline) -> {
            SignInProblem(
                kind = SignInProblemKind.CANT_REACH,
                title = "Can't reach the Tower (is it on?)",
                line = "Check the Tower is switched on, then try again in a minute.",
                button = "Try again",
            )
        }

        else -> {
            SignInProblem(
                kind = SignInProblemKind.OTHER,
                title = "That didn't work",
                line = "Something went wrong signing in. Try again in a minute.",
                button = "Try again",
            )
        }
    }
}

/** Ben's wording: "Ben's account is on too many TVs. Sign out on one, or ask Ben." */
fun tooManyLine(name: String): String {
    val whose = if (name.isBlank()) "This account" else "$name's account"
    val ask = if (name.equals("Ben", ignoreCase = true)) "" else ", or ask Ben"
    return "$whose is on too many TVs. Sign out on one$ask."
}
