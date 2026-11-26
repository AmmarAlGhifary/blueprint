package io.github.ammaralghifary.blueprint.actions

import io.github.ammaralghifary.blueprint.model.SduiAction
import io.github.ammaralghifary.blueprint.registry.SduiRegistryBuilder
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive

/**
 * `open_url`: asks the host to open [url]. Blueprint never opens it: your [SduiActionHandler] does,
 * after checking that it is a URL your app is willing to open.
 *
 * [toString] leaves the URL out, since it can carry tokens.
 *
 * @author Ammar Al Ghifary
 */
@Serializable
public data class OpenUrlAction(
    /** The URL as the server sent it, unchecked. */
    val url: String,
) : SduiAction {
    override fun toString(): String = "OpenUrlAction"
}

/**
 * `submit`: asks the host to send [params] to [target]. Blueprint never sends anything itself, and
 * [target] means only what your [SduiActionHandler] makes of it.
 *
 * [toString] gives the number of params and no payload text.
 *
 * @author Ammar Al Ghifary
 */
@Serializable
public data class SubmitAction(
    /** What to submit to, for example an endpoint name your app knows. */
    val target: String,
    /** String arguments for [target]. */
    val params: Map<String, String> = emptyMap(),
) : SduiAction {
    override fun toString(): String = "SubmitAction(params=${params.size})"
}

/**
 * `set_state`: stores [value] under [key] in the [SduiActionController], or removes the key when
 * [value] is null. Custom components read it with `stateValue(key)` on
 * [io.github.ammaralghifary.blueprint.render.LocalSduiActionContext]; no stock component does.
 *
 * Your [SduiActionHandler] is asked first. The controller writes the value only when the handler
 * returns [ActionResult.NotHandled].
 *
 * [toString] contains neither the key nor the value.
 *
 * @author Ammar Al Ghifary
 */
@Serializable
public data class SetStateAction(
    /** A flat key; there is no nesting. */
    val key: String,
    /** A JSON string, number or boolean, or null to remove the key. The payload must send it. */
    val value: JsonPrimitive?,
) : SduiAction {
    override fun toString(): String = "SetStateAction"
}

/**
 * Registers [OpenUrlAction] as `open_url`, [SubmitAction] as `submit` and [SetStateAction] as
 * `set_state`. `navigate` is not included; the stock pack in `blueprint-defaults` registers it. Like
 * any registration, a type that is already taken throws.
 */
public fun SduiRegistryBuilder.sduiActions() {
    action<OpenUrlAction>("open_url")
    action<SubmitAction>("submit")
    action<SetStateAction>("set_state")
}
