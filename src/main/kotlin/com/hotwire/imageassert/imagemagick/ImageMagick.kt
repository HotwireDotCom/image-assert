/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2016 Hotwire, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 *
 */

package com.hotwire.imageassert.imagemagick

import com.hotwire.imageassert.Image
import com.hotwire.imageassert.Rectangle
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.util.logging.Logger

fun start(vararg command: String): Process {
    try {
        val processBuilder = ProcessBuilder()
        processBuilder.command(*command)
        return processBuilder.start()
    } catch (e: IOException) {
        throw RuntimeException("Failed to start process!", e)
    }
}

fun wait(process: Process) {
    try {
        process.waitFor()
    } catch (e: InterruptedException) {
        throw RuntimeException(e)
    }
}

class Result(val image: Image, val delta: Int)

object ImageMagick {

    internal val LOGGER = Logger.getLogger(ImageMagick::class.java.simpleName)

    private fun read(inputStream: InputStream): String? {
        try {
            val inputStreamReader = InputStreamReader(inputStream)
            val bufferedReader = BufferedReader(inputStreamReader)
            return bufferedReader.readLine()
        } catch (e: IOException) {
            throw RuntimeException(e)
        }
    }

    fun write(outputStream: OutputStream, vararg images: Image) {
        try {
            for (image in images) {
                image.save(outputStream)
            }
            outputStream.close()
        } catch (e: IOException) {
            throw RuntimeException(e)
        }
    }

    /**
     * Call *compare* executable, write actual and expected images to *stdin* and read diff image from *stdout*.
     * Difference in pixels is being written to *stderr*.
     * *stdout* and *stderr* must be read simultaneously to prevent buffer from being blocked.
     *
     * @param actual   Actual image.
     * @param expected Expected image.
     * @return Result instance containing diff image and difference in pixels.
     */
    fun compare(actual: Image, expected: Image): Result {
        val process = start("magick", "compare", "-define", "stream:buffer-size=0", "-fuzz", "17%", "-metric", "AE", "miff:-", "png:-")
        write(process.outputStream, convertToMiff(actual), convertToMiff(expected))

        val asyncTaskPool = AsyncTaskPool()
        val imageByteArray = ByteArrayOutputStream()
        val stringByteArray = ByteArrayOutputStream()
        asyncTaskPool.read(process.inputStream, imageByteArray)
        asyncTaskPool.read(process.errorStream, stringByteArray)
        asyncTaskPool.join()

        val image = Image.load(imageByteArray.toByteArray())
        val output = stringByteArray.toString()
        if (output.length > 0) {
            LOGGER.finest(output)
            val intValue = java.lang.Double.valueOf(output).toInt()
            return Result(image, intValue)
        } else {
            throw RuntimeException("WTF???")
        }
    }

    /**
     * Copy alpha-channel.
     */
    fun copyOpacity(image: Image, mask: Image): Image {
        val process = start("magick", "convert", "-define", "stream:buffer-size=0", "miff:-", "-alpha", "off", "-compose", "copy-opacity", "-composite", "png:-")
        write(process.outputStream, convertToMiff(image), convertToMiff(mask))
        val bytes = AsyncReader.read(process.inputStream)
        val output = read(process.errorStream)
        wait(process)
        if (output != null) LOGGER.warning(output)
        if (process.exitValue() != 0) throw RuntimeException()
        return Image.load(bytes)
    }

    /**
     * Fill image with solid color.
     */
    fun fill(source: Image): Image {
        val process = start("magick", "convert", "-", "-fill", "white", "-colorize", "100%", "-")
        write(process.outputStream, source)
        val output = read(process.errorStream)
        if (output != null) {
            LOGGER.finest(output)
        }
        return Image.load(process.inputStream)
    }

    /**
     * Draw a rectangle.
     */
    fun rectangle(source: Image, rectangle: Rectangle): Image {
        val format = "rectangle %d,%d %d,%d".format(rectangle.x, rectangle.y, rectangle.x + rectangle.width, rectangle.y + rectangle.height)
        val process = start("magick", "convert", "-", "-fill", "black", "-draw", format, "-")
        write(process.outputStream, source)
        val output = read(process.errorStream)
        if (output != null) {
            LOGGER.finest(output)
        }
        return Image.load(process.inputStream)
    }

    /**
     * Remove alpha-channel.
     */
    fun removeAlpha(file: Image): Image {
        val process = start("magick", "convert", "png:-", "-alpha", "off", "png:-")
        write(process.outputStream, file)
        val bytes = AsyncReader.read(process.inputStream)
        val output = read(process.errorStream)
        if (output != null) {
            LOGGER.finest(output)
        }
        return Image.load(bytes)
    }


    /**
     * Convert png to Magic Image File Format - ImageMagick's input stream doesn't support reading morethan one png files
     */
    fun convertToMiff(source: Image): Image {
        val process = start("magick", "convert", "-",  "miff:-")
        write(process.outputStream, source)
        val bytes = AsyncReader.read(process.inputStream)
        val output = read(process.errorStream)
        if (output != null) {
            LOGGER.finest(output)
        }
        return Image.load(bytes)
    }
}