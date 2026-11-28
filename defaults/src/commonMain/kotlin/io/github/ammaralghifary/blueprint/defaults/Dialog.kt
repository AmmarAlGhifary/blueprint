package io.github.ammaralghifary.blueprint.defaults

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.DialogProperties
import io.github.ammaralghifary.blueprint.model.ActionStatus
import io.github.ammaralghifary.blueprint.model.SduiAction
import io.github.ammaralghifary.blueprint.model.SduiComponent
import io.github.ammaralghifary.blueprint.model.SduiModifier
import io.github.ammaralghifary.blueprint.render.LocalSduiActionContext
import io.github.ammaralghifary.blueprint.render.SduiScope
import kotlinx.coroutines.delay
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * `dialog`: a modal Material 3 alert with a [primary] button and an optional [secondary] one. A server can
 * send it, or the app can build it and show it with [BlueprintDialog].
 *
 * A button without an action closes the dialog. A button with one dispatches it and closes the dialog,
 * unless [io.github.ammaralghifary.blueprint.render.LocalSduiActionContext] tracks it: then the button is
 * disabled while the action is running, and the dialog closes when it stops running, but stays open if it
 * failed, so the user can try again.
 *
 * When [dismissible], a tap outside or a back press also closes it and dispatches [onDismiss]; otherwise
 * only the buttons close it.
 *
 * In a payload it is shown while the node is there and takes no space in place. Once closed it stays closed
 * for an equal dialog at the same position. [modifier] is not used: a dialog is not laid out in the screen.
 *
 * @author Ammar Al Ghifary
 */
@Serializable
public data class SduiDialog(
    val title: String? = null,
    val message: String? = null,
    val primary: SduiDialogButton,
    val secondary: SduiDialogButton? = null,
    val dismissible: Boolean = true,
    @SerialName("on_dismiss") val onDismiss: SduiAction? = null,
    override val modifier: SduiModifier? = null,
) : SduiComponent

/**
 * A button of a [SduiDialog]. [disabledSeconds] keeps it disabled that long from when the dialog appears,
 * with a countdown after the label, for example while a server asks the app to wait before retrying. Zero or
 * less means no wait, and more than 600 counts as 600.
 *
 * @author Ammar Al Ghifary
 */
@Serializable
public data class SduiDialogButton(
    val label: String,
    val action: SduiAction? = null,
    @SerialName("disabled_seconds") val disabledSeconds: Int? = null,
)

private const val MAX_DISABLED_SECONDS = 600

/**
 * Shows [dialog]. It needs no `Blueprint`, registry or payload, so an app can build one itself, for example
 * for an offline error, with its own [SduiAction] classes. The behaviour is the one described on
 * [SduiDialog], and the stock `dialog` component is this function.
 *
 * You own the visibility: [onDismissRequest] means the dialog wants to close, and it stays on screen until
 * you stop calling this. [onAction] receives the tapped button's action, or [SduiDialog.onDismiss]. To track
 * actions with a controller, wrap this in its context and hand the action to the controller in [onAction].
 *
 * The countdown, and which action is awaited, belong to one [dialog] value: an unequal value starts afresh.
 * Make your actions data classes or objects, or `remember` the dialog.
 */
@Composable
public fun BlueprintDialog(
    dialog: SduiDialog,
    onDismissRequest: () -> Unit,
    onAction: (SduiAction) -> Unit = {},
) {
    key(dialog) {
        val context = LocalSduiActionContext.current
        val close by rememberUpdatedState(onDismissRequest)
        // Tapped actions whose outcome decides whether the dialog closes.
        var awaited by remember { mutableStateOf(emptySet<SduiAction>()) }
        val onClick = { button: SduiDialogButton ->
            val action = button.action
            if (action == null) {
                close()
            } else {
                onAction(action)
                if (context == null) close() else awaited = awaited + action
            }
        }

        if (context != null) {
            // Decided after a composition, from the status the dispatch above left behind.
            awaited.forEach { action ->
                key(action) {
                    val status = context.statusOf(action)
                    LaunchedEffect(status) {
                        when (status) {
                            ActionStatus.Running -> Unit
                            ActionStatus.Failed -> awaited = awaited - action
                            ActionStatus.Done, ActionStatus.Idle -> {
                                awaited = awaited - action
                                close()
                            }
                        }
                    }
                }
            }
        }

        // Both countdowns start with the dialog, whether or not a button is drawn yet.
        val primaryWait = countdown(dialog.primary.disabledSeconds)
        val secondaryWait = countdown(dialog.secondary?.disabledSeconds)

        AlertDialog(
            onDismissRequest = {
                if (dialog.dismissible) {
                    dialog.onDismiss?.let(onAction)
                    close()
                }
            },
            confirmButton = { DialogButton(dialog.primary, primaryWait, emphasized = true) { onClick(dialog.primary) } },
            dismissButton = dialog.secondary?.let { secondary ->
                { DialogButton(secondary, secondaryWait, emphasized = false) { onClick(secondary) } }
            },
            title = dialog.title?.let { title -> { Text(title) } },
            // Scrolls, so a long message from a server cannot be cut off.
            text = dialog.message?.let { message -> { Text(message, Modifier.verticalScroll(rememberScrollState())) } },
            properties = DialogProperties(
                dismissOnBackPress = dialog.dismissible,
                dismissOnClickOutside = dialog.dismissible,
            ),
        )
    }
}

/** Seconds left of a wait of [seconds], counting down from the first composition; it survives recreation. */
@Composable
private fun countdown(seconds: Int?): Int {
    var left by rememberSaveable { mutableStateOf((seconds ?: 0).coerceIn(0, MAX_DISABLED_SECONDS)) }
    LaunchedEffect(Unit) {
        while (left > 0) {
            delay(1_000)
            left--
        }
    }
    return left
}

@Composable
private fun DialogButton(button: SduiDialogButton, secondsLeft: Int, emphasized: Boolean, onClick: () -> Unit) {
    val running = isRunning(button.action)
    val showProgress = showsProgress(button.action, running)
    val enabled = !running && secondsLeft == 0
    val label: @Composable () -> Unit = {
        if (showProgress) ButtonProgress()
        Text(if (secondsLeft > 0) "${button.label} ($secondsLeft)" else button.label)
    }
    if (emphasized) {
        Button(onClick, enabled = enabled) { label() }
    } else {
        TextButton(onClick, enabled = enabled) { label() }
    }
}

@Composable
internal fun RenderDialog(c: SduiDialog, scope: SduiScope) {
    // Keyed by value, so a different dialog at this position is shown even after this one was closed.
    key(c) {
        var open by rememberSaveable { mutableStateOf(true) }
        if (open) BlueprintDialog(c, onDismissRequest = { open = false }, onAction = scope::dispatch)
    }
}
