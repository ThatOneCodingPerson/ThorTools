package io.github.thatonecodingperson.thortools.hotkeys

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.FormatClear
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SpeakerNotesOff
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Splitscreen
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import io.github.thatonecodingperson.thortools.R
import io.github.thatonecodingperson.thortools.actions.ActionCategory
import io.github.thatonecodingperson.thortools.actions.ThorAction
import io.github.thatonecodingperson.thortools.actions.icon
import io.github.thatonecodingperson.thortools.ui.composables.CreditNote
import io.github.thatonecodingperson.thortools.ui.composables.DialogButton
import io.github.thatonecodingperson.thortools.ui.composables.HomeOffDialog
import io.github.thatonecodingperson.thortools.ui.composables.SubTopAppBar
import io.github.thatonecodingperson.thortools.ui.composables.SwitchPreference
import io.github.thatonecodingperson.thortools.ui.composables.TriggerPreference

/**
 * Hotkeys, laid out like a game's controls menu: a rail of lists on the left (your hotkeys, suggestions, every action
 * by category, options) and the chosen list on the right. The editor and the app picker take the whole screen.
 */
@Composable
fun HotkeysScreen(viewModel: HotkeysViewModel = hiltViewModel(), onlyAction: ThorAction? = null, onBack: () -> Unit) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.refresh()
        onlyAction?.let(viewModel::editOnly)
    }
    // Opened for one action's hotkey: nothing but its editor, and done once that closes.
    if (onlyAction != null && uiState.draft == null) {
        if (uiState.onlyAction == onlyAction) LaunchedEffect(Unit) { onBack() }
        return
    }
    BackHandler(enabled = uiState.recording || uiState.picker != null || uiState.draft != null) { viewModel.back() }

    val draft = uiState.draft
    val picker = uiState.picker
    when {
        picker != null -> AppPicker(
            mode = picker,
            apps = uiState.apps,
            loaded = uiState.appsLoaded,
            checked = if (picker == AppPickerMode.HOTKEY_APPS) draft?.apps.orEmpty() else uiState.offApps,
            onPick = viewModel::pickApp,
            onClose = viewModel::closePicker,
        )
        draft != null -> HotkeyEditor(state = uiState, draft = draft, viewModel = viewModel)
        else -> HotkeyLists(state = uiState, viewModel = viewModel, onBack = onBack)
    }

    if (uiState.confirmReset) {
        AlertDialog(
            onDismissRequest = viewModel::dismissReset,
            confirmButton = { DialogButton(text = stringResource(R.string.hotkeysReset), onClick = viewModel::reset) },
            dismissButton = { DialogButton(text = stringResource(R.string.cancel), onClick = viewModel::dismissReset) },
            text = { Text(text = stringResource(R.string.hotkeysResetConfirm)) },
        )
    }
    if (uiState.confirmHomeOff > 0) {
        HomeOffDialog(uiState.confirmHomeOff, onConfirm = viewModel::homeOffConfirmed, onDismiss = viewModel::homeOffDismissed)
    }
}

private data class TabItem(val tab: HotkeyTab, val icon: ImageVector, val label: String, val count: Int?)

