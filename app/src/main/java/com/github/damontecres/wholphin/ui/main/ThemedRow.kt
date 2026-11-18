package com.github.damontecres.wholphin.ui.main

import com.github.damontecres.wholphin.data.model.BaseItem

/** A row of titles with its own name (Because you watched, a genre, and so on) */
data class ThemedRow(
    val title: String,
    val items: List<BaseItem>,
)
