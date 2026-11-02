package com.github.damontecres.wholphin.ui.preferences

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.ui.FontAwesome
import com.github.damontecres.wholphin.ui.components.Button

/** A small icon button (used to move songs up and down in the music queue) */
@Composable
fun MoveButton(
    @StringRes icon: Int,
    enabled: Boolean,
    onClick: () -> Unit,
) = Button(
    onClick = onClick,
    enabled = enabled,
    modifier = Modifier.size(32.dp),
) {
    Text(
        text = stringResource(icon),
        fontSize = 16.sp,
        fontFamily = FontAwesome,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}
