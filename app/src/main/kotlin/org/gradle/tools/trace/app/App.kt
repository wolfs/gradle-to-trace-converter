package org.gradle.tools.trace.app

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.file
import com.google.gson.Gson
import perfetto.protos.TraceOuterClass.Trace
import java.io.File

fun main(args: Array<String>) = ConverterApp().main(args)

class ConverterApp : CliktCommand() {

    private val buildOperationTrace: File by argument(name = "trace", help = "Path to the build operation trace file")
        .file(mustExist = true, canBeDir = false, mustBeReadable = true)

    private val include: Regex? by option("-i", "--include", help = "Regex to filter the build operations to include by display name")
        .convert { it.toRegex() }

    private val exclude: Regex? by option("-e", "--exclude", help = "Regex to filter the build operations to exclude")
        .convert { it.toRegex() }

    override fun run() {
        convertToChromeTrace(buildOperationTrace)
    }

    private fun convertToChromeTrace(traceFile: File) {
        val records = readBuildOperationTrace(traceFile)
        println("Read ${records.size} records from ${traceFile.name}")
        val slice = BuildOperationTraceSlice(records.toList(), include, exclude)
        val traceEvents = TraceToChromeTraceConverter().convert(slice)
        val trace = Trace.newBuilder()
            .addAllPacket(traceEvents)
            .build()
        val traceFileProto = File(traceFile.parentFile, traceFile.nameWithoutExtension + "-chrome.proto")
        traceFileProto.writeBytes(trace.toByteArray())
        println("Wrote ${traceEvents.size} events to ${traceFileProto.absolutePath}")
    }

    private fun readBuildOperationTrace(traceJsonFile: File): Array<BuildOperationRecord> {
        val inputTraceJsonText = traceJsonFile.readText()
        try {
            return Gson().fromJson(inputTraceJsonText, Array<BuildOperationRecord>::class.java)
        } catch (e: Exception) {
            throw RuntimeException("Failed to read build operation trace file: ${traceJsonFile.absolutePath}", e)
        }
    }

}
