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

    private fun cloneAsMutable(files: List<File>): MutableList<File> {
        val m = mutableListOf<File>()
        m.addAll(files)
        return m
    }

    @Test
    fun sortDirTreeTest() {
        val input = cloneAsMutable(sortedList)

        // Start with sorted, then shuffle several times. The outcome should be sorted
        for(x in 1..10) {
            val actual = MergeDirectory.sortDirTree(input.asSequence())
            Assertions.assertIterableEquals(sortedList, actual)

            input.shuffle()
        }
    }

    @Test
    fun `combine - one pattern`() {
        var actual = MergeDirectory.combineLists(listOf("b.yml"), cloneAsMutable(sortedList))
        Assertions.assertIterableEquals(listOf(b, cp1b, a, cp1a), actual)

        actual = MergeDirectory.combineLists(listOf("/b.yml"), cloneAsMutable(sortedList))
        Assertions.assertIterableEquals(listOf(cp1b, a, b, cp1a), actual)
    }

    @Test
    fun `combine - two patterns`() {
        var actual = MergeDirectory.combineLists(listOf("b.yml", "p1/"), cloneAsMutable(sortedList))
        Assertions.assertIterableEquals(listOf(b, cp1b, cp1a, a), actual)

        actual = MergeDirectory.combineLists(listOf("/b.yml", "b.yml"), cloneAsMutable(sortedList))
        Assertions.assertIterableEquals(listOf(cp1b, b, a, cp1a), actual)
    }

    @Test
    fun `combine - dup patterns`() {
        val actual = MergeDirectory.combineLists(listOf("/p1/", "b.yml"), cloneAsMutable(sortedList))
        Assertions.assertIterableEquals(listOf(cp1a, cp1b, b, a), actual)
    }
}
