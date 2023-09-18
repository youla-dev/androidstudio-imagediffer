package com.allgorith.youla_tools.imagediff

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project

inline fun <reified T : Any> Project.lazyService(
    mode: LazyThreadSafetyMode = LazyThreadSafetyMode.SYNCHRONIZED,
) = lazy(mode) { service<T>() }
