package com.amcentral365.service

import mu.KLogging
import mu.KotlinLogging

import java.sql.Blob
import java.sql.CallableStatement
import java.sql.Clob
import java.sql.Connection
import java.sql.DatabaseMetaData
import java.sql.NClob
import java.sql.PreparedStatement
import java.sql.SQLWarning
import java.sql.SQLXML
import java.sql.Savepoint
import java.sql.Statement
import java.sql.Struct
import java.util.Properties
import java.util.concurrent.Executor

private val logger = KotlinLogging.logger {}

class DatabaseConnection(var conn: Connection): Connection {

    companion object {
        val connectionLeakWatcher = LeakWatcher()
    }

    init {
        connectionLeakWatcher.allocated(conn)
        logger.debug { "obtained db connection: $conn" }
    }

    override fun commit() = conn.commit()

    override fun setSavepoint(): Savepoint = conn.setSavepoint()
    override fun setSavepoint(name: String?): Savepoint = conn.setSavepoint(name)
    override fun releaseSavepoint(savepoint: Savepoint?) { conn.releaseSavepoint(savepoint) }
    override fun rollback() = conn.rollback()
    override fun rollback(savepoint: Savepoint?) = conn.rollback(savepoint)

    override fun abort(executor: Executor?) = conn.abort(executor)

    override fun getTransactionIsolation(): Int = conn.transactionIsolation
    override fun setTransactionIsolation(level: Int) { conn.transactionIsolation = level }

    override fun setAutoCommit(autoCommit: Boolean) { conn.autoCommit = autoCommit }
    override fun getAutoCommit(): Boolean = conn.autoCommit

    override fun getWarnings(): SQLWarning = conn.warnings
    override fun clearWarnings() = conn.clearWarnings()

    override fun setReadOnly(readOnly: Boolean) { conn.isReadOnly = readOnly }
    override fun isReadOnly(): Boolean = conn.isReadOnly
    override fun isValid(timeout: Int): Boolean = conn.isValid(timeout)
    override fun isClosed(): Boolean = conn.isClosed

    override fun close() {
        logger.debug { "releasing db connection $conn" }
        conn.close()
        connectionLeakWatcher.released(conn)
    }

    override fun getNetworkTimeout(): Int = conn.networkTimeout
    override fun setNetworkTimeout(executor: Executor?, milliseconds: Int) = conn.setNetworkTimeout(executor, milliseconds)

    override fun isWrapperFor(iface: Class<*>?): Boolean = conn.isWrapperFor(iface)
    override fun <T: Any?> unwrap(iface: Class<T>?): T = conn.unwrap(iface)
    override fun nativeSQL(sql: String?): String = conn.nativeSQL(sql)

    override fun getMetaData(): DatabaseMetaData = conn.metaData

    override fun getClientInfo(name: String?): String = conn.getClientInfo(name)
    override fun getClientInfo(): Properties = conn.clientInfo
    override fun setClientInfo(name: String?, value: String?) { conn.clientInfo[name] = value }
    override fun setClientInfo(properties: Properties?) { conn.clientInfo = properties }

    override fun getCatalog(): String = conn.catalog
    override fun setCatalog(catalog: String?) { conn.catalog = catalog }

    override fun getSchema(): String = conn.schema
    override fun setSchema(schema: String?) { conn.schema = schema }

    override fun getTypeMap(): MutableMap<String, Class<*>> = conn.typeMap
    override fun setTypeMap(map: MutableMap<String, Class<*>>?) { conn.typeMap = map }

    override fun getHoldability(): Int = conn.holdability
    override fun setHoldability(holdability: Int) { conn.holdability = holdability }

    override fun createNClob(): NClob = conn.createNClob()
    override fun createBlob(): Blob = conn.createBlob()
    override fun createArrayOf(typeName: String?, elements: Array<out Any>?): java.sql.Array = conn.createArrayOf(typeName, elements)
    override fun createStruct(typeName: String?, attributes: Array<out Any>?): Struct = conn.createStruct(typeName, attributes)
    override fun createClob(): Clob = conn.createClob()
    override fun createSQLXML(): SQLXML = conn.createSQLXML()

    override fun createStatement(): Statement = conn.createStatement()
    override fun createStatement(resultSetType: Int, resultSetConcurrency: Int): Statement
        = conn.createStatement(resultSetType, resultSetConcurrency)
    override fun createStatement(resultSetType: Int, resultSetConcurrency: Int, resultSetHoldability: Int): Statement
        = conn.createStatement(resultSetType, resultSetConcurrency, resultSetHoldability)

    override fun prepareStatement(sql: String?): PreparedStatement = conn.prepareStatement(sql)
    override fun prepareStatement(sql: String?, resultSetType: Int, resultSetConcurrency: Int): PreparedStatement
        = conn.prepareStatement(sql, resultSetType, resultSetConcurrency)
    override fun prepareStatement(sql: String?, resultSetType: Int, resultSetConcurrency: Int, resultSetHoldability: Int): PreparedStatement
        = conn.prepareStatement(sql, resultSetType, resultSetConcurrency, resultSetHoldability)
    override fun prepareStatement(sql: String?, autoGeneratedKeys: Int): PreparedStatement
        = conn.prepareStatement(sql, autoGeneratedKeys)
    override fun prepareStatement(sql: String?, columnIndexes: IntArray?): PreparedStatement
        = conn.prepareStatement(sql, columnIndexes)
    override fun prepareStatement(sql: String?, columnNames: Array<out String>?): PreparedStatement
        = conn.prepareStatement(sql, columnNames)
    override fun prepareCall(sql: String?): CallableStatement = conn.prepareCall(sql)
    override fun prepareCall(sql: String?, resultSetType: Int, resultSetConcurrency: Int): CallableStatement
        = conn.prepareCall(sql, resultSetType, resultSetConcurrency)
    override fun prepareCall(sql: String?, resultSetType: Int, resultSetConcurrency: Int, resultSetHoldability: Int): CallableStatement
        = conn.prepareCall(sql, resultSetType, resultSetConcurrency, resultSetHoldability)
}
