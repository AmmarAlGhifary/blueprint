package io.github.ammaralghifary.blueprint.defaults

import io.github.ammaralghifary.blueprint.model.NavigateAction
import io.github.ammaralghifary.blueprint.registry.SduiRegistry
import io.github.ammaralghifary.blueprint.registry.sduiRegistry

/**
 * The opt-in stock component pack: `text`, `image`, `button`, `card`, `column`, `row`, `box`, `list`,
 * `spacer`, `divider` and the `navigate` action. Use as-is or [SduiRegistryBuilder.include] it and
 * add or `replace` types.
 */
public val DefaultSduiRegistry: SduiRegistry = sduiRegistry {
    component<SduiText>("text") { c, mod, _ -> RenderText(c, mod) }
    component<SduiImage>("image") { c, mod, _ -> RenderImage(c, mod) }
    component<SduiButton>("button") { c, mod, scope -> RenderButton(c, mod, scope) }
    component<SduiCard>("card") { c, mod, scope -> RenderCard(c, mod, scope) }
    component<SduiColumn>("column") { c, mod, scope -> RenderColumn(c, mod, scope) }
    component<SduiRow>("row") { c, mod, scope -> RenderRow(c, mod, scope) }
    component<SduiBox>("box") { c, mod, scope -> RenderBox(c, mod, scope) }
    component<SduiList>("list") { c, mod, scope -> RenderList(c, mod, scope) }
    component<SduiSpacer>("spacer") { c, mod, _ -> RenderSpacer(c, mod) }
    component<SduiDivider>("divider") { c, mod, _ -> RenderDivider(c, mod) }
    action<NavigateAction>("navigate")
}
