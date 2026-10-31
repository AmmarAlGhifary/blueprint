/**
 * TypeScript types for the payload a server sends to Blueprint, an Android library that renders
 * server-driven JSON as native Jetpack Compose. Same contract as blueprint.schema.json; see README.md.
 *
 * Blueprint is tolerant at runtime: it ignores unknown fields and shows a placeholder for a component
 * or action 'type' it does not know, so one new server component never breaks a screen. These types
 * are stricter on purpose, so the compiler catches a typo or a missing required field before it ships.
 *
 * The stock components and the 'navigate' action are typed in full. To send a component or action of
 * your own (one the host app registered), pass its interface as a type argument:
 *
 *     interface ScheduleCard { type: "schedule_card"; title: string; time: string }
 *     const screen: BlueprintScreen<ScheduleCard> = {
 *       body: { type: "column", children: [{ type: "schedule_card", title: "Standup", time: "09:30" }] },
 *     };
 */

/** `#RGB`, `#RRGGBB` or `#AARRGGBB`. Anything else is skipped when drawing. */
export type BlueprintColor = string;

/** Spacing in dp. A specific side wins over `horizontal`/`vertical`, which win over `all`. */
export interface BlueprintPadding {
  all?: number;
  horizontal?: number;
  vertical?: number;
  start?: number;
  top?: number;
  end?: number;
  bottom?: number;
}

/** A width or height. `fill` and `wrap` take no value; `exact` needs `value` in dp. */
export type BlueprintSize =
  | { type: "fill" | "wrap" }
  | { type: "exact"; value: number };

/**
 * Where a child sits in its parent: column `start|center|end`, row `top|center|bottom`, box
 * `top_start|top_center|top_end|center_start|center|center_end|bottom_start|bottom_center|bottom_end`.
 */
export type BlueprintAlignment =
  | "start" | "center" | "end" | "top" | "bottom"
  | "top_start" | "top_center" | "top_end"
  | "center_start" | "center_end"
  | "bottom_start" | "bottom_center" | "bottom_end";

/**
 * Box model applied by the renderer before a component draws. Every field is optional. Dp values are
 * integers: a negative one is clamped to 0 and one above 16384 to 16384.
 */
export interface BlueprintModifier {
  margin?: BlueprintPadding;
  padding?: BlueprintPadding;
  width?: BlueprintSize;
  height?: BlueprintSize;
  alignment?: BlueprintAlignment;
  /** Share of the parent row or column; honoured only when greater than 0. */
  weight?: number;
  /** Shadow elevation in dp. */
  elevation?: number;
  background_color?: BlueprintColor;
  /** Corner radius in dp. */
  corner_radius?: number;
  /** Border width in dp; drawn only together with `border_color`. */
  border_width?: number;
  border_color?: BlueprintColor;
  /** Read by `text` and `button`. */
  text_color?: BlueprintColor;
  /** Font size in sp. Read by `text` and `button`. */
  font_size?: number;
  /** 100 to 900, clamped. Read by `text` and `button`. */
  font_weight?: number;
}

/** Asks the host to go somewhere. Blueprint never interprets `destination`; the host's `onAction` does. */
export interface NavigateAction {
  type: "navigate";
  destination: string;
  /** String arguments for `destination`. Values must be strings. */
  params?: { [key: string]: string };
}

/** `NavigateAction`, plus any action of your own: `BlueprintAction<{ type: "share"; url: string }>`. */
export type BlueprintAction<A extends { type: string } = never> = NavigateAction | A;

interface ComponentBase {
  modifier?: BlueprintModifier;
}

/** A run of text. */
export interface TextComponent extends ComponentBase {
  type: "text";
  text: string;
  /** Typography role. Unknown falls back to `body`. */
  style?: "display" | "headline" | "title" | "body" | "label";
  /** Truncates with an ellipsis. Values below 1 are treated as 1. */
  max_lines?: number;
  align?: "start" | "center" | "end";
}

