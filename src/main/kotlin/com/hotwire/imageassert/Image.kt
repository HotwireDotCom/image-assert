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

package com.hotwire.imageassert

import com.hotwire.imageassert.imagemagick.ImageMagick
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

class Image private constructor(private val bytes: ByteArray) {

    fun save(target: OutputStream) {
        bytes.inputStream().copyTo(target)
    }

    fun save(target: File): File {
        if (target.exists()) {
            if (!target.delete()) {
                throw RuntimeException("Failed to delete file %s".format(target.absolutePath))
            }
        }
        try {
            if (!target.parentFile.exists() && !target.parentFile.mkdirs()) {
                throw RuntimeException("Failed to create directory %s".format(target.parentFile.absolutePath))
            }
            if (!target.createNewFile()) {
                throw RuntimeException("Failed to create file %s".format(target.absolutePath))
            }
            val outputStream = FileOutputStream(target)
            try {
                save(outputStream)
            } finally {
                outputStream.close()
            }
        } catch (e: IOException) {
            throw RuntimeException("Failed to save %d bytes to file %s".format(bytes.size, target.absolutePath), e)
        }
        return target
    }

    companion object {
        @JvmStatic fun load(source: ByteArray): Image {
            return Image(source)
        }

        @JvmStatic fun load(source: InputStream): Image {
            try {
                val bytes = source.readBytes()
                return Image(bytes)
            } catch (e: IOException) {
                throw RuntimeException("Failed to copy stream!", e)
            }
        }

        @JvmStatic fun load(source: File): Image {
            try {
                return load(FileInputStream(source))
            } catch (e: FileNotFoundException) {
                throw RuntimeException("File does not exist!", e)
            }
        }

        @JvmStatic fun toPng(source: InputStream, pageNumber: Number, density: Int = 300, quality: Int = 90, backgroundColor: String = "white"): Image {
            try {
                return ImageMagick.convertToPng(source, pageNumber, density, quality, backgroundColor)
            } catch (e: IOException) {
                throw RuntimeException("Failed to convert PDF stream!", e)
            }
        }

        @JvmStatic fun toPng(source: File, pageNumber: Number, density: Int = 300, quality: Int = 90, backgroundColor: String = "white"): Image {
            try {
                return toPng(FileInputStream(source), pageNumber, density, quality, backgroundColor)
            } catch (e: FileNotFoundException) {
                throw RuntimeException("File does not exist!", e)
            }
        }
    }
}
