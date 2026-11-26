package io.github.ammaralghifary.blueprint.render

import androidx.compose.runtime.Composable
import kotlin.experimental.ExperimentalNativeApi

@OptIn(ExperimentalNativeApi::class) // the only way to ask a native binary how it was built
@Composable
internal actual fun isDebugBuild(): Boolean = Platform.isDebugBinary
