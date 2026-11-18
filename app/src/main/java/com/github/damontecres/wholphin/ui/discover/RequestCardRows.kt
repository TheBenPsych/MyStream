package com.github.damontecres.wholphin.ui.discover

import com.github.damontecres.wholphin.data.model.RequestStatus

private fun declined(entry: RequestEntry) =
    !entry.available && (entry.status == RequestStatus.DECLINED || entry.status == RequestStatus.FAILURE)

private fun progress(entry: RequestEntry): Float =
    when {
        entry.available -> 1f
        declined(entry) -> 1f
        entry.downloading -> 0.4f + 0.55f * ((entry.downloadPercent ?: 0) / 100f)
        entry.status == RequestStatus.PENDING -> 0.12f
        else -> 0.35f
    }
