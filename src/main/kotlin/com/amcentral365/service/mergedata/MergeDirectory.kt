package com.amcentral365.service.mergedata

import com.amcentral365.service.config
import java.nio.file.Paths

import java.io.File
import java.nio.file.Path
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.util.concurrent.ForkJoinPool
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

private const val MERGE_DATA_ROOT_DIR = "mergedata"
private const val PRIORITY_LIST_FILE_NAME = "_priority_list.txt"

class MergeDirectory { companion object {

    fun list(baseDirName: String): List<File> = this.list(Paths.get(MERGE_DATA_ROOT_DIR, baseDirName))

    fun list(baseDirPath: Path): List<File> {
        val priorityList = readPriorityList(baseDirPath)
        val sortedDirTree = sortDirTree(readDirTree(baseDirPath))
        return combineLists(priorityList, sortedDirTree)
    }

    fun combineLists(priorityList: List<String>, files: MutableList<File>): List<File> {
        if( priorityList.isEmpty() )
            return files

        val combinedList = mutableListOf<File>()
        for(pattern in priorityList) {                // O(priorityList size * files size)
            val matchedFileIndexes = matchFiles(files, Regex(pattern))
            combinedList.addAll(matchedFileIndexes.map { idx -> files[idx] })       // scan forward, preserve the order
            matchedFileIndexes.asReversed().forEach { idx -> files.removeAt(idx) }  // reversed, so previous indexes do not shift
        }

        combinedList.addAll(files)      // the remaining
        return combinedList
    }

    private fun matchFiles(files: List<File>, rx: Regex): List<Int> =
            files.withIndex().filter { rx.find(it.value.path) != null }.map { it.index }

    private fun readDirTree(baseDirPath: Path): Sequence<File> {
        val priorityFilePathStr = baseDirPath.resolve(PRIORITY_LIST_FILE_NAME).toFile().path
        return baseDirPath.toFile()
            .walkTopDown()
            .filter { it.isFile }
            .filter { it.path != priorityFilePathStr }
        }


    fun sortDirTree(files: Sequence<File>): MutableList<File> =
        files
            .sortedWith(compareBy(
                    { it.invariantSeparatorsPath.chars().filter{ c -> c.toChar() == '/' }.count() },  // path depth, shorter first
                    { it }  // within the same depth, asc by path
                ))
            .toMutableList()


    private fun readPriorityList(baseDirPath: Path): List<String> {
        return try {
            Files.readAllLines(baseDirPath.resolve(PRIORITY_LIST_FILE_NAME))
                    .map { it.trim() }
                    .filter { it.isNotEmpty() && !it.startsWith('#') }
        } catch(_: NoSuchFileException) {
            emptyList()
        }
    }


    data class Stats(val all: Int) {
        var processed = AtomicInteger(0)
    }

    fun process(files: List<File>, processFile: (file: File, processedFiles: MutableSet<File>, stats: Stats) -> Unit ): Stats {
        val stats = Stats(files.size)
        if( files.isEmpty() )
            return stats

        val processedFiles = mutableSetOf<File>()
        val pool = ForkJoinPool(Math.min(config.mergeThreads, files.size))
        val filetask = { file: File -> processFile(file, processedFiles, stats) }

        if( config.mergeThreads == 1 )
            files.forEach(filetask)
        else {
            if( config.mergeTimeLimitSec <= 0 )
                pool.submit { files.parallelStream().forEach(filetask)}.get()
            else
                pool.submit { files.parallelStream().forEach(filetask)}.get(config.mergeTimeLimitSec, TimeUnit.SECONDS)
            }

        return stats
    }

}}
