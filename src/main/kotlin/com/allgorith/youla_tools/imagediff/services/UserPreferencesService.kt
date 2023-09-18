package com.allgorith.youla_tools.imagediff.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import java.io.File

@Service(Service.Level.PROJECT)
class UserPreferencesService(project: Project) {
    // User defined/changeable?
    val devFilesDirectory: File = File("${project.basePath}/devFiles")
}
