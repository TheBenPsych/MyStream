package com.github.damontecres.wholphin.ui.door

import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.services.GameLibraryClient
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Lets a screen reach Tower's helper and the signed-in server (used by the Live TV Sports tab) */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface MatchHelperEntryPoint {
    fun helper(): GameLibraryClient

    fun servers(): ServerRepository
}
