package com.amcentral365.service

import mu.KLogging

class LeakWatcher {
    companion object: KLogging()

    data class AllocRecord(val x: String? = null) {
        val threadName: String
        val allocTs: Long
        val callStack: Array<StackTraceElement>

        init {
            this.threadName = Thread.currentThread().name
            this.allocTs = System.currentTimeMillis()
            this.callStack = Thread.currentThread().stackTrace
        }
    }

    private val allocations = mutableMapOf<Any, AllocRecord>()

    val allocateCount: Int get() = this.allocations.size

    fun allocated(resource: Any) {
        synchronized(allocations) {
            if(resource in allocations)
                throw StatusException(500, "double-allocation of $resource")
            allocations[resource] = AllocRecord()
        }
    }

    fun released(resource: Any) {
        synchronized(allocations) {
            if(resource !in allocations)
                throw StatusException(500, "release of unknown resource $resource")
            allocations.remove(resource)
        }
    }

    fun dump(withCallStacks: Boolean = false) {
        synchronized(allocations) {
            for(e in this.allocations.entries.sortedByDescending { e -> e.value.allocTs }) {
                logger.info { "${e.key}:  at ${e.value.allocTs} by ${e.value.threadName}" }
                if( withCallStacks )
                    for(ste in e.value.callStack)
                        with(ste) {
                            logger.info { "  $className.$methodName ($fileName:$lineNumber)" }
                        }
            }
        }
    }
}
