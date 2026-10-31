package io.github.ammaralghifary.blueprint.registry

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.ammaralghifary.blueprint.model.SduiAction
import io.github.ammaralghifary.blueprint.model.SduiComponent
import io.github.ammaralghifary.blueprint.model.UnknownComponent
import io.github.ammaralghifary.blueprint.render.DefaultFallback
import io.github.ammaralghifary.blueprint.render.SduiScope
import kotlin.reflect.KClass
import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer

/** Renders one component type. Use [SduiScope.Render] to draw children and [SduiScope.dispatch] for actions. */
public typealias SduiComponentRenderer<T> = @Composable (component: T, modifier: Modifier, scope: SduiScope) -> Unit

/** Draws a component whose type is not registered. */
public typealias SduiFallback = @Composable (component: UnknownComponent, modifier: Modifier) -> Unit

internal class ComponentRegistration<T : SduiComponent>(
    val type: String,
    val kClass: KClass<T>,
    val serializer: KSerializer<T>,
    val render: SduiComponentRenderer<T>,
) {
    /**
     * Invokes [render] for a component whose class is known to be [kClass]. Only the component is
     * cast (unchecked, erased). Never cast [render] itself to a function type: Kotlin's arity check
     * rejects a `ComposableLambdaImpl`, which implements Function2 through Function21.
     */
    @Composable
    fun Render(component: SduiComponent, modifier: Modifier, scope: SduiScope) {
        @Suppress("UNCHECKED_CAST")
        render(component as T, modifier, scope)
    }
}

internal class ActionRegistration<T : SduiAction>(
    val type: String,
    val kClass: KClass<T>,
    val serializer: KSerializer<T>,
)

/**
 * Immutable mapping from JSON `type` strings to model classes, serializers and composables.
 * Build one with [sduiRegistry].
 */
public class SduiRegistry internal constructor(
    internal val components: Map<String, ComponentRegistration<*>>,
    internal val actions: Map<String, ActionRegistration<*>>,
    internal val fallback: SduiFallback,
) {
    private val byClass: Map<KClass<*>, ComponentRegistration<*>> = components.values.associateBy { it.kClass }

    internal fun componentSerializer(type: String?): KSerializer<out SduiComponent>? = components[type]?.serializer
    internal fun actionSerializer(type: String?): KSerializer<out SduiAction>? = actions[type]?.serializer
    internal fun registrationFor(component: SduiComponent): ComponentRegistration<*>? = byClass[component::class]

    /** Registered component type strings. */
    public val componentTypes: Set<String> get() = components.keys
}

/** Builds a [SduiRegistry] from [block]. */
public fun sduiRegistry(block: SduiRegistryBuilder.() -> Unit): SduiRegistry =
    SduiRegistryBuilder().apply(block).build()

/** Receiver of [sduiRegistry]: register components, actions and a fallback. */
public class SduiRegistryBuilder internal constructor() {
    private val components = LinkedHashMap<String, ComponentRegistration<*>>()
    private val actions = LinkedHashMap<String, ActionRegistration<*>>()
    private var fallback: SduiFallback = DefaultFallback

    /**
     * Registers [T] for JSON `"type": type`. Throws if [type] (or the class) is already registered,
     * unless [replace] is true, which lets you override something pulled in with [include].
     */
    public inline fun <reified T : SduiComponent> component(
        type: String,
        replace: Boolean = false,
        noinline render: SduiComponentRenderer<T>,
    ) {
        component(type, T::class, serializer<T>(), replace, render)
    }

    /** Same as the reified [component], for when you already hold the [kClass] and [serializer]. */
    public fun <T : SduiComponent> component(
        type: String,
        kClass: KClass<T>,
        serializer: KSerializer<T>,
        replace: Boolean = false,
        render: SduiComponentRenderer<T>,
    ) {
        register(components, type, replace, kClass, ComponentRegistration(type, kClass, serializer, render)) { it.kClass }
    }

    /** Registers an action model for JSON `"type": type`. Same duplicate rules as [component]. */
    public inline fun <reified T : SduiAction> action(type: String, replace: Boolean = false) {
        action(type, T::class, serializer<T>(), replace)
    }

    /** Same as the reified [action], for when you already hold the [kClass] and [serializer]. */
    public fun <T : SduiAction> action(type: String, kClass: KClass<T>, serializer: KSerializer<T>, replace: Boolean = false) {
        register(actions, type, replace, kClass, ActionRegistration(type, kClass, serializer)) { it.kClass }
    }

    /** Replaces what is drawn for unregistered component types. */
    public fun fallback(render: SduiFallback) {
        fallback = render
    }

    /** Copies every component and action of [other] into this registry. Duplicates throw. */
    public fun include(other: SduiRegistry) {
        other.components.values.forEach { register(components, it.type, false, it.kClass, it) { r -> r.kClass } }
        other.actions.values.forEach { register(actions, it.type, false, it.kClass, it) { r -> r.kClass } }
    }

    internal fun build(): SduiRegistry = SduiRegistry(components.toMap(), actions.toMap(), fallback)

    private fun <R> register(
        map: MutableMap<String, R>,
        type: String,
        replace: Boolean,
        kClass: KClass<*>,
        entry: R,
        classOf: (R) -> KClass<*>,
    ) {
        require(type.isNotBlank()) { "Type must not be blank" }
        if (!replace) {
            require(type !in map) { "Type '$type' is already registered" }
        }
        val clash = map.entries.firstOrNull { (t, r) -> t != type && classOf(r) == kClass }
        require(clash == null) {
            "${kClass.simpleName} is already registered as '${clash?.key}'; one class cannot back two types"
        }
        map[type] = entry
    }
}
