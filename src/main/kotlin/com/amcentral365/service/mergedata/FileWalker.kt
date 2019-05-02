package com.amcentral365.service.mergedata

import java.nio.file.Paths

import java.io.File
import java.nio.file.Files
import java.nio.file.Path

private val MERGE_DATA_ROOT_DIR = "mergedata"
private val PRIORITY_LIST_FILE_NAME = "_priority_list.txt"

class FileWalker { companion object {

    fun walk(baseDirName: String): Iterator<String> = this.walk(Paths.get(MERGE_DATA_ROOT_DIR, baseDirName))

    fun walk(baseDirPath: Path): Iterator<String> {
        val priorityList = Files.readAllLines(baseDirPath.resolve(PRIORITY_LIST_FILE_NAME))
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith('#') }

        val sortedDirTree = sortDirTree(readDirTree(baseDirPath))

        val combinedList = combineLists(priorityList, sortedDirTree)

        return MergeItemIterator(baseDirPath, priorityList)
    }

    fun combineLists(priorityList: List<String>, files: MutableList<File>): List<File> {
        if( priorityList.isEmpty() )
            return files

        val combinedList = mutableListOf<File>()
        for(pattern in priorityList) {                // O(priorityList size * files size)
            val rx = Regex(pattern)
            val matchedFileIndexes = matchFiles(files, rx)
            combinedList.addAll(matchedFileIndexes.map { idx -> files[idx] })       // scan forward, preserve the order
            matchedFileIndexes.asReversed().forEach { idx -> files.removeAt(idx) }  // reversed, so previous indexes do not shift
        }

        combinedList.addAll(files)
        return combinedList
    }

    fun readDirTree(baseDirPath: Path): Sequence<File> =
        baseDirPath.toFile()
            .walkTopDown()
            .filter { it.isFile }

    fun sortDirTree(files: Sequence<File>): MutableList<File> =
        files
            .sortedWith(compareBy(
                    { it.invariantSeparatorsPath.chars().filter{ it.toChar() == '/' }.count()},  // path depth, shorter first
                    { it }  // within the same depth, asc by name including the path
                ))
            .toMutableList()


    fun matchFiles(files: List<File>, rx: Regex): List<Int> =
            files.withIndex().filter { rx.find(it.value.name) != null }.map { it.index }

    class MergeItemIterator(val baseDirPath: Path, val priorityList: List<String>): Iterator<String> {
        val priorityListIterator = priorityList.listIterator()
        var dirWalkIterator: Iterator<File>? = null

        override fun hasNext(): Boolean {
            if( priorityListIterator.hasNext() )
                return true

            if( dirWalkIterator == null )
                dirWalkIterator = openDirWalkIterator()

            return dirWalkIterator!!.hasNext()
        }

        override fun next(): String {
            if( priorityListIterator.hasNext() )
                return priorityListIterator.next()

            if( dirWalkIterator == null )
                dirWalkIterator = openDirWalkIterator()

            return dirWalkIterator!!.next().readText()
        }

        private fun openDirWalkIterator(): Iterator<File> = baseDirPath.toFile().walkBottomUp().sorted().iterator()
    }


}}
