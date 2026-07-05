package com.ella.music.ui.online

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.ella.music.R
import com.ella.music.data.SettingsManager
import com.ella.music.data.remote.EmbyService
import com.ella.music.data.remote.NavidromeService
import com.ella.music.data.remote.RemoteMusicProvider
import com.ella.music.data.remote.RemoteMusicSourceConfig
import com.ella.music.data.remote.SavedRemoteServer
import com.ella.music.ui.components.ConfirmDangerDialog
import com.ella.music.ui.components.EllaMiuixAction
import com.ella.music.ui.components.EllaMiuixActionRow
import com.ella.music.ui.components.EllaMiuixBottomSheet
import com.ella.music.ui.components.EllaSmallTopAppBar
import com.ella.music.ui.components.ellaPageBackground
import com.ella.music.ui.folder.WebDavTextField
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * Manage multiple saved servers for a remote music [provider] (Navidrome / Emby): add, edit,
 * delete, and pick the active one. The active server backs [SettingsManager.navidromeConfig] /
 * [SettingsManager.embyConfig] used by the rest of the app.
 */
@Composable
fun RemoteServerSettingsScreen(
    provider: RemoteMusicProvider,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settingsManager = remember { SettingsManager.getInstance(context) }
    val isNavidrome = provider == RemoteMusicProvider.Navidrome
    val providerName = if (isNavidrome) {
        stringResource(R.string.remote_source_navidrome)
    } else {
        stringResource(R.string.remote_source_emby)
    }
    val servers by (if (isNavidrome) settingsManager.navidromeServers else settingsManager.embyServers)
        .collectAsState(initial = emptyList())
    val activeId by (if (isNavidrome) settingsManager.navidromeActiveServerId else settingsManager.embyActiveServerId)
        .collectAsState(initial = "")

    var editorServer by remember { mutableStateOf<SavedRemoteServer?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<SavedRemoteServer?>(null) }
    val pageBackground = ellaPageBackground()
    val cardColor = if (MiuixTheme.colorScheme.background.luminance() < 0.5f) {
        Color(0xFF1D1D21)
    } else {
        Color(0xFFFFFFFF)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(pageBackground)
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        EllaSmallTopAppBar(
            title = stringResource(R.string.remote_server_manage_title, providerName),
            color = pageBackground,
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = MiuixIcons.Regular.Back,
                        contentDescription = stringResource(R.string.common_back),
                        tint = MiuixTheme.colorScheme.onSurface,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (servers.isEmpty()) {
                item {
                    RemoteServerCard(cardColor = cardColor) {
                        BasicComponent(
                            title = stringResource(R.string.remote_server_empty),
                            summary = stringResource(R.string.remote_server_manage_summary)
                        )
                    }
                }
            }
            items(servers, key = { it.id }) { server ->
                RemoteServerRow(
                    server = server,
                    isActive = server.id == activeId,
                    cardColor = cardColor,
                    onSetActive = {
                        scope.launch {
                            if (isNavidrome) settingsManager.setActiveNavidromeServer(server.id)
                            else settingsManager.setActiveEmbyServer(server.id)
                        }
                    },
                    onEdit = {
                        editorServer = server
                        showEditor = true
                    },
                    onDelete = { pendingDelete = server }
                )
            }
            item {
                RemoteServerCard(cardColor = cardColor) {
                    ArrowPreference(
                        title = stringResource(R.string.remote_server_add),
                        summary = providerName,
                        onClick = {
                            editorServer = null
                            showEditor = true
                        }
                    )
                }
                Spacer(modifier = Modifier.height(120.dp))
            }
        }
    }

    if (showEditor) {
        RemoteServerEditorSheet(
            provider = provider,
            existing = editorServer,
            onDismiss = { showEditor = false },
            onSaved = { showEditor = false }
        )
    }

    pendingDelete?.let { server ->
        ConfirmDangerDialog(
            show = true,
            title = stringResource(R.string.remote_server_edit),
            message = stringResource(R.string.remote_server_delete_message, server.name.ifBlank { server.config.baseUrl }),
            confirmText = stringResource(R.string.common_delete),
            onDismiss = { pendingDelete = null },
            onConfirm = {
                scope.launch {
                    if (isNavidrome) settingsManager.deleteNavidromeServer(server.id)
                    else settingsManager.deleteEmbyServer(server.id)
                }
                pendingDelete = null
            }
        )
    }
}

@Composable
private fun RemoteServerCard(
    cardColor: Color,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 16.dp,
        insideMargin = PaddingValues(0.dp),
        colors = CardDefaults.defaultColors(color = cardColor)
    ) {
        content()
    }
}

