package com.hstudy.app.testutil

import java.io.IOException
import okio.Buffer
import okio.FileSystem
import okio.ForwardingFileSystem
import okio.ForwardingSink
import okio.Path
import okio.Sink
import okio.Source

/** A real filesystem with a one-shot, path-specific failure injected before or during an operation. */
class FaultInjectingFileSystem(
    delegate: FileSystem = FileSystem.SYSTEM,
) : ForwardingFileSystem(delegate) {

    enum class Operation { COPY, DELETE, RENAME }

    data class Fault(
        val operation: Operation,
        val source: Path? = null,
        val target: Path? = null,
        /** For COPY, fail only after the sink has forwarded this many bytes to the real filesystem. */
        val failAfterBytes: Long? = null,
    ) {
        init {
            require(failAfterBytes == null || failAfterBytes > 0) {
                "failAfterBytes must be positive"
            }
        }
    }

    @Volatile
    var fault: Fault? = null

    val injectedFailures: Int
        get() = injectionCount

    @Volatile
    private var injectionCount = 0

    // FileSystem.copy is not open. It delegates to source/sink, so fail before either
    // side is opened when this path is selected; all bytes still use the real filesystem.
    override fun source(file: Path): Source {
        inject(Operation.COPY, source = file)
        return super.source(file)
    }

    override fun sink(file: Path, mustCreate: Boolean): Sink {
        val selected = fault
        if (selected == null || !matches(selected, Operation.COPY, target = file) ||
            selected.failAfterBytes == null
        ) {
            inject(Operation.COPY, target = file)
            return super.sink(file, mustCreate)
        }

        val failAfterBytes = selected.failAfterBytes
        return object : ForwardingSink(super.sink(file, mustCreate)) {
            private var bytesWritten = 0L

            override fun write(source: Buffer, byteCount: Long) {
                super.write(source, byteCount)
                bytesWritten += byteCount
                if (bytesWritten >= failAfterBytes) {
                    fault = null
                    injectionCount++
                    throw IOException("injected COPY failure after $bytesWritten bytes")
                }
            }
        }
    }

    override fun delete(path: Path, mustExist: Boolean) {
        inject(Operation.DELETE, source = path)
        super.delete(path, mustExist)
    }

    override fun atomicMove(source: Path, target: Path) {
        inject(Operation.RENAME, source, target)
        super.atomicMove(source, target)
    }

    private fun inject(operation: Operation, source: Path? = null, target: Path? = null) {
        val selected = fault ?: return
        if (!matches(selected, operation, source, target)) return
        fault = null
        injectionCount++
        throw IOException("injected ${operation.name} failure")
    }

    private fun matches(
        fault: Fault,
        operation: Operation,
        source: Path? = null,
        target: Path? = null,
    ): Boolean {
        if (fault.operation != operation) return false
        if (fault.source != null && fault.source != source) return false
        if (fault.target != null && fault.target != target) return false
        return true
    }
}
