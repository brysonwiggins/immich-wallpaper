package com.wiggyserver.immichwallpaper.render

import com.wiggyserver.immichwallpaper.core.PixelRect
import com.wiggyserver.immichwallpaper.core.Size
import java.awt.Color
import java.awt.GradientPaint
import java.awt.Rectangle
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import java.io.OutputStream
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageReader
import javax.imageio.ImageWriteParam

/** java.awt implementation, used for golden-image tests on the JVM. */
class AwtBackend : RasterBackend<BufferedImage> {
    override fun imageSize(file: File): Size = withReader(file) { Size(it.getWidth(0), it.getHeight(0)) }

    override fun decodeRegion(file: File, region: PixelRect, sampleSize: Int): BufferedImage = withReader(file) { reader ->
        val param = reader.defaultReadParam.apply {
            sourceRegion = Rectangle(region.left, region.top, region.width, region.height)
            setSourceSubsampling(sampleSize, sampleSize, 0, 0)
        }
        reader.read(0, param)
    }

    override fun createCanvas(size: Size) = BufferedImage(size.width, size.height, BufferedImage.TYPE_INT_RGB)

    override fun draw(canvas: BufferedImage, image: BufferedImage, dest: PixelRect) = graphics(canvas) { g ->
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        g.drawImage(image, dest.left, dest.top, dest.right, dest.bottom, 0, 0, image.width, image.height, null)
    }

    override fun fill(canvas: BufferedImage, rect: PixelRect, argb: Int) = graphics(canvas) { g ->
        g.color = Color(argb, true)
        g.fillRect(rect.left, rect.top, rect.width, rect.height)
    }

    override fun fillHorizontalGradient(canvas: BufferedImage, rect: PixelRect, leftArgb: Int, rightArgb: Int) = graphics(canvas) { g ->
        g.paint = GradientPaint(rect.left.toFloat(), 0f, Color(leftArgb, true), rect.right.toFloat(), 0f, Color(rightArgb, true))
        g.fillRect(rect.left, rect.top, rect.width, rect.height)
    }

    override fun averageColor(canvas: BufferedImage, rect: PixelRect): Int {
        if (rect.width <= 0 || rect.height <= 0) return RenderMath.average(IntArray(0))
        return RenderMath.average(canvas.getRGB(rect.left, rect.top, rect.width, rect.height, null, 0, rect.width))
    }

    override fun encodeJpeg(canvas: BufferedImage, out: OutputStream, quality: Int) {
        val writer = ImageIO.getImageWritersByFormatName("jpeg").next()
        try {
            ImageIO.createImageOutputStream(out).use { ios ->
                writer.output = ios
                val param = writer.defaultWriteParam.apply {
                    compressionMode = ImageWriteParam.MODE_EXPLICIT
                    compressionQuality = quality / 100f
                }
                writer.write(null, IIOImage(canvas, null, null), param)
            }
        } finally {
            writer.dispose()
        }
    }

    override fun release(image: BufferedImage) = image.flush()

    private fun <T> withReader(file: File, block: (ImageReader) -> T): T = ImageIO.createImageInputStream(file).use { input ->
        val reader = ImageIO.getImageReaders(input).asSequence().firstOrNull() ?: error("unsupported image: ${file.name}")
        try {
            reader.input = input
            block(reader)
        } finally {
            reader.dispose()
        }
    }

    private fun graphics(canvas: BufferedImage, block: (java.awt.Graphics2D) -> Unit) {
        val g = canvas.createGraphics()
        try {
            block(g)
        } finally {
            g.dispose()
        }
    }
}
