package io.github.ammaralghifary.blueprint.sample

import android.content.Context
import android.util.Log

/**
 * Loads the sample screen from `assets/sample_payload.json`.
 *
 * The payload carries two deliberate failures, each of which must stay local: `hologram` is not a
 * registered type, and the `text` in the middle is missing its required `text` field. Everything
 * around them still renders. It also uses the host-specific `schedule_card`.
 *
 * @author Ammar Al Ghifary
 */
object SamplePayload {
    const val FILE_NAME = "sample_payload.json"

    /** The file's text, or an empty object if it cannot be read, so `decodeScreen` shows its error screen instead of crashing. */
    fun read(context: Context): String =
        try {
            context.assets.open(FILE_NAME).bufferedReader().use { it.readText() }
        } catch (e: java.io.IOException) {
            Log.w("Blueprint", "could not read $FILE_NAME: ${e.javaClass.simpleName}")
            "{}"
        }
}
