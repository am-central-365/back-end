package com.amcentral365.service

import com.amcentral365.service.builtins.roles.ExecutionTarget
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions

import java.io.InputStream
import java.io.OutputStream

import com.amcentral365.service.builtins.roles.Script


internal class ReceiverHostTest {

    private class TestExecutionTargetHost: ExecutionTarget(null) {
        var baseDir: String? = "baseDirX"

        var existsCalls = 0
        var copyExecutableFileCalls = 0
        var createDirectoriesCalls = 0
        var copyFileCalls = 0

        override fun exists(pathStr: String): Boolean {
            existsCalls++
            return pathStr == "pass"
        }

        override fun copyExecutableFile(contentStream: InputStream, fileName: String): Long {
            copyExecutableFileCalls++
            return 0
        }

        override fun copyFile(contentStream: InputStream, fileName: String): Long {
            copyFileCalls++
            return 0
        }

        override fun createDirectories(dirPath: String) {
            createDirectoriesCalls++
        }

        // not needed
        override fun realExec(commands: List<String>, inputStream: InputStream?, outputStream: OutputStream): StatusMessage = StatusMessage.OK
        override fun connect(): Boolean = false
        override fun prepare(script: Script): Boolean = false
        override fun disconnect() {}
    }

    private fun assertCalls(
            target: TestExecutionTargetHost,
            existsCalls: Int = 0,
            copyFileCalls: Int = 0,
            copyExecutableFileCalls: Int = 0,
            createDirectoriesCalls: Int = 0
    ) {
        Assertions.assertEquals(existsCalls, target.existsCalls)
        Assertions.assertEquals(copyFileCalls, target.copyFileCalls)
        Assertions.assertEquals(copyExecutableFileCalls, target.copyExecutableFileCalls)
        Assertions.assertEquals(createDirectoriesCalls, target.createDirectoriesCalls)
    }

    private val script = Script(targetRoleName = "roleX", location = null, scriptMain = null, scriptArgs = null)
    /*@BeforeEach
    fun before() {
        script.assignMain("testScript")
    }*/

    @Test
    fun `verify file exists - positive`() {
        val target = TestExecutionTargetHost()
        val host = ReceiverHost(script, target)

        val item = TransferManager.Item("pass", verifyPathExists = true)
        host.apply(item)
        assertCalls(target, existsCalls = 1)
    }

    @Test
    fun `verify file exists - negative`() {
        val target = TestExecutionTargetHost()
        val host = ReceiverHost(script, target)

        val item = TransferManager.Item("no-pass", verifyPathExists = true)
        val e = org.junit.jupiter.api.assertThrows<StatusException> { host.apply(item) }
        Assertions.assertEquals(404, e.code)
        assertCalls(target, existsCalls = 1)
    }


    @Test
    fun `inline - happy path`() {
        val script = Script(targetRoleName = "roleX", location = null, scriptMain = null, scriptArgs = null)
        val target = TestExecutionTargetHost()
        val host = ReceiverHost(script, target)

        val item = TransferManager.Item("", "contentX".byteInputStream())
        host.apply(item)
        Assertions.assertTrue(script.hasMain)
        assertCalls(target, copyExecutableFileCalls = 1)
    }

    @Test
    fun `inline - has main`() {
        val script = Script(targetRoleName = "roleX", location = null, scriptMain = null, scriptArgs = null)
        script.assignMain("mainX")
        val target = TestExecutionTargetHost()
        val host = ReceiverHost(script, target)

        val item = TransferManager.Item("", "contentX".byteInputStream())
        val e = org.junit.jupiter.api.assertThrows<StatusException> { host.apply(item) }
        Assertions.assertEquals(412, e.code)
        Assertions.assertTrue("both main and the inline content" in e.message!!)
        assertCalls(target)
    }


    @Test
    fun `inline - no content`() {
        val script = Script(targetRoleName = "roleX", location = null, scriptMain = null, scriptArgs = null)
        val target = TestExecutionTargetHost()
        val host = ReceiverHost(script, target)

        val item = TransferManager.Item("", null)
        val e = org.junit.jupiter.api.assertThrows<StatusException> { host.apply(item) }
        Assertions.assertEquals(412, e.code)
        Assertions.assertTrue("there is no input" in e.message!!)
        assertCalls(target)
    }


    @Test
    fun `directory - happy path`() {
        val script = Script(targetRoleName = "roleX", location = null, scriptMain = null, scriptArgs = null)
        val target = TestExecutionTargetHost()
        val host = ReceiverHost(script, target)

        val item = TransferManager.Item("dirX", isDirectory = true)
        host.apply(item)
        assertCalls(target, createDirectoriesCalls = 1)
    }

    //@Test
    fun `directory - no base dir`() {
        val script = Script(targetRoleName = "roleX", location = null, scriptMain = null, scriptArgs = null)
        val target = TestExecutionTargetHost()
        val host = ReceiverHost(script, target)
        target.baseDir = null

        val item = TransferManager.Item("dirX", isDirectory = true)
        val e = org.junit.jupiter.api.assertThrows<StatusException> { host.apply(item) }
        Assertions.assertEquals(500, e.code)
        Assertions.assertTrue("does not define base directory" in e.message!!)
        assertCalls(target)
    }

