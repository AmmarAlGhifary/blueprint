package io.github.ammaralghifary.blueprint.model

import kotlinx.serialization.Serializable

/** Base type of every action a component can carry. Open for the same reason as [SduiComponent]. */
@Serializable
public abstract class SduiAction

/** A simple navigation request. Register it under whatever `type` string your server sends. */
@Serializable
public data class NavigateAction(
    val destination: String,
    val params: Map<String, String> = emptyMap(),
) : SduiAction()
