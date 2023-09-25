package com.allgorith.youla_tools.imagediff.presentation

import com.allgorith.youla_tools.imagediff.lazyService
import com.allgorith.youla_tools.imagediff.services.Device
import com.allgorith.youla_tools.imagediff.services.DeviceService
import com.allgorith.youla_tools.imagediff.services.FileFactoryService
import com.intellij.execution.runToolbar.components.MouseListenerHelper
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.datatransfer.DataFlavor
import java.awt.dnd.DnDConstants
import java.awt.dnd.DropTarget
import java.awt.dnd.DropTargetDropEvent
import java.io.File
import javax.imageio.ImageIO

class IDiffToolWindow(toolWindow: ToolWindow) {
    private val project = toolWindow.project
    private val scope = MainScope()

    private val deviceService: DeviceService by project.lazyService()
    private val fileFactory: FileFactoryService by project.lazyService()

    val ui = IDiffToolWindowUI(scope).apply {
        takeScreenshot.isEnabled = false
        devicesView.addActionListener {
            if (it.actionCommand == "comboBoxChanged") {
                takeScreenshot.isEnabled = devicesView.selectedItem != Device.noDevice
            }
        }
        takeScreenshot.addActionListener {
            createNewScreenshot()
        }
        saveResult.addActionListener {
            saveResult()
        }
        saveViewport.addActionListener {
            saveViewport()
        }
        MouseListenerHelper.addListener(
            refreshContainer,
            doClick = { refreshDevices() },
            doShiftClick = {},
            doRightClick = {}
        )

        dropTarget = object : DropTarget() {
            override fun drop(evt: DropTargetDropEvent) {
                try {
                    evt.acceptDrop(DnDConstants.ACTION_LINK)
                    @Suppress("UNCHECKED_CAST")
                    val f = evt.transferable
                        .getTransferData(DataFlavor.javaFileListFlavor) as List<File>
                    val file = f.firstOrNull {
                        it.extension == "png"
                    }
                    file?.let { acceptReferenceImage(it) }
                } catch (e: Exception) {
                    thisLogger().error("Failed to accept drag-and-drop file", e)
                } finally {
                    evt.dropComplete(true)
                }
            }
        }
    }

    init {
        refreshDevices()
    }

    private fun refreshDevices(): Job = scope.launch {
        ui.refreshIcon.isVisible = false
        ui.refreshingAnimIcon.isVisible = true
        ui.refreshContainer.isEnabled = false

        try {
            val rawDevices = withContext(Dispatchers.IO) {
                deviceService.resetAdbCaches()
                deviceService.devices()
            }
            val devices = rawDevices.ifEmpty { listOf(Device.noDevice) }

            ui.devicesModel.apply {
                removeAllElements()
                addAll(devices)
                selectedItem = getElementAt(0)
            }
        } catch (e: Exception) {
            thisLogger().error("Failed to fetch devices", e)
        } finally {
            ui.refreshIcon.isVisible = true
            ui.refreshingAnimIcon.isVisible = false
            ui.refreshContainer.isEnabled = true
        }
    }

    private fun createNewScreenshot(): Job = scope.launch {
        val dev = ui.devicesModel.selectedItem as? Device ?: return@launch

        ui.takeScreenshot.isEnabled = false
        val file = withContext(Dispatchers.IO) {
            try {
                val file = fileFactory.createOutputFile("image", "png", dev.name)
                val success = deviceService.takeScreenshot(dev, file)
                if (!success) file.delete()
                file.takeIf { success }
            } catch (e: Exception) {
                refreshDevices()
                thisLogger().error("Failed to create screenshot", e)
                null
            }
        }

        if (file != null) {
            ui.images.setScreenshot(file)
            ui.images.pxScale = dev.dpSize
            ui.repaint()
        }
        ui.takeScreenshot.isEnabled = true
    }

    private fun saveResult(): Job = scope.launch {
        val image = ui.images.result.get() ?: return@launch
        ui.saveResult.isEnabled = false
        try {
            withContext(Dispatchers.IO) {
                val file = fileFactory.createOutputFile("image", "png", "result")
                ImageIO.write(image, "png", file)
            }
        } catch (e: Exception) {
            thisLogger().error("Failed to save result image", e)
        } finally {
            ui.saveResult.isEnabled = true
        }
    }

    private fun saveViewport(): Job = scope.launch {
        ui.saveViewport.isEnabled = false
        try {
            withContext(Dispatchers.IO) {
                val image = ui.images.compileViewportImage()
                val file = fileFactory.createOutputFile("image", "png", "viewport")
                ImageIO.write(image, "png", file)
            }
        } catch (e: Exception) {
            thisLogger().error("Failed to save result image", e)
        } finally {
            ui.saveViewport.isEnabled = true
        }
    }

    private fun acceptReferenceImage(file: File): Job = scope.launch {
        ui.images.setReferenceFile(file)
    }

    class Factory : ToolWindowFactory, DumbAware {
        override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
            val wnd = IDiffToolWindow(toolWindow)
            val content = ContentFactory.getInstance().createContent(wnd.ui, null, false)
            toolWindow.contentManager.addContent(content)
        }
    }
}