@Composable
private fun HotkeyLists(state: HotkeysUiModel, viewModel: HotkeysViewModel, onBack: () -> Unit) {
    val tabs = listOf(
        TabItem(HotkeyTab.Yours, Icons.Rounded.Keyboard, stringResource(R.string.hotkeysTabYours), HotkeyList.listed(state.hotkeys).size),
        TabItem(HotkeyTab.Suggested, Icons.Rounded.Lightbulb, stringResource(R.string.hotkeysTabSuggested), null),
    ) + ActionCategory.entries.map { category ->
        TabItem(
            HotkeyTab.Actions(category),
            category.tabIcon,
            stringResource(category.label),
            HotkeyList.listed(state.hotkeys).count { it.action.category == category }.takeIf { it > 0 },
        )
    } + TabItem(HotkeyTab.Options, Icons.Rounded.Settings, stringResource(R.string.hotkeysTabOptions), null)

    Scaffold(topBar = { SubTopAppBar(title = R.string.hotkeys, onBack = onBack) }) { padding ->
        BoxWithConstraints(Modifier.padding(top = padding.calculateTopPadding()).fillMaxSize()) {
            if (maxWidth >= TWO_PANE_WIDTH) {
                Row(Modifier.fillMaxSize()) {
                    Column(
                        Modifier
                            .width(RAIL_WIDTH)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        tabs.forEach { item ->
                            NavigationDrawerItem(
                                label = { Text(item.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                icon = { Icon(item.icon, contentDescription = null) },
                                badge = { item.count?.let { Text(it.toString()) } },
                                selected = state.tab == item.tab,
                                onClick = { viewModel.selectTab(item.tab) },
                            )
                        }
                    }
                    VerticalDivider()
                    TabContent(state, viewModel, Modifier.weight(1f))
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        tabs.forEach { item ->
                            FilterChip(
                                selected = state.tab == item.tab,
                                onClick = { viewModel.selectTab(item.tab) },
                                label = { Text(item.label) },
                                leadingIcon = { Icon(item.icon, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            )
                        }
                    }
                    TabContent(state, viewModel, Modifier.weight(1f))
                }
            }
        }
    }
}

private val ActionCategory.tabIcon: ImageVector
    get() = when (this) {
        ActionCategory.CONTROLLER -> Icons.Rounded.SportsEsports
        ActionCategory.SCREENS -> Icons.Rounded.Splitscreen
        ActionCategory.SYSTEM -> Icons.Rounded.PhoneAndroid
        ActionCategory.LEVELS -> Icons.Rounded.Tune
        ActionCategory.PERFORMANCE -> Icons.Rounded.Speed
        ActionCategory.APPS -> Icons.Rounded.Apps
    }

@Composable
private fun TabContent(state: HotkeysUiModel, viewModel: HotkeysViewModel, modifier: Modifier) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.fillMaxHeight(),
    ) {
        if (!state.serviceRunning) item { NoteCard(stringResource(R.string.hotkeysServiceOff), warning = true) }
        if (state.homeHeldBack) {
            item {
                NoteCard(
                    text = stringResource(R.string.hotkeysHomeHeldBack),
                    warning = true,
                    actionLabel = stringResource(R.string.hotkeysTurnOn),
                    onAction = viewModel::turnOnSinglePressHome,
                )
            }
        }
        when (val tab = state.tab) {
            HotkeyTab.Yours -> yourHotkeys(state, viewModel)
            HotkeyTab.Suggested -> suggestions(state, viewModel)
            is HotkeyTab.Actions -> when (tab.category) {
                ActionCategory.APPS -> appHotkeys(state, viewModel)
                else -> actions(tab.category, state, viewModel)
            }
            HotkeyTab.Options -> item { Options(state, viewModel) }
        }
    }
}

private fun LazyListScope.yourHotkeys(state: HotkeysUiModel, viewModel: HotkeysViewModel) {
    if (HotkeyList.listed(state.hotkeys).isEmpty()) {
        item {
            EmptyHotkeys(
                onSuggestions = { viewModel.selectTab(HotkeyTab.Suggested) },
                onBrowse = { viewModel.selectTab(HotkeyTab.Actions(ActionCategory.SCREENS)) },
            )
        }
        return
    }
    val order = compareBy<Hotkey>({ it.button.ordinal }, { it.second?.ordinal ?: -1 }, { it.press.ordinal })
    val sorted = HotkeyList.listed(state.hotkeys).sortedWith(order)
    sorted.groupBy { it.button }.forEach { (button, hotkeys) ->
        item(key = "head-${button.id}") { ListHeading(stringResource(button.label)) }
        items(hotkeys, key = { HotkeyList.encode(listOf(it)) }) { hotkey ->
            HotkeyCard(hotkey, state) { viewModel.edit(hotkey) }
        }
    }
}

@Composable
private fun EmptyHotkeys(onSuggestions: () -> Unit, onBrowse: () -> Unit) {
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(20.dp)) {
            Text(stringResource(R.string.hotkeysNone), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.hotkeysNoneInfo),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = onSuggestions) { Text(stringResource(R.string.hotkeysSeeSuggestions)) }
                OutlinedButton(onClick = onBrowse) { Text(stringResource(R.string.hotkeysBrowseActions)) }
            }
        }
    }
}

