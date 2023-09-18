package com.allgorith.youla_tools.imagediff.services

import com.allgorith.youla_tools.imagediff.Cli
import com.allgorith.youla_tools.imagediff.Cli.attempt
import com.allgorith.youla_tools.imagediff.Message
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import java.io.File

class Device(
    val id: String,
    val name: String,
    val dpSize: Float,
) {

    override fun equals(other: Any?): Boolean = when (other) {
        is Device -> id == other.id
        else -> false
    }

    override fun hashCode(): Int = id.hashCode()

    override fun toString(): String = name

    companion object {
        val noDevice = Device("", Message.string("no_devices"), 1.0f)
    }
}

// Device connection service
@Service(Service.Level.PROJECT)
class DeviceService(private val project: Project) {

    private var adbLocation: String? = null
    private fun generateSdkLocation(): String {
        val f = File(project.basePath!!, "local.properties")
        val lines = f.readLines()
        for (it in lines) {
            if (it.startsWith("#")) continue
            // "sdk.dir=/Users/..."
            val split = it.indexOf('=')
            if (it.substring(0, split) == "sdk.dir") {
                return it.substring(split + 1).trim()
            }
        }
        throw IllegalStateException("Unable to parse local.properties, sdk.dir not found")
    }

    private val adb: String
        @Synchronized
        get() {
            return try {
                if (adbLocation == null) {
                    val sdkLocation = generateSdkLocation()
                    val adb = "$sdkLocation/platform-tools/adb"
                    if (!File(adb).exists())
                        throw IllegalStateException("sdk location found but adb not found")
                    adbLocation = adb
                }

                adbLocation!!
            } catch (e: Exception) {
                thisLogger().error(e)
                adbLocation = null
                "adb"
            }
        }

    fun resetAdbCaches() {
        adbLocation = null
    }

    fun devices(): List<Device> {
        return attempt {
            Cli.execForStdout("$adb devices -l")
                .splitToSequence("\n")
                // Drop 'List of devices attached' string
                .drop(1)
                .filter { it.isNotBlank() }
                .map {
                    val parts = it.splitToSequence(" ")
                        .filter { it.isNotEmpty() }
                        .iterator()

// Parts (7) [S3F4C19C13008032, device, usb:17825792X, product:STK-L21HNRU, model:STK_LX1, device:HWSTK-HF, transport_id:5]
// Parts (6) [emulator-5554, device, product:sdk_gphone64_arm64, model:sdk_gphone64_arm64, device:emulator64_arm64, transport_id:4]
                    val id = parts.next()
                    check(parts.next() == "device") {
                        "Possibly unsupported schema, check comment above and if schema matches update this check"
                    }
                    var name = "Unknown"
                    while (parts.hasNext()) {
                        // key:value
                        val kv = parts.next().split(":")
                        val key = kv[0]
                        val value = kv[1]
                        when (key) {
                            "model" -> name = value
                        }
                    }

                    val dpSize = getDPSize(id)
                    Device(id, name, dpSize)
                }
                .toList()
        } ?: emptyList()
    }

    private fun getDPSize(id: String): Float {
        return attempt {
            val parts = Cli.execForStdout("$adb -s $id shell wm density").split(":")
            assert(parts[0] == "Physical density") {
                "Unexpected density schema, got \"${parts[0]}\", expected \"Physical Density\""
            }
            parts[1].trim().toFloat() / 160.0f
        } ?: 1f
    }

    fun takeScreenshot(device: Device, output: File): Boolean {
        return attempt {
            output.outputStream().use { rx ->
                val tx = Cli.execForStream(
                    command = "$adb -s ${device.id} exec-out screencap -p",
                    wait = false,
                )
                tx.copyTo(rx)
            }
        } != null
    }
}
