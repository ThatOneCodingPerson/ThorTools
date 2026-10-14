package io.github.thatonecodingperson.thortools.retroarch

import android.content.Context
import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.automirrored.rounded.Shortcut
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.AddCircleOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.LibraryAdd
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Looks3
import androidx.compose.material.icons.rounded.Looks4
import androidx.compose.material.icons.rounded.Looks5
import androidx.compose.material.icons.rounded.Looks6
import androidx.compose.material.icons.rounded.LooksOne
import androidx.compose.material.icons.rounded.LooksTwo
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.thatonecodingperson.thortools.R
import io.github.thatonecodingperson.thortools.hotkeys.NoteCard
import io.github.thatonecodingperson.thortools.ui.composables.CardCheckRow
import io.github.thatonecodingperson.thortools.ui.composables.CardColumns
import io.github.thatonecodingperson.thortools.ui.composables.CardHeader
import io.github.thatonecodingperson.thortools.ui.composables.CardRow
import io.github.thatonecodingperson.thortools.ui.composables.CardRowBox
import io.github.thatonecodingperson.thortools.ui.composables.CardSwitchRow
import io.github.thatonecodingperson.thortools.ui.composables.CreditNote
import io.github.thatonecodingperson.thortools.ui.composables.SettingsCard
import io.github.thatonecodingperson.thortools.ui.composables.cardChipColors

/**
 * Shaders: the guide on how they work in RetroArch, getting libretro's shaders sorted into RetroArch's own Shaders menu,
 * the console folders, and the sorted folders with their shortcuts.
 */
