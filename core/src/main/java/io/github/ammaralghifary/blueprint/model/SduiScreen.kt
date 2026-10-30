package io.github.ammaralghifary.blueprint

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Root of a payload. A plain holder for [body]: Blueprint draws no Scaffold, app bar or dialogs,
 * those belong to the host app (or to components you register).
 */
@Serializable
public data class SduiScreen(
    @SerialName("screen_id") val id: String? = null,
    val body: SduiComponent,
    val version: Int? = null,
)
