package io.github.thatonecodingperson.thortools.hotkeys

import io.github.thatonecodingperson.thortools.actions.ThorAction
import io.github.thatonecodingperson.thortools.hotkeys.HotkeyRecognizer.Effect
import io.github.thatonecodingperson.thortools.panel.PanelTiles

/**
 * How the hotkeys share the buttons with the open quick panel. Pure: the service asks [answersHome]
 * before a key reaches the recognizer, and passes the recognizer's effects through [route]. Game buttons need no rule:
 * they are never held back on their own, so A, B, L1 and R1 reach the panel unless they are a combo's second button.
 */
object PanelKeys {
    /** The open panel, as far as routing goes. */
    data class Panel(val hasController: Boolean, val openedFromTop: Boolean)

    sealed interface Out {
        /** Carried out as with the panel closed; an action the panel shows the result of leaves it open. */
        data class Hotkeys(val effect: Effect) : Out

        /**
         * Close the panel first (the controller goes back where it was), then carry out [effect] [delayMs] later, as
         * the panel's own tile for that action does.
         */
        data class AfterClosing(val effect: Effect, val delayMs: Long) : Out

        /** The panel's own Back: back inside the panel, or close it. */
        data object PanelBack : Out

        /** Home from the panel: close it and go Home on the screen it was opened from. */
        data object PanelHome : Out
    }

    /**
     * Whether the open panel answers a Home press itself because no hotkey takes Home: Android would otherwise go Home
     * on the panel's own screen, behind the panel.
     */
    fun answersHome(button: PadButton, panelHasController: Boolean, hotkeysTakeHome: Boolean): Boolean =
        button == PadButton.HOME && panelHasController && !hotkeysTakeHome

    /**
     * What the hotkeys' effects do while the panel is open ([panel] null: it is closed, nothing changes).
     *
     * With the controller, a single Back is the panel's Back rather than a press given back (which would go to the
     * last touched screen, not necessarily the panel's): a Back given back, Back's own tap hotkey, and any hotkey whose
     * action is Back. A Home given back closes the panel and goes Home where it was opened from.
     *
     * Every other action runs the way the panel's own tile for it does ([leaveDelayMs]): one that leaves the panel
     * (Home, controller moves, screens, apps, system screens) closes it first, with "here" pinned to the screen it was
     * opened from ([pinHere]); levels and modes run with the panel open. The AYN drawer opens after the panel closes.
     */
    fun route(effects: List<Effect>, panel: Panel?): List<Out> {
        if (panel == null) return effects.map(Out::Hotkeys)
        return effects.flatMap { effect ->
            when {
                panel.hasController && effect is Effect.GiveBack && effect.button == PadButton.BACK -> listOfNotNull(
                    Out.PanelBack,
                    Out.Hotkeys(Effect.GiveBack(PadButton.BACK, effect.presses - 1)).takeIf { effect.presses > 1 },
                )
                panel.hasController && effect is Effect.Run && effect.hotkey.isPanelBack() -> listOf(Out.PanelBack)
                effect is Effect.GiveBack && effect.button == PadButton.HOME -> listOf(Out.PanelHome)
                effect is Effect.GiveBack && effect.button == PadButton.AYN -> listOf(Out.AfterClosing(effect, PanelTiles.LEAVE_MS))
                effect is Effect.Run -> {
                    val delay = leaveDelayMs(effect.hotkey.action)
                    if (delay == null) {
                        listOf(Out.Hotkeys(effect))
                    } else {
                        listOf(Out.AfterClosing(Effect.Run(pinHere(effect.hotkey, panel.openedFromTop)), delay))
                    }
                }
                else -> listOf(Out.Hotkeys(effect))
            }
        }
    }

    /**
     * How long after the panel closes a hotkey's [action] runs, as the panel's tile for it does; null when it runs with
     * the panel open. Opening an app (no tile) leaves like the others; the quick panel action toggles it closed itself.
     */
    fun leaveDelayMs(action: ThorAction): Long? = when (action) {
        ThorAction.LAUNCH_APP -> PanelTiles.LEAVE_MS
        else -> PanelTiles.leaveDelayMs(action.id)
    }

    /**
     * Once the panel has closed, the controller's screen is on its way back and can't be read yet; so "here" is decided
     * before closing, as the screen the panel was opened from. Plain Home becomes Home on that screen.
     */
    fun pinHere(hotkey: Hotkey, openedFromTop: Boolean): Hotkey {
        val here = if (openedFromTop) LaunchScreen.TOP else LaunchScreen.BOTTOM
        return when (hotkey.action) {
            ThorAction.HOME -> hotkey.copy(action = if (openedFromTop) ThorAction.HOME_TOP else ThorAction.HOME_BOTTOM)
            ThorAction.CLOSE_APP -> if (CloseAppArg.decode(hotkey.arg) == LaunchScreen.HERE) {
                hotkey.copy(arg = CloseAppArg.encode(here))
            } else {
                hotkey
            }
            ThorAction.LAUNCH_APP -> AppLaunch.decode(hotkey.arg)
                ?.takeIf { it.screen == LaunchScreen.HERE }
                ?.let { hotkey.copy(arg = it.copy(screen = here).encode()) }
                ?: hotkey
            else -> hotkey
        }
    }

    private fun Hotkey.isPanelBack() = action == ThorAction.BACK || (button == PadButton.BACK && second == null && press == PressKind.TAP)
}
