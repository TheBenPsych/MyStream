package com.github.damontecres.wholphin.ui.discover

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.github.damontecres.wholphin.data.model.DiscoverItem
import com.github.damontecres.wholphin.ui.util.EmptyStringProvider
import com.github.damontecres.wholphin.ui.util.StringProvider
import com.github.damontecres.wholphin.util.DataLoadingState
import com.github.damontecres.wholphin.util.DiscoverRequestType
import java.util.UUID

fun Int.toUUID() = UUID(0L, toLong())

fun UUID.toInt() = leastSignificantBits.toInt()

data class DiscoverRowData(
    val title: StringProvider,
    val items: DataLoadingState<List<DiscoverItem>>,
    val type: DiscoverRequestType,
) {
    companion object {
        val EMPTY =
            DiscoverRowData(
                EmptyStringProvider,
                DataLoadingState.Pending,
                DiscoverRequestType.UNKNOWN,
            )
    }
}
