package io.github.ammaralghifary.blueprint.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Root of a payload. A plain holder for [body]: Blueprint draws no Scaffold, app bar or dialogs,
 * those belong to the host app (or to components you register).
 */
@Serializable
public data class SduiScreen(
    /** The `screen_id` the server sent, if any. */
    @SerialName("screen_id") val id: String? = null,
    val body: SduiComponent,
    /** The payload version the server declared. It is kept as sent; Blueprint does not act on it. */
    val version: Int? = null,
)
