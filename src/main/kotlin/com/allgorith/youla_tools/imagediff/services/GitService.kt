package com.allgorith.youla_tools.imagediff.services

import com.allgorith.youla_tools.imagediff.Cli
import com.allgorith.youla_tools.imagediff.Cli.attempt
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project

@Service(Service.Level.PROJECT)
class GitService(private val project: Project) {
    fun branchName(): String = attempt {
        Cli.execForStdout("git symbolic-ref --short HEAD", workingDir = project.basePath)
            .trim()
    } ?: "UNKNOWN"
}
