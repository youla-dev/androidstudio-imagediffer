package com.allgorith.youla_tools.imagediff.composite

import java.awt.Composite
import java.awt.CompositeContext
import java.awt.RenderingHints
import java.awt.image.ColorModel
import java.awt.image.Raster
import java.awt.image.WritableRaster
import kotlin.math.absoluteValue
import kotlin.math.min

class PixDiffComposite(
    // [0 .. 255*3] max sum of color diff
    val minDiff: Int,
) : Composite {
    override fun createContext(
        src: ColorModel?,
        dst: ColorModel?,
        hints: RenderingHints?
    ): CompositeContext {
        require(src!!.componentSize.contentEquals(dst!!.componentSize)) {
            "${src.componentSize.joinToString()} vs ${dst.componentSize.joinToString()}"
        }
        // only argb supported
        return Context()
    }

    private inner class Context : CompositeContext {
        val srcPixel = IntArray(4)
        val dstPixel = IntArray(4)
        val outPixel = IntArray(4)

        override fun dispose() {}

        override fun compose(src: Raster, dstIn: Raster, dstOut: WritableRaster) {
            val width = min(src.width, dstIn.width)
            val height = min(src.height, dstIn.height)

            for (y in 0 until height) {
                for (x in 0 until width) {
                    dstOut.setPixel(
                        x,
                        y,
                        blend(
                            outPixel,
                            src.getPixel(x, y, srcPixel),
                            dstIn.getPixel(x, y, dstPixel)
                        )
                    )
                }
            }
        }

        private fun blend(buff: IntArray, a: IntArray, b: IntArray): IntArray {
            buff[0] = (a[0] * 0.5f).toInt().coerceIn(0, 255)
            buff[1] = (a[1] * 0.5f).toInt().coerceIn(0, 255)
            buff[2] = (a[2] * 0.5f).toInt().coerceIn(0, 255)
            buff[3] = 0xFF

            val alen = a[0] + a[1] + a[2]
            val blen = b[0] + b[1] + b[2]
            if ((alen - blen).absoluteValue > minDiff) {
                if (alen < blen) buff[1] = 0xFF
                else buff[0] = 0xFF
            }
            return buff
        }
    }
}
