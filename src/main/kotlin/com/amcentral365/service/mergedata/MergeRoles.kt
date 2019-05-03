package com.amcentral365.service.mergedata

import mu.KotlinLogging
import java.io.File

private val logger = KotlinLogging.logger {}

class MergeRoles { companion object {

    fun merge(baseDirName: String) {
        val id = Thread.currentThread().name
        val files = MergeDirectory.list("roles")
        MergeDirectory.process(files) { file, processedFiles, stats ->
            if( file in processedFiles )
                logger.info { "$id: skipping already processed ${file.path}" }
            else {
                logger.info { "$id: starting processing ${file.path}" }
                process(file)
                logger.info { "$id: finished processing ${file.path}" }
                processedFiles.add(file)
                stats.processed.incrementAndGet()
            }
        }
    }

    private fun process(file: File) {
        val id = Thread.currentThread().name
        logger.info { "$id:  something is happening here" }
    }

}}
