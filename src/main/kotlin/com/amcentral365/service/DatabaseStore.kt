package com.amcentral365.service

import com.amcentral365.pl4kotlin.Entity
import com.amcentral365.pl4kotlin.SelectStatement

import mu.KLogging
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException


class DatabaseStore {
    companion object: KLogging()

    private fun getGoodConnection(): Connection {
        while( keepRunning ) {
            try {
                logger.debug { "connecting to ${config.dbUrl} as ${config.dbUsr}" }
                val conn = DriverManager.getConnection(config.dbUrl, config.dbUsr, config.dbPwd)
                conn.autoCommit = false
                conn.transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
                logger.debug { "connected" }
                return conn
            } catch(x: SQLException) {
                logger.warn { "${x.message};  retrying in ${config.DBSTORE_RECONNECT_DELAY_SEC} sec" }
                Thread.sleep(config.DBSTORE_RECONNECT_DELAY_SEC*1000L)
            }
        }

        throw StatusException(500, "the server is shutting down")
    }


    fun fetchRowsAsObjects(entity: Entity, orderByExpr: String? = null, limit: Int = 0): List<Entity> {
        val selStmt = SelectStatement(entity).select(entity.allCols).byPresentValues()
        if( orderByExpr != null && orderByExpr.isNotBlank() )
            selStmt.orderBy(expr=orderByExpr)

        val fetchLimit = if( limit > 0 ) limit else Int.MAX_VALUE
        val conn = this.getGoodConnection()
        return selStmt.iterate(conn).asSequence().take(fetchLimit).toList()
    }
}