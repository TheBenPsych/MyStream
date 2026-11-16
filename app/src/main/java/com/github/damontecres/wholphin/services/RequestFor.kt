package com.github.damontecres.wholphin.services

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * Benflix "Request for" (Ben 2026-11-16): when family ask Ben to find something, he can look it up and ask
 * for it in their name. The request is then theirs: it shows in their Your requests, and they get the
 * "it's here" bubble, Home news and "Couldn't find it" notes, just like when they ask themselves.
 *
 * Only admins get the picker, Kids is never a choice, and it starts on "Me" again after [KEEP_MS] without
 * asking for anything, or when the profile changes, so a later ask doesn't land on the wrong person by accident.
 *
 * Movies, shows and anime: the Seerr request gets "userId" added on the way out (SeerrApiClient).
 * Music and books: the helper call gets "for" (GameLibraryClient.postJson).
 */
object RequestFor {
    data class Person(
        /** Jellyfin account id (no dashes, as Jellyfin writes it) */
        val id: String,
        val name: String,
        /** Their Seerr account number, or null when Seerr couldn't be matched */
        val seerr: Int?,
    )

    private const val KEEP_MS = 20 * 60 * 1000L

    private val _people = MutableStateFlow<List<Person>>(emptyList())

    /** Everyone Ben can ask for, himself left out */
    val people: StateFlow<List<Person>> = _people

    private val _picked = MutableStateFlow<Person?>(null)

    /** Who the next ask is for (null = me) */
    val picked: StateFlow<Person?> = _picked

    @Volatile private var lastUsed = 0L

    @Volatile private var me: String? = null

    fun pick(person: Person?) {
        _picked.value = person
        lastUsed = System.currentTimeMillis()
        Timber.i("Request for: %s", person?.name ?: "me")
    }

    fun clear() {
        if (_picked.value != null) _picked.value = null
    }

    /** The person picked for this ask (null = me). Starts on me again after a while without asking. */
    fun current(): Person? {
        val p = _picked.value ?: return null
        if (KidsMode.isActive || GuestMode.isActive || System.currentTimeMillis() - lastUsed > KEEP_MS) {
            clear()
            return null
        }
        lastUsed = System.currentTimeMillis()
        return p
    }

    internal fun loaded(
        meId: String,
        list: List<Person>,
    ) {
        if (me != null && me != meId) clear()
        me = meId
        _people.value = list.filter { !it.id.equals(meId, ignoreCase = true) }
    }
}

/** Loads the people for the "Request for" picker from Tower (admins only) */
@HiltViewModel
class RequestForViewModel
    @Inject
    constructor(
        private val helper: GameLibraryClient,
    ) : ViewModel() {
        val people = RequestFor.people
        val picked = RequestFor.picked
        val loading = MutableStateFlow(false)
        val problem = MutableStateFlow<String?>(null)

        fun load() {
            if (loading.value) return
            loading.value = true
            viewModelScope.launch {
                try {
                    val j = helper.getJson("/api/family")
                    val list = j.optJSONArray("people")
                    val out =
                        (0 until (list?.length() ?: 0)).mapNotNull { i ->
                            val o = list!!.optJSONObject(i) ?: return@mapNotNull null
                            RequestFor.Person(
                                id = o.optString("id"),
                                name = o.optString("name"),
                                seerr = o.optInt("seerr", 0).takeIf { it > 0 },
                            )
                        }
                    RequestFor.loaded(j.optString("me"), out)
                    problem.value = null
                } catch (ex: Exception) {
                    Timber.w(ex, "Request for: couldn't load the family")
                    problem.value = ex.message ?: "Tower didn't answer."
                } finally {
                    loading.value = false
                }
            }
        }
    }
