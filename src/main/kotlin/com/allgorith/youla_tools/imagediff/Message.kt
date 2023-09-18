package com.allgorith.youla_tools.imagediff

import com.intellij.DynamicBundle
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.PropertyKey

@NonNls
private const val STRINGS_BUNDLE = "strings.default"

// Analog android R
// Dereferences resources folder
@Suppress("unused")
object Message : DynamicBundle(STRINGS_BUNDLE) {
    fun string(
        @PropertyKey(resourceBundle = STRINGS_BUNDLE) key: String,
        vararg params: Any
    ) = getMessage(key, *params)
}
