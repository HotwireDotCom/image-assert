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
 */

package com.hotwire.imageassert.report

import com.google.gson.Gson
import com.hotwire.imageassert.listener.Result
import com.hotwire.imageassert.listener.ResultListener
import java.io.File
import java.io.IOException
import java.util.ArrayList
import java.util.HashMap

/**
 * Collect results and produce HTML report.
 */
class HTMLReportResultListener(val outputDir: File) : ResultListener {

    val results = ArrayList<Map<String, Any>>()

    @Throws(IOException::class)
    fun extractResource(resource: String, file: File) {
        val inputStream = HTMLReportResultListener::class.java.getResourceAsStream(resource)
        try {
            file.parentFile.apply { if (!exists() && !mkdirs()) throw IOException("Could not create directory ${canonicalPath}") }
            file.writeBytes(inputStream.readBytes());
        } finally {
            inputStream.close()
        }
    }

    override fun report(result: Result) {
        val map = HashMap<String, Any>()
        map.put("id", result.id)
        map.put("mismatch", result.mismatch)
        map.put("threshold", result.threshold)

        val id = result.id.toLowerCase().replace(" ".toRegex(), "-")
        val sample = File(outputDir, id + ".png")
        result.actual.save(sample)
        map.put("sample", sample.absolutePath)

        val base = File(outputDir, id + "-base.png")
        result.expected.save(base)
        map.put("base", base.absolutePath)

        val delta = File(outputDir, id + "-diff.png")
        result.diff.save(delta)
        map.put("delta", delta.absolutePath)

        results.add(map)
    }

    fun saveReport() = try {
        if (!outputDir.exists() && !outputDir.mkdirs()) throw IOException("Could not create directory $outputDir!")
        val json = Gson().toJson(results).replace(outputDir.absolutePath.toRegex(), ".")
        File(outputDir, "report.json").writeBytes(json.toByteArray())
        extractResource("index.html", File(outputDir, "index.html"))
        extractResource("css/style.css", File(outputDir, "css/style.css"))
        extractResource("js/engine.js", File(outputDir, "js/engine.js"))
        extractResource("js/jquery-1.11.3.min.js", File(outputDir, "js/jquery-1.11.3.min.js"))
    } catch (e: IOException) {
        throw RuntimeException(e)
    }
}