@Composable
internal fun ShadersTab(state: RaUiModel, viewModel: RetroArchViewModel) {
    if (state.shaders.guide) {
        ShaderGuide(state, viewModel)
        return
    }
    val format = state.shaderFormat
    val scan = state.shaders.scan
    CardColumns(
        padding = PaddingValues(),
        left = {
            StatusNotes(state, viewModel)
            Text(
                stringResource(R.string.raShaderIntro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SettingsCard(contentPadding = PaddingValues()) {
                CardRow(
                    title = stringResource(R.string.raShaderGuideOpen),
                    info = stringResource(R.string.raShaderGuideOpenInfo),
                    icon = Icons.AutoMirrored.Rounded.MenuBook,
                    onClick = { viewModel.showShaderGuide(true) },
                )
            }
            when {
                format == null -> NoteCard(
                    stringResource(R.string.raShaderNoFormat, state.config?.value(RaUiModel.VIDEO_DRIVER) ?: "?"),
                    caution = true,
                )
                scan == null -> NoteCard(
                    stringResource(if (state.shaders.loaded) R.string.raShaderScanFailed else R.string.raShaderLooking),
                )
                else -> {
                    GetCard(state, format, scan, viewModel)
                    ConsolesCard(state, viewModel)
                    if (scan.oldLooks.isNotEmpty()) {
                        NoteCard(
                            stringResource(
                                R.string.raShaderOldLooks,
                                scan.oldLooks.joinToString(", ") { it.substringBeforeLast('/').substringAfterLast('/') },
                            ),
                            caution = true,
                            actionLabel = stringResource(R.string.raShaderOldLooksRemove),
                            onAction = viewModel::removeOldLooks,
                        )
                    }
                }
            }
            ShaderNoteCards(state.shaders.note)
            ResultNote(state.note)
            CreditNote(stringResource(R.string.raShaderCredit), stringResource(R.string.raShaderCreditLink), SHADERS_LINK)
        },
        right = {
            if (format != null && scan != null) {
                if (scan.libraryReady) LinkCard(state, format, viewModel)
                ChosenCard(state, scan, viewModel)
                YourShadersCard(state, scan, viewModel)
            }
        },
    )
}

/** Getting the shaders into RetroArch's menu: what will happen or what is there, how far it got, and the menu's folder. */
@Composable
private fun GetCard(state: RaUiModel, format: ShaderFormat, scan: ShaderScan, viewModel: RetroArchViewModel) {
    val context = LocalContext.current
    val shaders = state.shaders
    val places = state.found?.let { found -> state.config?.let { ShaderPlaces.of(it, found.packageName) } }
    val ready = scan.libraryReady
    SettingsCard(contentPadding = PaddingValues()) {
        CardHeader(
            Icons.Rounded.LibraryAdd,
            stringResource(R.string.raShaderGetTitle),
            when {
                ready -> stringResource(
                    R.string.raShaderReady,
                    scan.shortcuts,
                    ShaderShortcuts.byFolder(scan.presets.filter { it.ours }).size,
                    places?.root.orEmpty(),
                )
                scan.privateFiles > 0 -> stringResource(R.string.raShaderGetCopyInfo, format.pack)
                else -> stringResource(R.string.raShaderGetDownloadInfo)
            },
        )
        val progress = shaders.progress
        val step = shaders.step
        when {
            progress != null -> CardRowBox {
                val (done, total) = progress
                Muted(
                    if (total > 0) {
                        stringResource(R.string.raShaderDownloading, size(context, done), size(context, total))
                    } else {
                        stringResource(R.string.raShaderDownloadingUnknown, size(context, done))
                    },
                )
                LinearProgressIndicator(
                    progress = { if (total > 0) done.toFloat() / total else 0f },
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                )
            }
            step != null -> CardRowBox { Muted(stringResource(R.string.raShaderCopying, step.first, step.second)) }
            shaders.working -> CardRowBox { Muted(stringResource(R.string.raShaderWorking)) }
        }
        if (!ready) {
            CardRow(
                title = stringResource(R.string.raShaderGet),
                icon = Icons.Rounded.Download,
                enabled = !shaders.working && state.found?.rootWorks == true,
                onClick = viewModel::getShaders,
            )
        } else {
            if (places != null && !places.menuOnStorage) {
                CardRow(
                    title = stringResource(R.string.raShaderPoint),
                    info = stringResource(R.string.raShaderPointInfo, places.own, places.root),
                    enabled = state.canChange,
                    onClick = viewModel::pointShaderMenu,
                )
            }
            CardRow(
                title = stringResource(R.string.raShaderSortAgain),
                info = stringResource(R.string.raShaderSortAgainInfo),
                icon = Icons.AutoMirrored.Rounded.Sort,
                enabled = !shaders.working,
                onClick = viewModel::sortShadersAgain,
            )
        }
        if (state.config?.flag(RaUiModel.SHADERS_ON) == false) {
            CardSwitchRow(
                title = stringResource(R.string.raShaderOn),
                info = stringResource(R.string.raShaderOnInfo),
                checked = false,
                enabled = state.canChange,
                onChange = viewModel::setShadersOn,
            )
        }
        shaders.pack?.let { pack ->
            CardRow(
                title = stringResource(R.string.raShaderPackDelete),
                info = stringResource(R.string.raShaderPackDeleteInfo, size(context, pack.size)),
                danger = true,
                enabled = !shaders.working,
                onClick = viewModel::deleteShaderPack,
            )
        }
    }
}

/** Which console folders are made: the cores' consoles, or every console with shaders made for it. */
@Composable
private fun ConsolesCard(state: RaUiModel, viewModel: RetroArchViewModel) {
    val all = state.shaders.allConsoles
    val consoles = ShaderShortcuts.consoles(state.cheats.side?.cores.orEmpty(), all)
    SettingsCard(contentPadding = PaddingValues()) {
        CardHeader(
            Icons.Rounded.Folder,
            stringResource(R.string.raShaderConsoles),
            if (consoles.isEmpty()) {
                stringResource(R.string.raShaderConsolesNone)
            } else {
                stringResource(R.string.raShaderConsolesInfo, consoles.joinToString(", ") { ShaderShortcuts.consoleFolder(it) })
            },
        )
        CardSwitchRow(
            title = stringResource(R.string.raShaderAllConsoles),
            info = stringResource(R.string.raShaderAllConsolesInfo, ShaderCatalog.systems.size),
            checked = all,
            enabled = !state.shaders.working,
            onChange = viewModel::setAllShaderConsoles,
        )
    }
}

/** Shaders from a link: the link typed and read, its presets by where they go, and adding the ticked ones. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LinkCard(state: RaUiModel, format: ShaderFormat, viewModel: RetroArchViewModel) {
    val shaders = state.shaders
    val scan = shaders.linkScan
    val busy = shaders.linkBusy || shaders.working
    SettingsCard(contentPadding = PaddingValues()) {
        CardHeader(Icons.Rounded.Link, stringResource(R.string.raShaderLinkTitle), stringResource(R.string.raShaderLinkInfo))
        CardRowBox {
            OutlinedTextField(
                value = shaders.linkText,
                onValueChange = viewModel::setLinkText,
                singleLine = true,
                placeholder = { Text(stringResource(R.string.raShaderLinkHint)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { viewModel.lookLink() }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        CardRow(
            title = stringResource(R.string.raShaderLinkLook),
            icon = Icons.Rounded.Search,
            enabled = shaders.linkText.isNotBlank() && !busy && state.found?.rootWorks == true,
            onClick = { viewModel.lookLink() },
        )
        if (shaders.linkBusy) CardRowBox { Muted(stringResource(R.string.raShaderLinkBusy)) }
        if (scan == null) {
            shaders.links.filter { it != shaders.linkText.trim() }.take(LINKS_SHOWN).forEach { link ->
                CardRow(
                    title = link.removePrefix("https://"),
                    info = stringResource(R.string.raShaderLinkAgain),
                    enabled = !busy,
                    onClick = { viewModel.lookLink(link) },
                )
            }
        } else {
            LinkPresets(state, scan, format, busy, viewModel)
        }
    }
}

/** A link's presets grouped by the folders they go to: ticking a group or one, changing a group's folder, adding. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LinkPresets(state: RaUiModel, scan: LinkScan, format: ShaderFormat, busy: Boolean, viewModel: RetroArchViewModel) {
    val shaders = state.shaders
    val driver = state.config?.value(RaUiModel.VIDEO_DRIVER) ?: "?"
    CardRowBox {
        Muted(
            pluralStringResource(
                R.plurals.raShaderLinkFound,
                scan.presets.size,
                scan.presets.size,
                scan.link.label,
                scan.presets.count { it.usable },
                format.preset,
            ),
        )
        if (scan.truncated) Warning(stringResource(R.string.raShaderLinkTruncated))
    }
    val linkFolder = "${ShaderShortcuts.LINKS}/${ShaderShortcuts.safe(scan.link.label)}"
    val consoles = (ShaderCatalog.systems + playedConsoles(state)).distinct()
    val choices = ShaderShortcuts.folders(consoles) + linkFolder
    scan.presets.groupBy {
        it.group
    }.toList().sortedBy { (group, _) -> group.startsWith(ShaderShortcuts.LINKS) }.forEach { (group, presets) ->
        val usable = presets.filter { it.usable && !it.added }
        CardCheckRow(
            title = stringResource(R.string.raShaderLinkGroup, group.replace("/", " › ")),
            info = pluralStringResource(R.plurals.raShaderLinkGroupCount, presets.size, presets.size),
            checked = usable.isNotEmpty() && usable.all { it.ticked },
            enabled = usable.isNotEmpty() && !busy,
            onChange = { on -> viewModel.tickLinkGroup(group, on) },
        )
        CardRow(
            title = stringResource(R.string.raShaderLinkChange),
            enabled = !busy,
            onClick = { viewModel.chooseLinkGroup(group.takeUnless { shaders.linkGroup == it }) },
        )
        if (shaders.linkGroup == group) {
            CardRowBox {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    choices.filterNot { it == group }.forEach { folder ->
                        FilterChip(
                            selected = false,
                            onClick = { viewModel.moveLinkGroup(group, folder) },
                            label = { Text(folderLabel(folder)) },
                            colors = cardChipColors(),
                        )
                    }
                }
            }
        }
        presets.take(GROUP_SHOWN).forEach { preset ->
            CardCheckRow(
                title = preset.name,
                info = when {
                    !preset.usable -> stringResource(R.string.raShaderLinkOtherLanguage, preset.path.substringAfterLast('.'), driver)
                    preset.added -> stringResource(
                        R.string.raShaderLinkAlready,
                        preset.already.joinToString(", ") { it.substringBeforeLast('.').replace("/", " › ") },
                    )
                    preset.onThor -> stringResource(R.string.raShaderLinkOnThor, preset.path)
                    else -> preset.path
                },
                checked = preset.ticked && preset.usable,
                enabled = preset.usable && !busy,
                onChange = { on -> viewModel.tickLinkPreset(preset.path, on) },
            )
        }
        if (presets.size > GROUP_SHOWN) CardRowBox { Muted(stringResource(R.string.raShaderLinkMore, presets.size - GROUP_SHOWN)) }
    }
    CardRow(
        title = if (scan.ticked.isEmpty()) {
            stringResource(R.string.raShaderLinkTickSome)
        } else {
            pluralStringResource(R.plurals.raShaderLinkAdd, scan.ticked.size, scan.ticked.size)
        },
        info = stringResource(R.string.raShaderLinkAddInfo),
        icon = Icons.Rounded.AddCircleOutline,
        enabled = scan.ticked.isNotEmpty() && !busy,
        onClick = viewModel::addLinkShaders,
    )
}

/** The consoles the installed cores play, as the folders link shaders may go to. */
private fun playedConsoles(state: RaUiModel): List<CheatSystem> {
    val cores = state.cheats.side?.cores.orEmpty()
    return CheatSystem.entries.filter { system -> system.db != null && cores.any { system.db in it.databases } }
}

/** The shortcut chosen in "Your shaders": where it is and points, moving it to another folder, removing it. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChosenCard(state: RaUiModel, scan: ShaderScan, viewModel: RetroArchViewModel) {
    val chosen = scan.presets.firstOrNull { it.path == state.shaders.chosen } ?: return
    val consoles = ShaderShortcuts.consoles(state.cheats.side?.cores.orEmpty(), state.shaders.allConsoles)
    val folders = ShaderShortcuts.folders(consoles).filterNot { it.equals(chosen.folder, ignoreCase = true) }
    SettingsCard(contentPadding = PaddingValues()) {
        CardHeader(
            Icons.AutoMirrored.Rounded.Shortcut,
            chosen.name,
            stringResource(R.string.raShaderChosenInfo, folderLabel(chosen.folder), pointsAt(state, chosen.target)),
        )
        CardRowBox {
            Text(
                stringResource(R.string.raShaderMoveTo),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 6.dp),
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                folders.forEach { folder ->
                    FilterChip(
                        selected = false,
                        enabled = !state.shaders.working,
                        onClick = { viewModel.moveShortcut(folder) },
                        label = { Text(folderLabel(folder)) },
                        colors = cardChipColors(),
                    )
                }
            }
        }
        CardRow(
            title = stringResource(R.string.raShaderRemove),
            info = stringResource(
                if (ShaderShortcuts.linkKey(chosen.target) != null) R.string.raShaderRemoveLinkInfo else R.string.raShaderRemoveInfo,
            ),
            danger = true,
            enabled = !state.shaders.working,
            onClick = viewModel::removeShortcut,
        )
        CardRow(title = stringResource(R.string.raShaderDone), onClick = { viewModel.chooseShortcut(null) })
    }
}

/** Where a shortcut's [target] is, in words: libretro's shaders, or the link it came from. */
@Composable
private fun pointsAt(state: RaUiModel, target: String?): String {
    val path = target.orEmpty()
    val key = ShaderShortcuts.linkKey(path)
    val pack = state.shaderFormat?.pack
    return when {
        key != null -> {
            val label = state.shaders.links.mapNotNull(ShaderLinks::parse).firstOrNull { it.key == key }?.label ?: key
            stringResource(R.string.raShaderFromLink, label, path.removePrefix("${ShaderShortcuts.LINKS_LIBRARY}/$key/"))
        }
        pack != null && path.startsWith("$pack/") -> stringResource(R.string.raShaderFromLibrary, path.removePrefix("$pack/"))
        else -> path
    }
}

/** The sorted folders as RetroArch's Shaders menu shows them; a folder opens to its shaders, a shortcut can be chosen. */
@Composable
private fun YourShadersCard(state: RaUiModel, scan: ShaderScan, viewModel: RetroArchViewModel) {
    val shaders = state.shaders
    val folders = ShaderShortcuts.byFolder(scan.presets)
    SettingsCard(contentPadding = PaddingValues()) {
        CardHeader(Icons.Rounded.FolderOpen, stringResource(R.string.raShaderYours), stringResource(R.string.raShaderYoursInfo))
        if (folders.isEmpty()) CardRowBox { Muted(stringResource(R.string.raShaderYoursNone)) }
        folders.forEach { (folder, presets) ->
            val open = shaders.openFolder == folder
            CardRow(
                title = folderLabel(folder),
                info = pluralStringResource(R.plurals.raShaderFolderCount, presets.size, presets.size),
                icon = if (open) Icons.Rounded.FolderOpen else Icons.Rounded.Folder,
                onClick = { viewModel.openShaderFolder(folder.takeUnless { open }) },
            )
            if (open) {
                presets.forEach { preset ->
                    CardRow(
                        title = preset.name,
                        info = if (preset.ours) {
                            pointsAt(state, preset.target)
                        } else {
                            stringResource(R.string.raShaderNotOurs)
                        },
                        value = stringResource(R.string.raShaderChosen).takeIf { shaders.chosen == preset.path },
                        enabled = preset.ours,
                        onClick = { viewModel.chooseShortcut(preset.path) },
                    )
                }
            }
        }
    }
}

/** The step-by-step guide to shaders in RetroArch, with this Thor's own driver, hotkey and folders. */
@Composable
private fun ShaderGuide(state: RaUiModel, viewModel: RetroArchViewModel) {
    val format = state.shaderFormat
    val driver = state.config?.value(RaUiModel.VIDEO_DRIVER) ?: "?"
    val language = when (format) {
        ShaderFormat.SLANG -> stringResource(R.string.raShaderLanguageSlang)
        ShaderFormat.GLSL -> stringResource(R.string.raShaderLanguageGlsl)
        null -> stringResource(R.string.raShaderLanguageNone)
    }
    val enable = state.enable
    val menu = (state.binding(RaKey.Of(RaHotkey.MENU)) as? RaBinding.Thor)?.button
    val hotkey = if (enable != null && menu != null) {
        stringResource(R.string.raShaderGuideHotkey, stringResource(enable.keyLabel), stringResource(menu.keyLabel))
    } else {
        stringResource(R.string.raShaderGuideNoHotkey)
    }
    val root = state.found?.let { found -> state.config?.let { ShaderPlaces.of(it, found.packageName).root } }.orEmpty()
    val auto = stringResource(
        if (state.config?.flag(RaUiModel.AUTO_SHADERS) == false) R.string.raShaderGuideAutoOff else R.string.raShaderGuideAutoOn,
    )
    CardColumns(
        padding = PaddingValues(),
        left = {
            SettingsCard(contentPadding = PaddingValues()) {
                CardRow(
                    title = stringResource(R.string.raShaderGuideBack),
                    icon = Icons.AutoMirrored.Rounded.ArrowBack,
                    onClick = { viewModel.showShaderGuide(false) },
                )
            }
            GuideStep(
                Icons.Rounded.LooksOne,
                stringResource(R.string.raShaderGuide1Title),
                stringResource(R.string.raShaderGuide1, driver, language),
            )
            GuideStep(
                Icons.Rounded.LooksTwo,
                stringResource(R.string.raShaderGuide2Title),
                stringResource(R.string.raShaderGuide2, root, format?.pack ?: ShaderFormat.SLANG.pack),
            )
            GuideStep(Icons.Rounded.Looks3, stringResource(R.string.raShaderGuide3Title), stringResource(R.string.raShaderGuide3, hotkey))
        },
        right = {
            GuideStep(Icons.Rounded.Looks4, stringResource(R.string.raShaderGuide4Title), stringResource(R.string.raShaderGuide4, auto))
            GuideStep(Icons.Rounded.Looks5, stringResource(R.string.raShaderGuide5Title), stringResource(R.string.raShaderGuide5))
            GuideStep(Icons.Rounded.Looks6, stringResource(R.string.raShaderGuide6Title), stringResource(R.string.raShaderGuide6))
        },
    )
}

@Composable
private fun GuideStep(icon: ImageVector, title: String, text: String) {
    SettingsCard(contentPadding = PaddingValues()) {
        CardHeader(icon, title, text)
    }
}

@Composable
private fun ShaderNoteCards(note: ShaderNote?) {
    when (note) {
        null -> Unit
        is ShaderNote.Ready -> NoteCard(stringResource(R.string.raShaderNoteReady, note.shortcuts))
        is ShaderNote.Updated -> NoteCard(stringResource(R.string.raShaderNoteUpdated, note.added, note.removed))
        is ShaderNote.Moved -> NoteCard(stringResource(R.string.raShaderNoteMoved, folderLabel(note.folder)))
        ShaderNote.Removed -> NoteCard(stringResource(R.string.raShaderNoteRemoved))
        ShaderNote.OldLooksRemoved -> NoteCard(stringResource(R.string.raShaderNoteOldLooks))
        ShaderNote.ScanFailed -> NoteCard(stringResource(R.string.raShaderScanFailed), warning = true)
        is ShaderNote.Failed -> NoteCard(stringResource(R.string.raShaderFailed, note.detail), warning = true)
        ShaderNote.NotALink -> NoteCard(stringResource(R.string.raShaderNotALink), caution = true)
        ShaderNote.NoPresets -> NoteCard(stringResource(R.string.raShaderNoPresets), caution = true)
        is ShaderNote.LinkAdded -> {
            val added = stringResource(
                R.string.raShaderNoteLinkAdded,
                pluralStringResource(R.plurals.raShaderFolderCount, note.shortcuts, note.shortcuts),
                pluralStringResource(R.plurals.raShaderFolders, note.folders, note.folders),
            )
            if (note.missing == 0) {
                NoteCard(added)
            } else {
                NoteCard("$added ${pluralStringResource(R.plurals.raShaderNoteLinkMissing, note.missing, note.missing)}", caution = true)
            }
        }
        is ShaderNote.Problem -> NoteCard(
            when (val problem = note.problem) {
                is CheatProblem.Offline -> stringResource(R.string.raShaderOffline)
                is CheatProblem.Http -> if (problem.code == NOT_FOUND) {
                    stringResource(R.string.raShaderLinkNotFound)
                } else {
                    stringResource(R.string.raCheatHttp, problem.code)
                }
                is CheatProblem.RateLimited -> stringResource(R.string.raCheatRateLimited)
                else -> stringResource(R.string.raShaderFailed, problem.message.orEmpty())
            },
            warning = true,
        )
    }
}

/** A folder as RetroArch's menu leads to it: "Any console › CRT TVs". */
private fun folderLabel(folder: String): String = folder.replace("/", " › ")

private fun size(context: Context, bytes: Long): String = Formatter.formatShortFileSize(context, bytes)

@Composable
private fun Muted(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun Warning(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
}

private const val SHADERS_LINK = "https://github.com/libretro/slang-shaders"

/** Links added before, shown to look at again. */
private const val LINKS_SHOWN = 3

/** Presets shown per group of a link; the rest are ticked with the group. */
private const val GROUP_SHOWN = 20

private const val NOT_FOUND = 404
