package com.github.damontecres.wholphin.ui.preferences

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.ui.components.DialogItem
import com.github.damontecres.wholphin.ui.components.DialogPopup
import java.time.Month
import java.time.MonthDay
import java.time.format.TextStyle
import java.util.Locale

/**
 * Pick a birthday: first the month, then the day
 */
@Composable
fun BirthdayDialog(
    onPick: (MonthDay?) -> Unit,
    onDismissRequest: () -> Unit,
) {
    var month by remember { mutableStateOf<Month?>(null) }
    val currentMonth = month
    if (currentMonth == null) {
        DialogPopup(
            showDialog = true,
            title = stringResource(R.string.my_birthday),
            dialogItems =
                buildList {
                    Month.entries.forEach { m ->
                        add(
                            DialogItem(
                                headlineContent = { Text(m.getDisplayName(TextStyle.FULL, Locale.getDefault())) },
                                onClick = { month = m },
                            ),
                        )
                    }
                    add(
                        DialogItem(
                            headlineContent = { Text(stringResource(R.string.remove_birthday)) },
                            onClick = {
                                onPick(null)
                                onDismissRequest()
                            },
                        ),
                    )
                },
            onDismissRequest = onDismissRequest,
            dismissOnClick = false,
            waitToLoad = false,
        )
    } else {
        DialogPopup(
            showDialog = true,
            title = currentMonth.getDisplayName(TextStyle.FULL, Locale.getDefault()),
            dialogItems =
                (1..currentMonth.maxLength()).map { day ->
                    DialogItem(
                        headlineContent = { Text(day.toString()) },
                        onClick = {
                            onPick(MonthDay.of(currentMonth, day))
                            onDismissRequest()
                        },
                    )
                },
            onDismissRequest = onDismissRequest,
            dismissOnClick = false,
            waitToLoad = false,
        )
    }
}
