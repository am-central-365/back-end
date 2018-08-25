package com.amcentral365.service

import com.amcentral365.pl4kotlin.*

import mu.KLogging
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException


class DatabaseStore {
    companion object: KLogging()

    internal fun getGoodConnection(): Connection {
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


    private fun isNullOrBlank(colDef: Entity.ColDef?): Boolean {
        if( colDef == null )
            return true
        val v = colDef.getValue()
        return v == null || v.toString().isBlank()
    }

    /**
     * Fetch rows from a single table as list of objects, matching present properties of the [entity] filter.
     *
     * @param entity the search filter. Fields with non-null non-empty values are used as "=" filter
     *               to the WHERE clause. They are joined with ANDs.
     * @param orderBy the SQL order by clause. <code>ORDER BY column_list</code>
     * @return List of fetched objects. On error, stops and returns whatever gotten so far.
     */
    fun fetchRowsAsObjects(entity: Entity, orderByExpr: String? = null, limit: Int = 0): List<Entity> {
        val selStmt = SelectStatement(entity).select(entity.allCols).byPresentValues()
        if( orderByExpr != null && orderByExpr.isNotBlank() )
            selStmt.orderBy(expr=orderByExpr)

        val fetchLimit = if( limit > 0 ) limit else Int.MAX_VALUE
        val conn = this.getGoodConnection()
        return selStmt.iterate(conn).asSequence().take(fetchLimit).toList()
    }

    /**
     * Insert or update a row in a database table, as defined by the [entity] instance.
     *
     * When [entity] PK is null, performs an INSERT, otherwise UPDATE.
     * This isn't generic, but suits our database setup.
     * All updates are performed with optimistic lock. It is an error to run UPDATE
     * with a null/empty OptLock column value.
     *
     * @return HTTP code and the corresponding message, to return to the user.
     * @throws The function doesn't throw exceptions explicitly
     */
    internal fun mergeObjectAsRow(entity: Entity): StatusMessage {
        val inserting = entity.pkCols.all { it.getValue() == null }
        val identityStr: String?
        val conn = this.getGoodConnection()

        try {
            if( inserting ) {
                logger.info { "inserting into ${entity.tableName}" }
                val cnt = InsertStatement(entity).run(conn)
                if( cnt == 0 )
                    return StatusMessage(500, "There was no error, but the record was not inserted")
                identityStr = entity.getIdentityAsJsonStr()
            } else {
                logger.info { "updating ${entity.tableName}" }
                if( this.isNullOrBlank(entity.optLockCol) )
                    return StatusMessage(400, "Updates require an optimistic lock which was not provided or is null")

                val cnt = UpdateStatement(entity)
                            .update(entity.allColsButPkAndOptLock!!
                                          .filter { it.getValue() != null && it.getValue().toString().isNotBlank() })
                            .withOptLock()
                            .byPkAndOptLock()
                            .run(conn)
                if( cnt == 0 )
                    return StatusMessage(410, "No row was updated: either it does not exist, or its OptLock was modified")

                identityStr = entity.getIdentityAsJsonStr()
            }

            conn.commit()
            logger.info { "${if( inserting ) "insert" else "update"} ok, returning $identityStr" }
            return StatusMessage(200, identityStr)

        } catch(x: SQLException) {
            conn.rollback()
            return StatusMessage(x)
        } finally {
            closeIfCan(conn)
        }
    }

    /**
     * Delete a database row, corresponding to the supplied object
     *
     * The operation requires all PL values and the OptLock column to be present.
     * It doesn'r work w/o OptLock.
     */
    internal fun deleteObjectRow(entity: Entity): StatusMessage {
        logger.info { "deleting from ${entity.tableName} by ${entity.getIdentityAsJsonStr()}" }

        try {
            if( this.isNullOrBlank(entity.optLockCol) || entity.pkCols.any { this.isNullOrBlank(it) } )
                return StatusMessage(400, "all PK columns and OptLock column must be supplied to Delete")

            val cnt = DeleteStatement(entity, ::getGoodConnection).byPkAndOptLock().run()
            if (cnt == 0)
                return StatusMessage(410, "either the record does not exist, or it's been modified")
        } catch(x: SQLException) {
            return StatusMessage(x)
        }

        return StatusMessage.OK
    }
}