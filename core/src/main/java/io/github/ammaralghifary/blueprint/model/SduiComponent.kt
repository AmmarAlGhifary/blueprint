package io.github.ammaralghifary.blueprint

import kotlinx.serialization.Serializable

/**
 * Base type of every node in a Blueprint tree.
 *
 * Intentionally *not* sealed: components are registered by type string in a [SduiRegistry], so
 * host apps and libraries can add their own subclasses. Subclasses should be `@Serializable`.
 */
@Serializable
public abstract class SduiComponent {
    /** Box-model description sent by the server; applied by the renderer before your composable runs. */
    public abstract val modifier: SduiModifier?
}
