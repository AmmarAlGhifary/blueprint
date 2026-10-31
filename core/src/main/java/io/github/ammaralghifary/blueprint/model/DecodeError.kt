package io.github.ammaralghifary.blueprint.model

import io.github.ammaralghifary.blueprint.Blueprint
import io.github.ammaralghifary.blueprint.registry.SduiRegistry
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.MissingFieldException
import kotlinx.serialization.descriptors.PolymorphicKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.json.JsonObject

/** What [Blueprint] tolerated instead of failing the screen. */
public enum class DecodeErrorKind {
    /** The node's `type` is not in the registry, or is missing. Expected on an older app. */
    UnregisteredType,

    /** A registered component whose fields do not fit its model; it became an [UnknownComponent]. */
    InvalidComponent,

    /** A registered action whose fields do not fit its model; it became an [UnknownAction]. */
    InvalidAction,

    /** A registered component kept, but its malformed `modifier` was dropped. */
    ModifierIgnored,

    /** The screen itself was unreadable (bad JSON, missing body, too deep) or its metadata was dropped. */
    InvalidPayload,
}

/**
 * One thing [Blueprint] tolerated while decoding, handed to its `onDecodeError` callback.
 *
 * [message] and [toString] never contain payload values, so they are safe to log. [raw] is the
 * offending node itself and does carry payload text: do not forward it to analytics or a crash reporter.
 */
public class DecodeError(
    /** What kind of problem was tolerated. */
    public val kind: DecodeErrorKind,
    /** The node's `type` string, or null when it is missing or the problem is not about one node. */
    public val type: String?,
    /** Why it was tolerated: the field path and the expected kind, without echoing any payload value. */
    public val message: String?,
    /** The offending JSON object, when there is one. It carries payload text; keep it out of analytics. */
    public val raw: JsonObject?,
) {
    override fun equals(other: Any?): Boolean =
        other is DecodeError && kind == other.kind && type == other.type && message == other.message && raw == other.raw

    override fun hashCode(): Int {
        var result = kind.hashCode()
        result = 31 * result + (type?.hashCode() ?: 0)
        result = 31 * result + (message?.hashCode() ?: 0)
        result = 31 * result + (raw?.hashCode() ?: 0)
        return result
    }

    /** Omits [raw], and omits [type] when the server sent it for a type nobody registered. */
    override fun toString(): String {
        val shownType = if (type != null && kind != DecodeErrorKind.UnregisteredType) "type=$type, " else ""
        return "DecodeError(kind=$kind, ${shownType}message=$message)"
    }
}

private val FAILED_LITERAL = Regex("""^Failed to parse literal '.*' as (an? \w+) value""")
private val WRONG_ELEMENT = Regex("""^Expected (\w+), but had (\w+) as the serialized body of ([\w.<>$]+)""")
private val BAD_TOKEN = Regex("""^Unexpected JSON token at offset (\d+): Expected ([^\n]{1,40}?), but had""")
private val FAILURE_PATH = Regex("""at (?:element|path): (\$\S*)""")
private val PATH_TOKEN = Regex("""\.([A-Za-z0-9_]+)|\[\d+]|\['[^']*']""")

/**
 * Describes a decoding failure without echoing the payload. kotlinx puts the offending value in its
 * messages (`Failed to parse literal '"hunter2"'`), appends a `JSON input:` snippet, and names map keys
 * in paths, so the text is rebuilt from the shapes it is known to take instead of filtered. Anything
 * unrecognised, including a host model's own `require` message, is reduced to its exception name.
 */
@OptIn(ExperimentalSerializationApi::class) // MissingFieldException.missingFields names schema fields, not payload text
internal fun describeFailure(e: Throwable, fieldNames: Set<String>): String {
    if (e is MissingFieldException) {
        val fields = e.missingFields.joinToString(", ") { "'$it'" }
        return "Missing required field${if (e.missingFields.size > 1) "s" else ""} $fields"
    }
    val first = e.message.orEmpty().substringBefore('\n')
    val path = FAILURE_PATH.find(first)?.let { " at ${safePath(it.groupValues[1], fieldNames)}" }.orEmpty()
    FAILED_LITERAL.find(first)?.let { return "Expected ${it.groupValues[1]} value$path" }
    WRONG_ELEMENT.find(first)?.let { return "Expected ${it.groupValues[1]}, but had ${it.groupValues[2]} for ${it.groupValues[3]}$path" }
    BAD_TOKEN.find(first)?.let { return "Malformed JSON at offset ${it.groupValues[1]}: expected ${it.groupValues[2]}" }
    return "Invalid value (${e::class.simpleName})$path"
}

/** Keeps only segments that name a field the schema knows; anything else (a map key) becomes `*`. */
private fun safePath(path: String, fieldNames: Set<String>): String {
    val out = StringBuilder("$")
    for (token in PATH_TOKEN.findAll(path.removePrefix("$"))) {
        val name = token.groupValues[1]
        when {
            name.isNotEmpty() -> out.append(if (name in fieldNames) ".$name" else ".*")
            token.value.startsWith("['") -> out.append("[*]")
            else -> out.append(token.value)
        }
    }
    return out.toString()
}

/** Every field name the envelope, the modifier and each registered component or action declares. */
@OptIn(ExperimentalSerializationApi::class) // walking descriptors is the only way to list a schema's fields
private fun schemaFieldNames(registry: SduiRegistry): Set<String> {
    val names = HashSet<String>()
    val seen = HashSet<String>()
    fun walk(d: SerialDescriptor) {
        if (!seen.add(d.serialName)) return
        when (d.kind) {
            StructureKind.CLASS, StructureKind.OBJECT -> for (i in 0 until d.elementsCount) {
                names += d.getElementName(i)
                walk(d.getElementDescriptor(i))
            }
            StructureKind.LIST -> walk(d.getElementDescriptor(0))
            StructureKind.MAP -> walk(d.getElementDescriptor(1)) // the key (0) is payload text
            is PolymorphicKind -> Unit
            else -> Unit
        }
    }
    listOf(SduiScreen.serializer(), SduiModifier.serializer()).forEach { walk(it.descriptor) }
    registry.components.values.forEach { walk(it.serializer.descriptor) }
    registry.actions.values.forEach { walk(it.serializer.descriptor) }
    return names
}

/** Collects [DecodeError]s for the [Blueprint.decodeScreen] call running on this thread. */
internal class DecodeErrorSink(registry: SduiRegistry) {
    private val current = ThreadLocal<ArrayList<DecodeError>?>()

    /** Field names a path may show; computed on the first failure, since most payloads have none. */
    val fieldNames: Set<String> by lazy { schemaFieldNames(registry) }

    /** Reports into the active call; outside [collect] (direct `json` use) it is a no-op. */
    fun report(error: DecodeError) {
        current.get()?.add(error)
    }

    /** Position to [rollback] to, for an attempt that may be retried. */
    fun mark(): Int = current.get()?.size ?: 0

    /** Forgets what a failed attempt reported, so its retry does not report the same nodes twice. */
    fun rollback(mark: Int) {
        current.get()?.let { while (it.size > mark) it.removeAt(it.size - 1) }
    }

    fun <T> collect(block: () -> T): Pair<T, List<DecodeError>> {
        val previous = current.get()
        val errors = ArrayList<DecodeError>()
        current.set(errors)
        try {
            return block() to errors
        } finally {
            current.set(previous)
        }
    }
}
