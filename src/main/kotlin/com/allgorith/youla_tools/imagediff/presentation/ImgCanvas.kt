package com.allgorith.youla_tools.imagediff.presentation

import com.intellij.ui.components.JBPanel
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.plugins.notebooks.visualization.use
import java.awt.Color
import java.awt.Composite
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.RenderingHints
import java.awt.event.InputEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseWheelEvent
import java.awt.image.BufferedImage
import java.io.File
import java.util.Queue
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.atomic.AtomicReference
import javax.imageio.ImageIO
import javax.swing.SwingUtilities
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private data class RenderCfg(
    val ref: BufferedImage? = null,
    val scr: BufferedImage? = null,
    val composite: Composite? = null,
    val pxScale: Float = 0f,
    val referenceXOffset: Int = 0,
    val referenceYOffset: Int = 0,
)

private data class Finder(
    var x0: Int = 0,
    var x1: Int = 0,
    var y0: Int = 0,
    var y1: Int = 0,
) {
    fun isNotEmpty(): Boolean =
        x0 != x1 && y0 != y1

    fun contains(x: Int, y: Int) = when {
        x < min(x0, x1) -> false
        x > max(x0, x1) -> false
        y < min(y0, y1) -> false
        y > max(y0, y1) -> false
        else -> true
    }
}

