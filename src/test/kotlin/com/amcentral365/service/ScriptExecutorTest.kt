package com.amcentral365.service

import com.amcentral365.service.builtins.roles.Script
import com.amcentral365.service.dao.Asset

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

import java.io.InputStream
import java.io.OutputStream

val BAD_STATUS = 666

internal class ScriptExecutorTest {

    class TestScriptExecutorFlow(
        val connectThrows: Boolean = false,
        val connectRetval: Boolean = true,
        val prepareThrows: Boolean = false,
        val prepareRetval: Boolean = true,
        val executeThrows: Boolean = false,
        val executeRetval: StatusMessage = StatusMessage.OK,
        val cleanupThrows: Boolean = false,
        val disconnectThrows: Boolean = false
    ): ScriptExecutorFlow {
        var connectCalls = 0
        var prepareCalls = 0
        var executeCalls = 0
        var cleanupCalls = 0
        var disconnectCalls = 0

        override var asset: Asset?
            get() = null
            set(value) {}

        override fun connect(): Boolean {
            connectCalls += 1
            if( connectThrows )
                throw StatusException(BAD_STATUS, "connect")
            return connectRetval
        }

        override fun prepare(script: Script): Boolean {
            Assertions.assertEquals("testScript", script.name)
            prepareCalls += 1
            if( prepareThrows )
                throw StatusException(BAD_STATUS, "prepare")
            return prepareRetval
        }

        override fun execute(script: Script, outputStream: OutputStream, inputStream: InputStream?): StatusMessage {
            Assertions.assertEquals("testScript", script.name)
            executeCalls += 1
            if( executeThrows )
                throw StatusException(BAD_STATUS, "execute")
            return executeRetval
        }

        override fun cleanup(script: Script) {
            Assertions.assertEquals("testScript", script.name)
            cleanupCalls += 1
            if( cleanupThrows )
                throw StatusException(BAD_STATUS, "cleanup")
        }

        override fun disconnect() {
            disconnectCalls += 1
            if( disconnectThrows )
                throw StatusException(BAD_STATUS, "disconnect")
        }

    }

    val testScript = Script(null, null, null, null, null)

    fun assertCalls(
            testScriptExecutorFlow: TestScriptExecutorFlow,
            connectCalls: Int = 0,
            prepareCalls: Int = 0,
            executeCalls: Int = 0,
            cleanupCalls: Int = 0,
            disconnectCalls: Int = 0
    ) {
        Assertions.assertEquals(connectCalls, testScriptExecutorFlow.connectCalls)
        Assertions.assertEquals(prepareCalls, testScriptExecutorFlow.prepareCalls)
        Assertions.assertEquals(executeCalls, testScriptExecutorFlow.executeCalls)
        Assertions.assertEquals(cleanupCalls, testScriptExecutorFlow.cleanupCalls)
        Assertions.assertEquals(disconnectCalls, testScriptExecutorFlow.disconnectCalls)
    }

    @BeforeEach
    fun before() {
        testScript.asset = Asset("testScript")
    }

    @Test
    fun `run - happy path`() {
        val flow = TestScriptExecutorFlow()
        val executor = ScriptExecutor("test")
        executor.run(testScript, flow, StringOutputStream())
        assertCalls(flow, 1, 1, 1, 1, 1)
    }

    @Test
    fun `run - connect - throws`() {
        val flow = TestScriptExecutorFlow(connectThrows = true)
        val executor = ScriptExecutor("test")
        val e = assertThrows<StatusException> { executor.run(testScript, flow, StringOutputStream()) }
        Assertions.assertEquals(500, e.code)
        Assertions.assertEquals("com.amcentral365.service.StatusException: connect", e.message)
        Assertions.assertEquals(BAD_STATUS, (e.cause as StatusException).code)
        assertCalls(flow, connectCalls = 1)
    }

    @Test
    fun `run - connect - failure`() {
        val flow = TestScriptExecutorFlow(connectRetval = false)
        val executor = ScriptExecutor("test")
        executor.run(testScript, flow, StringOutputStream())
        assertCalls(flow, connectCalls = 1)
    }


    @Test
    fun `run - prepare - throws`() {
        val flow = TestScriptExecutorFlow(prepareThrows = true)
        val executor = ScriptExecutor("test")
        val e = assertThrows<StatusException> { executor.run(testScript, flow, StringOutputStream()) }
        Assertions.assertEquals(500, e.code)
        Assertions.assertEquals("com.amcentral365.service.StatusException: prepare", e.message)
        Assertions.assertEquals(BAD_STATUS, (e.cause as StatusException).code)
        assertCalls(flow, connectCalls = 1, prepareCalls = 1, disconnectCalls = 1)
    }

    @Test
    fun `run - prepare - failure`() {
        val flow = TestScriptExecutorFlow(prepareRetval = false)
        val executor = ScriptExecutor("test")
        executor.run(testScript, flow, StringOutputStream())
        assertCalls(flow, connectCalls = 1, prepareCalls = 1, disconnectCalls = 1)
    }


    @Test
    fun `run - execute - throws`() {
        val flow = TestScriptExecutorFlow(executeThrows = true)
        val executor = ScriptExecutor("test")
        val e = assertThrows<StatusException> { executor.run(testScript, flow, StringOutputStream()) }
        Assertions.assertEquals(500, e.code)
        Assertions.assertEquals("com.amcentral365.service.StatusException: execute", e.message)
        Assertions.assertEquals(BAD_STATUS, (e.cause as StatusException).code)
        assertCalls(flow, 1, 1, 1, 1, 1)
    }

    @Test
    fun `run - execute - failure`() {
        val failMsg = StatusMessage(BAD_STATUS+1, "execute failed")
        val flow = TestScriptExecutorFlow(executeRetval = failMsg)
        val executor = ScriptExecutor("test")
        val retMsg = executor.run(testScript, flow, StringOutputStream())
        Assertions.assertEquals(failMsg, retMsg)
        assertCalls(flow, 1, 1, 1, 1, 1)
    }


    @Test
    fun `run - cleanup - throws`() {
        val flow = TestScriptExecutorFlow(cleanupThrows = true)
        val executor = ScriptExecutor("test")
        val e = assertThrows<StatusException> { executor.run(testScript, flow, StringOutputStream()) }
        Assertions.assertEquals(500, e.code)
        Assertions.assertEquals("com.amcentral365.service.StatusException: cleanup", e.message)
        Assertions.assertEquals(BAD_STATUS, (e.cause as StatusException).code)
        assertCalls(flow, 1, 1, 1, 1, 1)
    }


    @Test
    fun `run - disconnect - throws`() {
        val flow = TestScriptExecutorFlow(disconnectThrows = true)
        val executor = ScriptExecutor("test")
        executor.run(testScript, flow, StringOutputStream())    // disconnect exceptions are logged but ignored
        assertCalls(flow, 1, 1, 1, 1, 1)
    }

}
