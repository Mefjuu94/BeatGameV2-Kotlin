package pl.mefjuu.beatgame

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

@Composable
fun ControlsDialog(
    currentSettings: GameSettings,
    onSave: (GameSettings) -> Unit,
    onDismiss: () -> Unit
) {
    var leftKey by remember { mutableStateOf(currentSettings.leftKey) }
    var rightKey by remember { mutableStateOf(currentSettings.rightKey) }
    var bindingSide by remember { mutableStateOf<String?>(null) }
    val focusRequester = remember { FocusRequester() }

    Dialog(onCloseRequest = onDismiss, title = "Settings") {
        Surface(
            modifier = Modifier
                .padding(16.dp)
                .focusRequester(focusRequester)
                .focusable()
                .onKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown && bindingSide != null) {
                        if (bindingSide == "left") leftKey = event.key else rightKey = event.key
                        bindingSide = null
                        true
                    } else false
                }
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(16.dp)) {
                Text("Click to rebind:")
                Spacer(Modifier.height(16.dp))

                Button(
                    onClick = { bindingSide = "left"; focusRequester.requestFocus() },
                    colors = ButtonDefaults.buttonColors(containerColor = if(bindingSide == "left") Color.Red else Color.Gray)
                ) { Text("Left: ${getKeyName(leftKey)}") }

                Button(
                    onClick = { bindingSide = "right"; focusRequester.requestFocus() },
                    colors = ButtonDefaults.buttonColors(containerColor = if(bindingSide == "right") Color.Red else Color.Gray)
                ) { Text("Right: ${getKeyName(rightKey)}") }

                Spacer(Modifier.height(16.dp))
                Button(onClick = { onSave(GameSettings(leftKey, rightKey)); onDismiss() }) { Text("Save") }
            }
        }
    }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
}