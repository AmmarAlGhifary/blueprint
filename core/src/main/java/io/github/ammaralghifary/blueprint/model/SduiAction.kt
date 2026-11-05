package io.github.ammaralghifary.blueprint.model

import kotlinx.serialization.Serializable

/**
 * Base type of every action a component can carry. Open for the same reason as [SduiComponent].
 *
 * @author Ammar Al Ghifary
 */
@Serializable
public abstract class SduiAction

/**
 * A simple navigation request. Register it under whatever `type` string your server sends.
 *
 * @author Ammar Al Ghifary
 */
@Serializable
public data class NavigateAction(
    /** Where to go. Blueprint never interprets it: your `onAction` does. */
    val destination: String,
    /** String arguments for [destination]. */
    val params: Map<String, String> = emptyMap(),
) : SduiAction()
