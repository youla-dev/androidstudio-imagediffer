package com.allgorith.youla_tools.imagediff.services

import com.allgorith.youla_tools.imagediff.lazyService
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import java.io.File
import java.time.LocalDateTime

@Service(Service.Level.PROJECT)
class FileFactoryService(project: Project) {
    private val userPreferences: UserPreferencesService by project.lazyService()
    private val gitService: GitService by project.lazyService()

    fun createOutputFile(subdirectory: String, fileExtension: String, tag: String?): File {
        val branch = gitService.branchName()
        val root = File(userPreferences.devFilesDirectory, "$branch/$subdirectory")
        root.mkdirs()

        val baseName = if (tag != null) "${dateString()}-$tag"
        else dateString()
        var i = 0
        while (true) {
            val name = if (i == 0) "$baseName.$fileExtension"
            else "$baseName-$i.$fileExtension"
            val file = File(root, name)
            if (!file.exists()) {
                file.createNewFile()
                return file
            } else {
                i++
            }
        }
    }

    private fun dateString(): String {
        val date = LocalDateTime.now()
        val yy = date.year % 100
        val mm = date.monthValue
        val dd = date.dayOfMonth
        val hh = date.hour
        val mn = date.minute
        val ss = date.second
        return String.format(
            "%02d%02d%02d-%02d%02d%02d",
            yy,
            mm,
            dd,
            hh,
            mn,
            ss,
        )
    }
}
