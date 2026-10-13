package com.noctra.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.noctra.app.data.Gemini
import com.noctra.app.data.NocConfig
import com.noctra.app.ui.components.ConfirmDialog
import com.noctra.app.ui.components.LightBackdrop
import com.noctra.app.ui.components.NcCard
import com.noctra.app.ui.components.PrimaryButton
import com.noctra.app.ui.components.SecondaryButton
import com.noctra.app.viewmodel.NocMessage
import com.noctra.app.viewmodel.NocRole
import com.noctra.app.viewmodel.NocViewModel

private val suggestions = listOf(
    "Add a physics task for tomorrow, 90 min",
    "What should I do next?",
    "I messed up, I wasted 2 hours",
    "Plan the rest of my day"
)

@Composable
fun NocScreen(vm: NocViewModel = viewModel()) {
    val cfg by vm.config.collectAsStateWithLifecycle()
    val messages = vm.messages
    var input by remember { mutableStateOf("") }
    var confirmClear by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size, vm.sending) {
        val extra = if (vm.sending) 1 else 0
        val count = messages.size + extra
        if (count > 0) listState.animateScrollToItem(count - 1)
    }

    if (confirmClear) {
        ConfirmDialog(
            "Clear chat history?",
            "This deletes the saved conversation with Noc. Your tasks and logs are not touched.",
            "CLEAR",
            { confirmClear = false; vm.clear() },
            { confirmClear = false }
        )
    }

    LightBackdrop {
        Column(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
            Row(
                Modifier.padding(start = 20.dp, top = 16.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Noc", style = MaterialTheme.typography.headlineLarge)
                    Text(
                        "Your AI coach. Ask, plan, confess.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (messages.isNotEmpty()) {
                    IconButton(onClick = { confirmClear = true }, enabled = !vm.sending) { Icon(Icons.Filled.Delete, "Clear chat") }
                }
            }

            if (!cfg.ready) {
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    NcCard {
                        Text("Connect Noc", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Noc runs on Google Gemini. Add your own API key to turn it on.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    NocKeyCard(cfg, onSaveKey = vm::saveKey, onClearKey = {}, onSaveModel = {})
                }
            } else {
                LazyColumn(
                    Modifier.weight(1f),
                    state = listState,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (messages.isEmpty()) {
                        item {
                            NcCard {
                                Text("Talk to Noc", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "Noc sees your tasks and today's numbers. It can add, edit, delete, start, complete or postpone tasks, log study you already did, and log lost time. Tell it when you mess up. Your chat is saved on this phone.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        items(suggestions) { s ->
                            SecondaryButton(s, { vm.send(s) }, Modifier.fillMaxWidth())
                        }
                    }
                    items(messages, key = { it.id }) { m ->
                        Column {
                            Bubble(m)
                            if (vm.canRetry && !vm.sending && m.role == NocRole.ERROR && m.id == messages.last().id) {
                                TextButton(onClick = vm::retry) { Text("TRY AGAIN") }
                            }
                        }
                    }
                    if (vm.sending) {
                        item { Bubble(NocMessage(-1, NocRole.NOC, "Noc is thinking…")) }
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        placeholder = { Text("Message Noc") },
                        shape = RoundedCornerShape(24.dp),
                        maxLines = 4,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = { vm.send(input); input = "" },
                        enabled = input.isNotBlank() && vm.loaded
                    ) { Icon(Icons.AutoMirrored.Filled.Send, "Send", tint = MaterialTheme.colorScheme.primary) }
                }
            }
        }
    }
}

@Composable
private fun Bubble(m: NocMessage) {
    when (m.role) {
        NocRole.USER -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Surface(
                shape = RoundedCornerShape(20.dp, 20.dp, 4.dp, 20.dp),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.widthIn(max = 300.dp)
            ) {
                Text(m.text, Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onPrimary)
            }
        }
        NocRole.NOC -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
            Surface(
                shape = RoundedCornerShape(20.dp, 20.dp, 20.dp, 4.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.widthIn(max = 320.dp)
            ) {
                Text(m.text, Modifier.padding(12.dp))
            }
        }
        NocRole.ACTION -> Text(
            "✓ ${m.text}",
            Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary
        )
        NocRole.ERROR -> Text(
            m.text,
            Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error
        )
    }
}

/** Gemini key + model entry. Used by the Noc tab (first run) and by Settings. */
@Composable
fun NocKeyCard(
    config: NocConfig,
    onSaveKey: (String) -> Unit,
    onClearKey: () -> Unit,
    onSaveModel: (String) -> Unit
) {
    val ctx = LocalContext.current
    var key by remember { mutableStateOf("") }
    var show by remember { mutableStateOf(false) }
    var model by remember(config.model) { mutableStateOf(config.model) }

    NcCard {
        Text("Gemini API key", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        if (config.ready) {
            Text(
                "Connected. Key ending ${config.apiKey.takeLast(4)}.",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = model,
                onValueChange = { model = it },
                label = { Text("Model") },
                supportingText = { Text("Default: ${Gemini.DEFAULT_MODEL} (always the current Flash model)") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            )
            Row {
                TextButton(
                    enabled = model.trim() != config.model,
                    onClick = { onSaveModel(model) }
                ) { Text("SAVE MODEL") }
                TextButton(onClick = onClearKey) { Text("REMOVE KEY") }
            }
        } else {
            OutlinedTextField(
                value = key,
                onValueChange = { key = it.trim() },
                label = { Text("Paste your key") },
                singleLine = true,
                visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { show = !show }) { Text(if (show) "HIDE" else "SHOW") }
                TextButton(onClick = {
                    ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://aistudio.google.com/apikey")))
                }) { Text("GET A FREE KEY") }
            }
            PrimaryButton("SAVE KEY", { onSaveKey(key); key = "" }, Modifier.fillMaxWidth(), enabled = key.length >= 20)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "The key stays on this phone and is sent only to Google. When you chat, your messages and your task list go to Google's Gemini API too.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