/** One hotkey: its keys on the left, what it does on the right. */
@Composable
private fun HotkeyCard(hotkey: Hotkey, state: HotkeysUiModel, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Box(Modifier.width(TRIGGER_WIDTH)) { TriggerKeys(hotkey.button, hotkey.second, hotkey.press) }
            ActionLabel(hotkey, state, Modifier.weight(1f))
            SwitchMarks(hotkey)
            Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** The action's icon and name; for an app, its icon, name and screen. */
@Composable
private fun ActionLabel(hotkey: Hotkey, state: HotkeysUiModel, modifier: Modifier = Modifier) {
    val launch = AppLaunch.decode(hotkey.arg).takeIf { hotkey.action == ThorAction.LAUNCH_APP }
    val app = launch?.let { state.app(it.packageName) }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        if (app != null) {
            Image(painter = rememberDrawablePainter(app.icon), contentDescription = null, modifier = Modifier.size(24.dp))
        } else {
            Icon(hotkey.action.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
        }
        Column(Modifier.padding(horizontal = 12.dp)) {
            Text(
                text = if (launch != null) {
                    stringResource(R.string.hotkeyOpenApp, app?.name ?: launch.packageName)
                } else {
                    stringResource(hotkey.action.label)
                },
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = when {
                    launch != null -> stringResource(launch.screen.label)
                    // Close the current app says which screen it acts on; hotkeys from before 0.16.0 act on the last app.
                    hotkey.action == ThorAction.CLOSE_APP ->
                        stringResource(CloseAppArg.decode(hotkey.arg)?.label ?: R.string.hotkeyCloseLegacy)
                    else -> triggerText(hotkey.button, hotkey.second, hotkey.press)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            if (hotkey.apps.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.hotkeyOnlyIn, appNames(hotkey.apps, state)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** The apps' names as the launcher shows them, in order; a package that isn't installed shows as it is. */
fun appNames(apps: Set<String>, state: HotkeysUiModel): String = apps.map { state.app(it)?.name ?: it }.sorted().joinToString(", ")

/** One profile's ready-made hotkeys, each with Add or Replace; the profiles are tabs (users can't make their own). */
private fun LazyListScope.suggestions(state: HotkeysUiModel, viewModel: HotkeysViewModel) {
    val profile = state.suggestionProfile
    item(key = "profiles") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
            SuggestionProfile.entries.forEach { choice ->
                FilterChip(
                    selected = profile == choice,
                    onClick = { viewModel.selectProfile(choice) },
                    label = { Text(stringResource(choice.title)) },
                )
            }
        }
    }
    item(key = "profile-info") {
        val free = profile.hotkeys.count { suggestion -> state.hotkeys.none { it.sameTrigger(suggestion) } }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(profile.info),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f).padding(end = 12.dp),
            )
            FilledTonalButton(onClick = viewModel::addFreeSuggestions, enabled = free > 0) { Text(stringResource(R.string.hotkeysAddFree)) }
        }
    }
    if (profile.credit) item(key = "credit") { Credit() }
    if (!state.singlePressHome && profile.hotkeys.any { it.usesHome }) item { NoteCard(stringResource(R.string.hotkeysSuggestedHome)) }
    items(profile.hotkeys, key = { HotkeyList.encode(listOf(it)) }) { suggestion ->
        val added = state.hotkeys.any { it.sameJob(suggestion) }
        val taken = state.hotkeys.find { it.sameTrigger(suggestion) && !it.sameJob(suggestion) }
        Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Box(Modifier.width(TRIGGER_WIDTH)) { TriggerKeys(suggestion.button, suggestion.second, suggestion.press) }
                Column(Modifier.weight(1f)) {
                    ActionLabel(suggestion, state)
                    if (taken != null) {
                        Text(
                            text = stringResource(R.string.hotkeySuggestionTaken, stringResource(taken.action.label)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 36.dp),
                        )
                    }
                }
                if (added) {
                    Icon(
                        Icons.Rounded.Check,
                        contentDescription = stringResource(R.string.hotkeySuggestionAdded),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                } else {
                    OutlinedButton(onClick = { viewModel.addSuggestion(suggestion) }) {
                        Text(stringResource(if (taken != null) R.string.hotkeySuggestionReplace else R.string.hotkeySuggestionAdd))
                    }
                }
            }
        }
    }
}

/** Where the Wayfinder-Like profile's idea came from. */
@Composable
private fun Credit() = CreditNote(
    stringResource(R.string.suggestionCredit),
    stringResource(R.string.suggestionCreditLink),
    SuggestionProfile.WAYFINDER_URL,
)

private fun LazyListScope.actions(category: ActionCategory, state: HotkeysUiModel, viewModel: HotkeysViewModel) {
    item { ListHeading(stringResource(R.string.hotkeysActionsInfo)) }
    if (category == ThorAction.TOGGLE_DESKTOP.category) item { ListHeading(stringResource(R.string.hotkeysDesktopElsewhere)) }
    val shown = ThorAction.entries.filter { it.category == category && !it.needsApp && it !in HotkeyList.SET_ELSEWHERE }
    items(shown, key = { it.id }) { action ->
        ActionCard(
            action = action,
            hotkeys = state.hotkeys.filter { it.action == action },
            onAdd = { viewModel.add(action) },
            onEdit = viewModel::edit,
        )
    }
}

/** An action with every hotkey it has, and a way to add one. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActionCard(action: ThorAction, hotkeys: List<Hotkey>, onAdd: () -> Unit, onEdit: (Hotkey) -> Unit) {
    Surface(
        onClick = onAdd,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(12.dp)) {
            Icon(
                action.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 2.dp).size(24.dp),
            )
            Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(stringResource(action.label), style = MaterialTheme.typography.bodyLarge)
                Text(
                    stringResource(action.description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (hotkeys.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        hotkeys.forEach { hotkey -> TriggerChip(hotkey) { onEdit(hotkey) } }
                    }
                }
            }
            AddButton(onAdd)
        }
    }
}

@Composable
private fun TriggerChip(hotkey: Hotkey, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)) {
            TriggerKeys(hotkey.button, hotkey.second, hotkey.press)
            SwitchMarks(hotkey)
        }
    }
}

/** Small marks for a hotkey's switches: it also locks the controller, it shows no text. */
@Composable
private fun SwitchMarks(hotkey: Hotkey) {
    val tint = MaterialTheme.colorScheme.onSurfaceVariant
    if (hotkey.lock) {
        Icon(
            Icons.Rounded.Lock,
            contentDescription = stringResource(R.string.hotkeyLockMark),
            tint = tint,
            modifier = Modifier.padding(start = 6.dp).size(16.dp),
        )
    }
    if (hotkey.cleanMemory) {
        Icon(
            Icons.Rounded.Memory,
            contentDescription = stringResource(R.string.hotkeyCleanMemoryMark),
            tint = tint,
            modifier = Modifier.padding(start = 6.dp).size(16.dp),
        )
    }
    if (!hotkey.showText) {
        Icon(
            Icons.Rounded.SpeakerNotesOff,
            contentDescription = stringResource(R.string.hotkeyNoTextMark),
            tint = tint,
            modifier = Modifier.padding(start = 6.dp).size(16.dp),
        )
    }
}

/** Every hotkey's text switch at once, as two option rows; each hotkey also has its own in the editor. */
@Composable
private fun TextForAll(hotkeys: List<Hotkey>, onSet: (Boolean) -> Unit) {
    val on = hotkeys.count { it.showText }
    val count = stringResource(R.string.hotkeysTextAllInfo, on, hotkeys.size)
    OptionRow(Icons.Rounded.TextFields, stringResource(R.string.hotkeysTextShowAll), count, enabled = on < hotkeys.size) { onSet(true) }
    OptionRow(Icons.Rounded.FormatClear, stringResource(R.string.hotkeysTextHideAll), count, enabled = on > 0) { onSet(false) }
}

/** An option that does something at once, in the look of the other option rows. */
@Composable
private fun OptionRow(icon: ImageVector, title: String, description: String, enabled: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Icon(icon, contentDescription = null, tint = colors.onSurfaceVariant)
        Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
            Text(title, color = if (enabled) colors.onSurface else colors.onSurface.copy(alpha = 0.38f))
            Text(text = description, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
    }
}

@Composable
private fun AddButton(onClick: () -> Unit) {
    FilledTonalButton(onClick = onClick, contentPadding = PaddingValues(horizontal = 12.dp)) {
        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(4.dp))
        Text(stringResource(R.string.hotkeyAddShort))
    }
}

