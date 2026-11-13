package pt.avus.app.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import pt.avus.app.data.OfficialList
import pt.avus.app.data.ThemeMode
import pt.avus.app.letter.Sender
import pt.avus.app.reminders.ReplyReminderWorker

/**
 * Public page of the app (like SpeedTV-app): what it does, downloads, privacy policy and bug reports.
 * The source code stays in the private "Avus" repository.
 */
const val PROJECT_URL = "https://github.com/cruz-py/Avus-app"
const val PRIVACY_URL = "$PROJECT_URL/blob/main/PRIVACIDADE.md"
const val ISSUES_URL = "$PROJECT_URL/issues"
const val SOURCE_URL = "https://github.com/cruz-py/Avus"
private const val CARTA_SOCIAL_URL = "https://www.cartasocial.pt"
private const val OSM_COPYRIGHT_URL = "https://www.openstreetmap.org/copyright"

/** "Definições": appearance, reminders, the family's data and "Sobre". */
@Composable
fun SettingsScreen(state: AppState, vm: AvusViewModel, snackbar: SnackbarHostState, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val settings = state.settings

    var confirmImport by remember { mutableStateOf<Uri?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        uri?.let { vm.exportBackup(it) }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        confirmImport = uri
    }

    // Reminders need notification permission (Android 13+); re-checked when coming back from Settings.
    var canNotify by remember { mutableStateOf(ReplyReminderWorker.canNotify(context)) }
    var askedPermission by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        canNotify = ReplyReminderWorker.canNotify(context)
    }
    LifecycleResumeEffect(Unit) {
        canNotify = ReplyReminderWorker.canNotify(context)
        onPauseOrDispose { }
    }
    fun askForNotifications() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !askedPermission) {
            askedPermission = true
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                )
            }
        }
    }

    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = screenBackground,
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("Definições") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar") }
                },
                colors = screenTopBarColors(),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = ScreenPadding, end = ScreenPadding, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
        ) {
            // ---------- Appearance ----------
            item { ListHeader("Aparência") }
            item {
                SegmentedListItem(colors = segmentedColors(), shapes = segmentedShapes(0, 2)) {
                    Column(Modifier.padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Tema")
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
                        ) {
                            val options = listOf(ThemeMode.SYSTEM to "Automático", ThemeMode.LIGHT to "Claro", ThemeMode.DARK to "Escuro")
                            options.forEachIndexed { i, (mode, label) ->
                                ToggleButton(
                                    checked = settings.theme == mode,
                                    onCheckedChange = { vm.updateSettings { s -> s.copy(theme = mode) } },
                                    modifier = Modifier.weight(1f),
                                    shapes = when (i) {
                                        0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                                        options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                                        else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                                    },
                                    colors = ToggleButtonDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                                ) { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                            }
                        }
                    }
                }
            }
            item {
                val supported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                SegmentedListItem(
                    checked = settings.dynamicColor && supported,
                    onCheckedChange = { on -> vm.updateSettings { it.copy(dynamicColor = on) } },
                    enabled = supported,
                    colors = segmentedColors(),
                    shapes = segmentedShapes(1, 2),
                    supportingContent = {
                        Text(if (supported) "Usa as cores do seu fundo de ecrã." else "Disponível a partir do Android 12.")
                    },
                    trailingContent = {
                        Switch(checked = settings.dynamicColor && supported, onCheckedChange = null, enabled = supported)
                    },
                ) { Text("Cores do papel de parede") }
            }

            // ---------- Reminders ----------
            item { ListHeader("Lembretes", Modifier.padding(top = 12.dp)) }
            item {
                val active = settings.reminders && canNotify
                SegmentedListItem(
                    checked = active,
                    onCheckedChange = { on ->
                        vm.updateSettings { it.copy(reminders = on) }
                        if (on && !canNotify) askForNotifications()
                    },
                    colors = segmentedColors(),
                    shapes = segmentedShapes(0, 1),
                    leadingContent = { ShapeIcon(Icons.Filled.Notifications) },
                    supportingContent = {
                        Text(
                            if (settings.reminders && !canNotify) "As notificações estão desligadas: toque para as ativar."
                            else "Um aviso quando um lar não responde há mais de uma semana."
                        )
                    },
                    trailingContent = { Switch(checked = active, onCheckedChange = null) },
                ) { Text("Lembrar lares sem resposta") }
            }

            // ---------- The family's data ----------
            item { ListHeader("Os meus dados", Modifier.padding(top = 12.dp)) }
            item {
                SettingsRow(Icons.Filled.Share, "Exportar os meus dados", "Cópia de segurança (.zip) da ficha, documentos e respostas.", 0, 3) {
                    exportLauncher.launch("avus-dados-${java.time.LocalDate.now()}.zip")
                }
            }
            item {
                SettingsRow(Icons.Filled.Refresh, "Importar os meus dados", "Repor uma cópia de segurança feita neste ou noutro telemóvel.", 1, 3) {
                    importLauncher.launch(arrayOf("application/zip", "application/octet-stream"))
                }
            }
            item {
                SettingsRow(Icons.Filled.Delete, "Apagar os meus dados", "Apaga a ficha, os documentos e as respostas deste telemóvel.", 2, 3, danger = true) {
                    confirmDelete = true
                }
            }

            // ---------- About ----------
            item { ListHeader("Sobre", Modifier.padding(top = 12.dp)) }
            item {
                SettingsRow(Icons.Filled.Info, "Avus", if (version.isNotBlank()) "Versão $version" else "", 0, 8)
            }
            item {
                SettingsRow(Icons.Filled.Star, "Página do projeto", "Novidades e transferências · ${PROJECT_URL.removePrefix("https://")}", 1, 8) {
                    Sender.openUrl(context, PROJECT_URL)
                }
            }
            item {
                SettingsRow(Icons.Filled.Lock, "Política de privacidade", "O que a app guarda e o que nunca sai do telemóvel.", 2, 8) {
                    Sender.openUrl(context, PRIVACY_URL)
                }
            }
            item {
                SettingsRow(Icons.Filled.Build, "Código-fonte", "Software livre (GPL-3.0) · ${SOURCE_URL.removePrefix("https://")}", 3, 8) {
                    Sender.openUrl(context, SOURCE_URL)
                }
            }
            item {
                SettingsRow(Icons.Filled.Warning, "Reportar um problema", "Erros e sugestões, na página do projeto.", 4, 8) {
                    Sender.openUrl(context, ISSUES_URL)
                }
            }
            item {
                val list = state.officialListDate?.let { " · lista de ${formatIsoDate(it)}" }.orEmpty()
                SettingsRow(Icons.Filled.Home, "Dados dos lares", "${OfficialList.SOURCE}$list", 5, 8) {
                    Sender.openUrl(context, CARTA_SOCIAL_URL)
                }
            }
            item {
                SettingsRow(Icons.Filled.LocationOn, "Mapas", "© Contribuidores do OpenStreetMap · OpenFreeMap · MapLibre", 6, 8) {
                    Sender.openUrl(context, OSM_COPYRIGHT_URL)
                }
            }
            item {
                SettingsRow(
                    Icons.Filled.Share, "Componentes de código aberto",
                    "AndroidX e Jetpack Compose (Apache 2.0) · MapLibre (BSD) · dados do mapa © OpenStreetMap (ODbL)", 7, 8,
                )
            }
        }
    }

    confirmImport?.let { uri ->
        AlertDialog(
            onDismissRequest = { confirmImport = null },
            title = { Text("Importar os dados?") },
            text = {
                Text(
                    "Substitui a ficha, o contacto, os documentos, as respostas e os lares acrescentados neste telemóvel " +
                        "pelos da cópia de segurança. A lista oficial de lares mantém-se."
                )
            },
            confirmButton = { TextButton(onClick = { vm.importBackup(uri); confirmImport = null }) { Text("Importar") } },
            dismissButton = { TextButton(onClick = { confirmImport = null }) { Text("Cancelar") } },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Apagar os seus dados?") },
            text = {
                Text(
                    "A ficha, o contacto, os documentos, as respostas e os lares acrescentados são apagados deste " +
                        "telemóvel. A lista oficial de lares e as definições mantêm-se. Pode exportar uma cópia antes."
                )
            },
            confirmButton = {
                TextButton(onClick = { vm.deleteMyData(); confirmDelete = false }) {
                    Text("Apagar", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    supporting: String,
    index: Int,
    count: Int,
    danger: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val leading: @Composable () -> Unit = {
        if (danger) {
            ShapeIcon(
                icon,
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
            )
        } else ShapeIcon(icon)
    }
    val supportingContent: (@Composable () -> Unit)? = if (supporting.isNotBlank()) ({ Text(supporting) }) else null
    val titleContent: @Composable () -> Unit = {
        Text(title, color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
    }
    if (onClick != null) {
        SegmentedListItem(
            onClick = onClick,
            colors = segmentedColors(),
            shapes = segmentedShapes(index, count),
            leadingContent = leading,
            supportingContent = supportingContent,
            content = titleContent,
        )
    } else {
        SegmentedListItem(
            colors = segmentedColors(),
            shapes = segmentedShapes(index, count),
            leadingContent = leading,
            supportingContent = supportingContent,
            content = titleContent,
        )
    }
}
