package io.github.ammaralghifary.blueprint.render

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import io.github.ammaralghifary.blueprint.model.ActionStatus
import io.github.ammaralghifary.blueprint.model.SduiAction
import kotlinx.serialization.json.JsonPrimitive

/**
 * What a component may read about actions while it draws. `blueprint-actions` provides an
 * implementation; a host with its own action handling can implement it and provide it through
 * [LocalSduiActionContext].
 *
 * Both reads should be backed by Compose snapshot state, so that a composable calling them recomposes
 * when the answer changes.
 *
 * @author Ammar Al Ghifary
 */
public interface SduiActionContext {
    /** The status of [action]. Actions that are equal share one status. */
    public fun statusOf(action: SduiAction): ActionStatus

    /** The state stored under [key], or null when there is none. The value is payload text. */
    public fun stateValue(key: String): JsonPrimitive?
}

/**
 * The [SduiActionContext] of the surrounding tree, or null (the default) when nobody provides one, in
 * which case every component behaves as if actions had no status.
 */
public val LocalSduiActionContext: ProvidableCompositionLocal<SduiActionContext?> = staticCompositionLocalOf { null }