    @Test
    fun `directory - blank dir path`() {
        val script = Script(targetRoleName = "roleX", location = null, scriptMain = null, scriptArgs = null)
        val target = TestExecutionTargetHost()
        val host = ReceiverHost(script, target)

        val item = TransferManager.Item(" ", isDirectory = true)
        val e = org.junit.jupiter.api.assertThrows<StatusException> { host.apply(item) }
        Assertions.assertEquals(412, e.code)
        Assertions.assertTrue("empty directory" in e.message!!)
        assertCalls(target)
    }

    @Test
    fun `directory - absolute path`() {
        val script = Script(targetRoleName = "roleX", location = null, scriptMain = null, scriptArgs = null)
        val target = TestExecutionTargetHost()
        val host = ReceiverHost(script, target)

        val item = TransferManager.Item("/absDirX", isDirectory = true)
        val e = org.junit.jupiter.api.assertThrows<StatusException> { host.apply(item) }
        Assertions.assertEquals(412, e.code)
        Assertions.assertTrue("Absolute paths" in e.message!!)
        assertCalls(target)
    }


    @Test
    fun `file - happy path`() {
        val script = Script(targetRoleName = "roleX", location = null, scriptMain = null, scriptArgs = null)
        val target = TestExecutionTargetHost()
        val host = ReceiverHost(script, target)

        val item = TransferManager.Item("fileX", "contentX".byteInputStream())
        host.apply(item)
        assertCalls(target, copyFileCalls = 1)
    }

    //@Test
    fun `file - no base dir`() {
        val script = Script(targetRoleName = "roleX", location = null, scriptMain = null, scriptArgs = null)
        val target = TestExecutionTargetHost()
        val host = ReceiverHost(script, target)

        target.baseDir = null
        val item = TransferManager.Item("fileX", "contentX".byteInputStream())
        val e = org.junit.jupiter.api.assertThrows<StatusException> { host.apply(item) }
        Assertions.assertEquals(500, e.code)
        Assertions.assertTrue("does not define the base directory" in e.message!!)
        assertCalls(target)
    }

    @Test
    fun `file - no content`() {
        val script = Script(targetRoleName = "roleX", location = null, scriptMain = null, scriptArgs = null)
        val target = TestExecutionTargetHost()
        val host = ReceiverHost(script, target)

        val item = TransferManager.Item("fileX")
        val e = org.junit.jupiter.api.assertThrows<StatusException> { host.apply(item) }
        Assertions.assertEquals(400, e.code)
        Assertions.assertTrue("The input stream is required" in e.message!!)
        assertCalls(target)
    }

    @Test
    fun `file - absolute path`() {
        val script = Script(targetRoleName = "roleX", location = null, scriptMain = null, scriptArgs = null)
        val target = TestExecutionTargetHost()
        val host = ReceiverHost(script, target)

        val item = TransferManager.Item("/fileX", "contentX".byteInputStream())
        val e = org.junit.jupiter.api.assertThrows<StatusException> { host.apply(item) }
        Assertions.assertEquals(412, e.code)
        Assertions.assertTrue("Absolute paths" in e.message!!)
        assertCalls(target)
    }

    @Test
    fun `file - blank 2nd path`() {
        val script = Script(targetRoleName = "roleX", location = null, scriptMain = null, scriptArgs = null)
        val target = TestExecutionTargetHost()
        val host = ReceiverHost(script, target)

        // 1st is ok
        val item1 = TransferManager.Item("fileX", "contentX".byteInputStream())
        host.apply(item1)
        assertCalls(target, copyFileCalls = 1)

        // blank paths in 2nd are not allowed
        val item2 = TransferManager.Item(" ", "contentX".byteInputStream())
        val e = org.junit.jupiter.api.assertThrows<StatusException> { host.apply(item2) }
        Assertions.assertEquals(412, e.code)
        Assertions.assertTrue("both inline and regular files" in e.message!!) { "message: ${e.message}" }
        assertCalls(target, copyFileCalls = 1)
    }

    @Test
    fun `file - no main`() {
        val script = Script(targetRoleName = "roleX", location = null, scriptMain = null, scriptArgs = null)
        val target = TestExecutionTargetHost()
        val host = ReceiverHost(script, target)

        // 1st is ok
        val item1 = TransferManager.Item("fileX", "contentX".byteInputStream())
        host.apply(item1)
        assertCalls(target, copyFileCalls = 1)

        // blank paths in 2nd are not allowed
        val item2 = TransferManager.Item("fileY", "contentY".byteInputStream())
        val e = org.junit.jupiter.api.assertThrows<StatusException> { host.apply(item2) }
        Assertions.assertEquals(400, e.code)
        Assertions.assertTrue("more than one file and no 'main'" in e.message!!) { "message: ${e.message}" }
        assertCalls(target, copyFileCalls = 1)
    }
}
