package com.github.damontecres.wholphin.ui.games.ember

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.ui.theme.BenflixTitleFont

/** A phone joining the TV's game: it shows the TV's words, and its buttons vote or play */
@Composable
fun EmberPhoneScreen(vm: EmberViewModel) {
    val context = LocalContext.current
    var tvs by remember { mutableStateOf<List<FoundEmberTv>>(emptyList()) }
    var view by remember { mutableStateOf<PhoneView?>(null) }
    val client =
        remember {
            EmberLinkClient(
                context = context.applicationContext,
                phoneName = vm.phoneName,
                onFound = { list -> android.os.Handler(android.os.Looper.getMainLooper()).post { tvs = list } },
                onView = { v -> android.os.Handler(android.os.Looper.getMainLooper()).post { view = v } },
            )
        }
    DisposableEffect(client) {
        client.start()
        onDispose { client.stop() }
    }
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
    ) {
        Text("EMBER ROAD", fontFamily = BenflixTitleFont, fontSize = 28.sp, color = EmberGold)
        val v = view
        if (v == null) {
            Text(
                if (tvs.isEmpty()) {
                    "Looking for a TV playing Ember Road… Open Ember Road on the TV (Games on Benflix). This phone needs to be on the same Wi-Fi."
                } else {
                    "Pick the TV to join:"
                },
                color = EmberText,
                fontSize = 17.sp,
            )
            tvs.forEach { t -> EmberButton(t.name, onClick = { client.join(t) }, modifier = Modifier.fillMaxWidth()) }
            EmberButton("Back", onClick = { vm.goTo(EmberScreen.TITLE) })
            return@Column
        }
        if (v.status.isNotBlank()) Text(v.status, color = EmberDim, fontSize = 13.sp)
        if (v.title.isNotBlank()) Text(v.title, color = EmberGold, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        TypeText(v.text, size = 17)
        v.reply?.takeIf { v.typing }?.let {
            Text(
                it,
                color = EmberText,
                fontSize = 15.sp,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF2A1C18), RoundedCornerShape(6.dp))
                        .padding(10.dp),
            )
        }
        if (v.typing) TypeBox(isTv = false, autoFocus = false) { client.type(it) }
        if (v.vote &&
            v.actions.isNotEmpty()
        ) {
            Text(
                if (v.mine ==
                    null
                ) {
                    "Vote!"
                } else {
                    "Vote counted. You can change it."
                },
                color = EmberDim,
                fontSize = 14.sp,
            )
        }
        v.actions.forEach { a ->
            EmberButton(
                (if (v.mine == a.id) "● " else "") + a.label,
                onClick = { client.press(a.id) },
                big = a.id.startsWith("mg:"),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        EmberButton("Leave the TV's game", onClick = {
            client.leave()
            vm.goTo(EmberScreen.TITLE)
        })
    }
}