private fun LazyListScope.appHotkeys(state: HotkeysUiModel, viewModel: HotkeysViewModel) {
    item {
        Surface(
            onClick = { viewModel.openPicker(AppPickerMode.OPEN_APP) },
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(12.dp)) {
                Icon(ThorAction.LAUNCH_APP.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(stringResource(R.string.hotkeyOpenAnApp), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        stringResource(R.string.hotkeyOpenAnAppInfo),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                AddButton { viewModel.openPicker(AppPickerMode.OPEN_APP) }
            }
        }
    }
    val appHotkeys = state.hotkeys.filter { it.action == ThorAction.LAUNCH_APP }
    items(appHotkeys, key = { HotkeyList.encode(listOf(it)) }) { hotkey ->
        HotkeyCard(hotkey, state) { viewModel.edit(hotkey) }
    }
}

@Composable
private fun Options(state: HotkeysUiModel, viewModel: HotkeysViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        TextForAll(state.hotkeys, viewModel::setTextForAll)
        SwitchPreference(
            icon = R.drawable.ic_home,
            title = R.string.singlePressHome,
            description = R.string.hotkeysSinglePressHomeInfo,
            state = state.singlePressHome,
            onChange = viewModel::setSinglePressHome,
        )
        TriggerPreference(
            icon = R.drawable.ic_app_settings,
            title = stringResource(R.string.hotkeysOffApps),
            description = if (state.offApps.isEmpty()) {
                stringResource(R.string.hotkeysOffAppsNone)
            } else {
                state.offApps.joinToString { state.app(it)?.name ?: it }
            },
        ) { viewModel.openPicker(AppPickerMode.HOTKEYS_OFF) }
        TriggerPreference(
            icon = R.drawable.ic_more_time,
            title = R.string.hotkeysReset,
            description = R.string.hotkeysResetInfo,
            onClick = viewModel::askReset,
        )
        Text(
            text = stringResource(R.string.hotkeysFootnote),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Composable
fun ListHeading(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
    )
}

private val TWO_PANE_WIDTH = 600.dp
private val RAIL_WIDTH = 232.dp
private val TRIGGER_WIDTH = 168.dp
