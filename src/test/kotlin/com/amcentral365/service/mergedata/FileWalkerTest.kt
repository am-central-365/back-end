package com.amcentral365.service.mergedata

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Paths

internal class FileWalkerTest {

    val a = File("com.p1.a.yml")
    val b = File("com.p1.b.yml")
    val cp1a = Paths.get("com", "p1", "a.yml").toFile()
    val cp1b = Paths.get("com", "p1", "b.yml").toFile()

    val sortedList = listOf(a, b, cp1a, cp1b)

    private fun toMutable(files: List<File>): MutableList<File> {
        val m = mutableListOf<File>()
        m.addAll(files)
        return m
    }

    @Test
    fun sortDirTreeTest() {
        val actual = FileWalker.sortDirTree(sequenceOf(cp1b, a, cp1a, b))
        Assertions.assertIterableEquals(sortedList, actual)
    }

    @Test
    fun combineListsTest() {
        val actual = FileWalker.combineLists(listOf("b.yml"), toMutable(sortedList))

        Assertions.assertIterableEquals(listOf(b, cp1b, a, cp1a), actual)
    }
}
