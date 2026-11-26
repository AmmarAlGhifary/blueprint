package io.github.ammaralghifary.blueprint.model

import io.github.ammaralghifary.blueprint.Blueprint
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Root of a payload. A plain holder for [body]: Blueprint draws no Scaffold, app bar or dialogs,
 * those belong to the host app (or to components you register).
 *
 * @author Ammar Al Ghifary
 */
@Serializable
public data class SduiScreen(
    /** The `screen_id` the server sent, if any. */
    @SerialName("screen_id") val id: String? = null,
    val body: SduiComponent,
    /** The payload version the server declared. It is kept as sent; Blueprint does not act on it. */
    val version: Int? = null,
    /**
     * The `schema_version` the server sent: the major version of the payload schema it was written
     * for. Null when it is absent (read as 1) or unreadable. A value outside
     * [Blueprint.supportedSchemaVersions] is kept here and reported while decoding.
     */
    @SerialName("schema_version") val schemaVersion: Int? = null,
)