@Composable
private fun RemoteServerRow(
    server: SavedRemoteServer,
    isActive: Boolean,
    cardColor: Color,
    onSetActive: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    RemoteServerCard(cardColor = cardColor) {
        Column {
            BasicComponent(
                title = server.name.ifBlank { server.config.baseUrl },
                summary = buildString {
                    append(server.config.baseUrl)
                    if (isActive) {
                        append('\n')
                        append(stringResource(R.string.remote_server_active))
                    }
                }
            )
            if (!isActive) {
                ArrowPreference(
                    title = stringResource(R.string.remote_server_set_active),
                    summary = server.name.ifBlank { server.config.baseUrl },
                    onClick = onSetActive
                )
            }
            ArrowPreference(
                title = stringResource(R.string.remote_server_edit),
                summary = server.name.ifBlank { server.config.baseUrl },
                onClick = onEdit
            )
            ArrowPreference(
                title = stringResource(R.string.common_delete),
                summary = server.name.ifBlank { server.config.baseUrl },
                onClick = onDelete
            )
        }
    }
}

@Composable
private fun RemoteServerEditorSheet(
    provider: RemoteMusicProvider,
    existing: SavedRemoteServer?,
    onDismiss: () -> Unit,
    onSaved: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settingsManager = remember { SettingsManager.getInstance(context) }
    val navidromeService = remember(context) { NavidromeService(context) }
    val embyService = remember(context) { EmbyService(context) }
    val isNavidrome = provider == RemoteMusicProvider.Navidrome
    val providerName = stringResource(
        if (isNavidrome) R.string.remote_source_navidrome else R.string.remote_source_emby
    )

    var name by remember(existing) { mutableStateOf(existing?.name.orEmpty()) }
    var url by remember(existing) { mutableStateOf(existing?.config?.baseUrl.orEmpty()) }
    var user by remember(existing) { mutableStateOf(existing?.config?.username.orEmpty()) }
    var password by remember(existing) { mutableStateOf(existing?.config?.password.orEmpty()) }
    var status by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }

    EllaMiuixBottomSheet(
        show = true,
        title = stringResource(if (existing == null) R.string.remote_server_add else R.string.remote_server_edit),
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier.padding(bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            WebDavTextField(stringResource(R.string.remote_server_name_label), name, onValueChange = { name = it })
            WebDavTextField(stringResource(R.string.webdav_url), url, onValueChange = { url = it })
            WebDavTextField(stringResource(R.string.webdav_username), user, onValueChange = { user = it })
            WebDavTextField(
                label = stringResource(R.string.webdav_password),
                value = password,
                onValueChange = { password = it },
                visualTransformation = PasswordVisualTransformation()
            )
            status?.let {
                Text(text = it, color = MiuixTheme.colorScheme.primary, modifier = Modifier.padding(vertical = 8.dp))
            }
            EllaMiuixActionRow(
                actions = listOf(
                    EllaMiuixAction(text = stringResource(R.string.common_cancel), onClick = onDismiss),
                    EllaMiuixAction(
                        text = stringResource(R.string.common_save),
                        primary = true,
                        onClick = onClick@{
                            if (saving) return@onClick
                            saving = true
                            scope.launch {
                                val id = existing?.id ?: settingsManager.newRemoteServerId()
                                val trimmedUrl = url.trim().trimEnd('/')
                                val displayName = name.trim().ifBlank {
                                    trimmedUrl.substringAfter("://").substringBefore('/').ifBlank { trimmedUrl }
                                }
                                runCatching {
                                    if (isNavidrome) {
                                        val config = RemoteMusicSourceConfig(provider, trimmedUrl, user.trim(), password)
                                        navidromeService.test(config)
                                        settingsManager.upsertNavidromeServer(
                                            SavedRemoteServer(id = id, name = displayName, config = config)
                                        )
                                    } else {
                                        val login = embyService.login(trimmedUrl, user.trim(), password)
                                        val config = RemoteMusicSourceConfig(
                                            provider = provider,
                                            baseUrl = trimmedUrl,
                                            username = user.trim(),
                                            token = login.token,
                                            userId = login.userId,
                                            serverName = login.serverName
                                        )
                                        settingsManager.upsertEmbyServer(
                                            SavedRemoteServer(id = id, name = displayName, config = config)
                                        )
                                    }
                                }.onSuccess {
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.remote_source_saved_named, providerName),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    saving = false
                                    onSaved()
                                }.onFailure { error ->
                                    saving = false
                                    status = error.localizedMessage ?: context.getString(R.string.remote_source_request_failed)
                                }
                            }
                        }
                    )
                )
            )
        }
    }
}