class ImgCanvas(
    private val scope: CoroutineScope,
) : JBPanel<JBPanel<*>>() {
    val result = AtomicReference<BufferedImage>()
    private val imagePool = ConcurrentLinkedDeque<BufferedImage>()
    private val state = MutableStateFlow(RenderCfg())

    private val rect = Rectangle()
    private val finders = mutableListOf<Finder>()
    private var draggingFinder: Finder? = null

    private var offsetX = 0f
    private var offsetY = 0f
    private var scale = 1.0f

    init {
        scope.launch {
            state.collect {
                val newResult = withContext(Dispatchers.Default) {
                    generateResultingImage(it, imagePool)
                }
                result.getAndSet(newResult)?.let(imagePool::addLast)
                repaint()
            }
        }
    }

    var pxScale: Float
        get() = state.value.pxScale
        set(value) = state.update { it.copy(pxScale = value) }

    var referenceXOffset: Int
        get() = state.value.referenceXOffset
        set(value) = state.update { it.copy(referenceXOffset = value) }

    var referenceYOffset: Int
        get() = state.value.referenceYOffset
        set(value) = state.update { it.copy(referenceYOffset = value) }

    init {
        val listener = Listener()
        addMouseListener(listener)
        addMouseMotionListener(listener)
        addMouseWheelListener(listener)
    }

    fun setReferenceFile(file: File) {
        @Suppress("BlockingMethodInNonBlockingContext")
        scope.launch(Dispatchers.IO) {
            state.update { it.copy(ref = ImageIO.read(file)) }
        }
    }

    fun setScreenshot(file: File) {
        @Suppress("BlockingMethodInNonBlockingContext")
        scope.launch(Dispatchers.IO) {
            state.update { it.copy(scr = ImageIO.read(file)) }
        }
    }

    fun setComposite(composite: Composite) {
        state.update { it.copy(composite = composite) }
    }

    fun compileViewportImage(): BufferedImage {
        return UIUtil.createImage(this, rect.width, rect.height, BufferedImage.TYPE_INT_ARGB)
            .apply {
                graphics.use {
                    paint(it)
                }
            }
    }

    private fun generateResultingImage(cfg: RenderCfg, pool: Queue<BufferedImage>): BufferedImage? = with(cfg) {
        if (scr == null) return ref
        if (ref == null) return scr

        val w = scr.width
        val h = scr.height

        val img: BufferedImage
        while (true) {
            val img1 = pool.poll()
            if (img1 == null) {
                img = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
                break
            }
            if (img1.width == w && img1.height == height) {
                img = img1
                break
            }
        }

        (img.graphics as Graphics2D).use { g ->
            g.renderRespectingAspect(ref, referenceXOffset, referenceYOffset, w)
            g.composite = composite
            g.renderRespectingAspect(scr, 0, 0, w)
        }
        return img
    }

    @Suppress("UseJBColor")
    override fun paint(g: Graphics?) {
        super.paint(g)
        if (g !is Graphics2D) return
        g.setRenderingHint(
            RenderingHints.KEY_INTERPOLATION,
            RenderingHints.VALUE_INTERPOLATION_BICUBIC
        )

        computeVisibleRect(rect)
        g.color = Color.DARK_GRAY
        g.fillRect(rect.x, rect.y, rect.width, rect.height)

        result.get()?.let {
            val w = it.width
            g.renderRespectingAspect(
                it,
                rect.x + offsetX.roundToInt(),
                rect.y + offsetY.roundToInt(),
                (w * scale).roundToInt()
            )

            g.color = Color.WHITE
            for (f in finders) {
                g.drawFinderBox(f)
            }

            draggingFinder?.let {
                g.color = Color.YELLOW
                g.drawFinderBox(it)
            }
        }
    }

    private fun Graphics.drawFinderBox(finder: Finder) {
        val fl = min(finder.x0, finder.x1)
        val ft = min(finder.y0, finder.y1)
        val fr = max(finder.x0, finder.x1)
        val fb = max(finder.y0, finder.y1)
        val fw = fr - fl
        val fh = fb - ft

        val adj = scale
        val l = (((rect.x + fl) * adj + offsetX)).roundToInt()
        val t = (((rect.y + ft) * adj + offsetY)).roundToInt()
        val w = (fw * scale).roundToInt()
        val h = (fh * scale).roundToInt()
        drawRect(l, t, w, h)

        val wString = if (pxScale > 0) {
            val dw = fw / pxScale
            "w:${fw}px (${dw}dp)"
        } else {
            "w:${fw}px"
        }
        val hString = if (pxScale > 0) {
            val dh = fh / pxScale
            "h:${fh}px (${dh}dp)"
        } else {
            "h:${fh}px"
        }
        drawString(wString, l, t - 10)
        drawString(hString, l + w + 10, t + h)
    }

    private fun Graphics2D.renderRespectingAspect(image: BufferedImage, x: Int, y: Int, w: Int) {
        val srcW = image.width
        val srcH = image.height
        val aspect = srcH.toFloat() / srcW.toFloat()

        val h = (aspect * w).toInt()
        drawImage(image, x, y, w, h, null)
    }

    private inner class Listener : MouseAdapter() {
        private val MouseEvent.canvasX: Int
            get() = ((x - rect.x - offsetX) / scale).roundToInt()

        private val MouseEvent.canvasY: Int
            get() = ((y - rect.y - offsetY) / scale).roundToInt()

        override fun mousePressed(event: MouseEvent) {
            if (SwingUtilities.isLeftMouseButton(event)) {
                if (result.get() == null) return
                val x = event.canvasX
                val y = event.canvasY
                draggingFinder = Finder(x, x, y, y)
                repaint()
            } else if (SwingUtilities.isRightMouseButton(event)) {
                val x = event.canvasX
                val y = event.canvasY
                var i = finders.size - 1
                while (i >= 0) {
                    val finder = finders[i]
                    if (finder.contains(x, y)) {
                        finders.removeAt(i)
                        repaint()
                        return
                    } else {
                        i -= 1
                    }
                }
            }
        }

        override fun mouseDragged(event: MouseEvent) {
            val finder = draggingFinder ?: return
            finder.x1 = event.canvasX
            finder.y1 = event.canvasY
            repaint()
        }

        override fun mouseReleased(event: MouseEvent) {
            val finder = draggingFinder ?: return
            draggingFinder = null
            finder.x1 = event.canvasX
            finder.y1 = event.canvasY
            if (finder.isNotEmpty()) {
                finders.add(finder)
            }
            repaint()
        }

        override fun mouseWheelMoved(event: MouseWheelEvent) {
            if (event.modifiersEx containsMask InputEvent.ALT_DOWN_MASK) {
                val fScale = event.preciseWheelRotation.toFloat() * 0.02f
                offsetX -= (event.x - offsetX) * fScale
                offsetY -= (event.y - offsetY) * fScale
                scale *= 1.0f + fScale
            } else {
                val dist = (-event.preciseWheelRotation * 10f).toFloat()
                // Horizontal 2finger swipe reported as Shift + Wheel
                if (event.modifiersEx containsMask InputEvent.SHIFT_DOWN_MASK) {
                    offsetX += dist
                } else {
                    offsetY += dist
                }
            }
            repaint()
        }

        private infix fun Int.containsMask(mask: Int): Boolean = (this and mask) == mask
    }
}