/** A remote image. */
export interface ImageComponent extends ComponentBase {
  type: "image";
  url: string;
  /** Omit for a decorative image. */
  content_description?: string;
  /** Unknown falls back to `fit`. */
  scale?: "fit" | "crop" | "fill";
}

/** Disabled when `action` is absent. */
export interface ButtonComponent<A extends { type: string } = never> extends ComponentBase {
  type: "button";
  text: string;
  action?: BlueprintAction<A>;
  /** Unknown falls back to `filled`. */
  variant?: "filled" | "outlined" | "text";
}

/** A container that fires its action when tapped. */
export interface CardComponent<C extends CustomComponent = never, A extends { type: string } = never>
  extends ComponentBase {
  type: "card";
  children?: BlueprintComponent<C, A>[];
  action?: BlueprintAction<A>;
}

export type Arrangement = "start" | "center" | "end" | "space_between" | "space_around" | "space_evenly";

/** A vertical stack. */
export interface ColumnComponent<C extends CustomComponent = never, A extends { type: string } = never>
  extends ComponentBase {
  type: "column";
  children?: BlueprintComponent<C, A>[];
  /** Unknown falls back to `start`. */
  arrangement?: Arrangement;
  /** Scrolls vertically when true. Default false. */
  scrollable?: boolean;
}

/** A horizontal stack. */
export interface RowComponent<C extends CustomComponent = never, A extends { type: string } = never>
  extends ComponentBase {
  type: "row";
  children?: BlueprintComponent<C, A>[];
  /** Unknown falls back to `start`. */
  arrangement?: Arrangement;
}

/** Overlapping children, placed by each child's `modifier.alignment`. */
export interface BoxComponent<C extends CustomComponent = never, A extends { type: string } = never>
  extends ComponentBase {
  type: "box";
  children?: BlueprintComponent<C, A>[];
}

/**
 * A lazy list. With no bounded room on its scroll axis (inside a scrollable column, or inside another
 * list's item) it renders as a plain stack.
 */
export interface ListComponent<C extends CustomComponent = never, A extends { type: string } = never>
  extends ComponentBase {
  type: "list";
  children?: BlueprintComponent<C, A>[];
  /** Unknown falls back to `vertical`. */
  orientation?: "vertical" | "horizontal";
  /** Gap between items in dp. */
  spacing?: number;
}

/** Empty space. */
export interface SpacerComponent extends ComponentBase {
  type: "spacer";
  /** dp. Default 8. A string such as `"medium"` is not accepted. */
  size?: number;
}

/** A hairline. */
export interface DividerComponent extends ComponentBase {
  type: "divider";
  /** dp. Default 1. */
  thickness?: number;
  color?: BlueprintColor;
}

/** What a component of your own must look like: a `type` string, and optionally a `modifier`. */
export interface CustomComponent {
  type: string;
  modifier?: BlueprintModifier;
}

/** One node of the tree: a stock component, or one of your own passed as `C`. */
export type BlueprintComponent<C extends CustomComponent = never, A extends { type: string } = never> =
  | TextComponent
  | ImageComponent
  | ButtonComponent<A>
  | CardComponent<C, A>
  | ColumnComponent<C, A>
  | RowComponent<C, A>
  | BoxComponent<C, A>
  | ListComponent<C, A>
  | SpacerComponent
  | DividerComponent
  | C;

/**
 * The root of a payload. Blueprint draws no scaffold or app bar, so any other root key (an `app_bar`,
 * a `type: "screen"`) is ignored; the host app can read it itself.
 */
export interface BlueprintScreen<C extends CustomComponent = never, A extends { type: string } = never> {
  /** Kept as sent; Blueprint does not act on it. */
  screen_id?: string;
  /** The payload version the server declares. Kept as sent; Blueprint does not act on it. */
  version?: number;
  body: BlueprintComponent<C, A>;
}
