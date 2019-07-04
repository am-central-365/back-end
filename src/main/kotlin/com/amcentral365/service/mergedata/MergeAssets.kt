package com.amcentral365.service.mergedata

import com.amcentral365.pl4kotlin.InsertStatement
import com.amcentral365.pl4kotlin.SelectStatement
import com.amcentral365.pl4kotlin.UpdateStatement
import com.amcentral365.pl4kotlin.closeIfCan
import com.amcentral365.service.StatusException
import com.amcentral365.service.api.SchemaUtils
import mu.KotlinLogging

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException

import java.io.File
import java.io.IOException

import java.lang.UnsupportedOperationException

import com.amcentral365.service.config
import com.amcentral365.service.dao.Asset
import com.amcentral365.service.dao.AssetRoleValues
import com.amcentral365.service.databaseStore

import com.google.common.annotations.VisibleForTesting
import com.google.common.base.Preconditions
import java.sql.Connection

import java.sql.SQLException
import java.util.UUID
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.getOrSet
import kotlin.concurrent.write


private val logger = KotlinLogging.logger {}


open class MergeAssets(private val baseDirName: String) {

    // All variables are shared between threads
    private val gson = Gson()  // thread safe
    private var processedFiles = mutableSetOf<File>()   // read and updated by all threads
    private lateinit var files: List<File>   // set and populated by `merge` once, read by all threads
    private val schemaUtils = SchemaUtils(500)

    private val openedConnections = mutableListOf<Connection>()
    private val openedConnectionsLock = ReentrantReadWriteLock()

    // These variables are local to the thread
    private val conn = ThreadLocal<Connection>()

    /**
     * Merge asset definitions into the database
     *
     * Read asset definition files under the specified directory (defaulting to mergedata/roles),
     * and merge (add or update) assets into the database table <code>ASSETS</code>.
     *
     * The files are processed with parallelism --merge-threads.
     *
     * @return the number of failures
     */
    fun merge(): Int {
        this.files = MergeDirectory.list(baseDirName)
        val stats = MergeDirectory.process(this.files) { file, stats ->
            // This code may execute in parallel
            // It is the onluy code responsible for managing the transaction state

            if( file in processedFiles )
                logger.info { "skipping already processed ${file.path}" }
            else {
                this.conn.getOrSet { initThreadConnection() }
                this.conn.get().rollback()      // promote code quality by discarding uncommitted changes

                logger.debug { "starting processing ${file.path}" }
                val succeeded = this.process(file, stats)

                logger.debug { "finished processing ${file.path} with ${if(succeeded) "success" else "failure"}" }
                this.processedFiles.add(file)

                if( succeeded ) {
                    this.conn.get().commit()
                    stats.processed.incrementAndGet()
                } else {
                    this.conn.get().rollback()
                    stats.failed.incrementAndGet()
                }
            }
        }

        openedConnections.forEach {
            try { it.rollback() } catch(x: SQLException) { /* let it slide */ }
            closeIfCan(it)
        }

        with(stats) {
            logger.info { "Merge completed for $totalConsidered assets and role values with $processed processed successfully and $failed failed" }
            logger.info { "  process stats: added $inserted, updated $updated, and skipped $unchanged unchanged" }
            logger.info { "  (this low level stats treats Assets and each of their AssetRoleValues individually)" }
        }

        return stats.failed.get()
    }

    // Called from threads. Should not write the object state unless protected by a lock
    // Manages transaction state
    private fun process(file: File, stats: MergeDirectory.Companion.Stats): Boolean {
        return when(val ext = file.extension.toLowerCase()) {
            "jsn", "json" -> processJson(file, stats)
            "yml", "yaml" -> processYaml(file, stats)
            "xml"         -> processXml(file, stats)
            else -> {
                logger.info { "unsupported extension $ext, supported extensions are: jsn, json, yml, yaml, xml" }
                false
            }
        }
    }


    private fun readFile(file: File): String = file.readText(config.charSet)

    private fun parseJson(content: String): Map<String, Any> = gson.fromJson<Map<String, Any>>(content, Map::class.java)

    private fun roleNameToInvariantPath(roleName: String) =
            "$MERGE_DATA_ROOT_DIR/$baseDirName/" + roleName.replace('.', '/')


    @VisibleForTesting fun findRoleFile(roleName: String, files: List<File>): File? =
        this.roleNameToInvariantPath(roleName).let { rolePath ->
            files.find {
                it.invariantSeparatorsPath.substring(0, it.invariantSeparatorsPath.lastIndexOf('.')) == rolePath
            }
        }


    private fun serializeToJsonObj(v: Any): String = gson.toJson(v)

    private fun jsonsMatch(js1: String, js2: String): Boolean =
            parseJson(js1) == parseJson(js2)


    private fun initThreadConnection(): Connection {
        val c = databaseStore.getGoodConnection()
        this.openedConnectionsLock.write {
            this.openedConnections.add(c)
        }
        return c
    }

    data class PermissiveRoleValues(val roleName: String, var values: Map<String, Any>?)
    data class PermissiveAssetWithRoles(val asset: Asset, var roles: List<PermissiveRoleValues>?)

    data class RoleValues(val roleName: String, val valuesAsJsonStr: String)
    data class AssetWithRoles(val asset: Asset, val roles: List<RoleValues>)

    private fun readAssetObjectFromFile(file: File): AssetWithRoles {
        val fileText = readFile(file)
        val fileAsset = gson.fromJson(fileText, PermissiveAssetWithRoles::class.java)

        val problem = checkAssetIdAndName(fileAsset.asset)
        if( problem != null )
            throw StatusException(412, problem)

        if( fileAsset.roles == null )
            fileAsset.roles = emptyList()

        val collectedRoles = mutableListOf<RoleValues>()
        for(rval in fileAsset.roles!!) {
            if( rval.roleName.isBlank() )
                throw StatusException(412, "element 'roleName' is blank")

            if( rval.values == null )
                rval.values = emptyMap()

            val valsAsString = this.serializeToJsonObj(rval.values!!)

            try {
                this.schemaUtils.validateAssetValue(rval.roleName, valsAsString)
            } catch(x: StatusException) {
                throw StatusException(x, x.code, "in file ${file.path}}, role ${rval.roleName}: ${x.message}")
            }

            collectedRoles.add(RoleValues(rval.roleName, valsAsString))
        }

        return AssetWithRoles(fileAsset.asset, collectedRoles.sortedBy { it.roleName })  // sorting roles protects us from deadlocks
    }


    private fun checkAssetIdAndName(mergeAsset: Asset): String? {
        val mergeAssetIdIsPresent = mergeAsset.assetId != null
        val mergeAssetNameIsPresent = !mergeAsset.name.isNullOrBlank()

        var dbNameFromId: String? = null
        var dbIdFromName: UUID? = null

        val dbAsset = Asset()
        if( mergeAssetIdIsPresent ) {
            logger.info { "  fetching " }
            dbAsset.assetId = mergeAsset.assetId
            val count = SelectStatement(dbAsset).select(dbAsset::name).by(dbAsset::assetId).run(this.conn.get())
            if(count == 1)
                dbNameFromId = dbAsset.name
        }

        if( mergeAssetNameIsPresent ) {
            dbAsset.name = mergeAsset.name
            val count = SelectStatement(dbAsset).select(dbAsset::assetId).by(dbAsset::name).run(this.conn.get())
            if(count == 1)
                dbIdFromName = dbAsset.assetId
        }

        return checkMergeAssetAgainstDb(mergeAsset, dbIdFromName, dbNameFromId)
    }

    @VisibleForTesting
    fun checkMergeAssetAgainstDb(mergeAsset: Asset, dbIdFromName: UUID?, dbNameFromId: String?): String? {
        val mergeAssetIdIsPresent = mergeAsset.assetId != null
        val mergeAssetNameIsPresent = !mergeAsset.name.isNullOrBlank()

        if( mergeAssetIdIsPresent ) {
            if( mergeAssetNameIsPresent ) {
                when(dbIdFromName) {
                    null               -> logger.debug { "  asset with name ${mergeAsset.name} isn't in the database, will insert" }
                    mergeAsset.assetId -> logger.debug { "  database asset id matches the one in the mergefile" }
                    else               -> return "Database id $dbIdFromName of asset ${mergeAsset.name} does not match one in the mergefile: ${mergeAsset.assetId}"
                }

                when(dbNameFromId) {
                    null            -> logger.debug { "  asset with id ${mergeAsset.assetId} isn't in the database, will insert" }
                    mergeAsset.name -> logger.debug { "  database asset name matches the one in the mergefile" }
                    else            -> logger.warn  { "  database asset ${mergeAsset.assetId} will be renamed from $dbNameFromId to ${mergeAsset.name}" }
                }
            } else {
                if( dbIdFromName != null )
                    return "Coding error: database id $dbIdFromName was somehow fetched even though asset name wasn't provided"

                if( dbNameFromId == null )
                    return "Asset ${mergeAsset.assetId} needs to be inserted, but its name was not provided"

                logger.debug { "  using database name $dbNameFromId for asset ${mergeAsset.assetId}" }
                mergeAsset.name = dbNameFromId
            }
        } else if( mergeAssetNameIsPresent ) {
            if( dbIdFromName == null ) {
                mergeAsset.assetId = UUID.randomUUID()
                logger.info { "  generated id ${mergeAsset.assetId} for new asset ${mergeAsset.name}" }
            } else
                mergeAsset.assetId = dbIdFromName

        } else {
            return "neither asset id nor name is present in the mergefile"
        }

        return null
    }


    private fun processJson(file: File, stats: MergeDirectory.Companion.Stats): Boolean {
        logger.info { "processing JSON file ${file.path}" }
        try {
            val assetFromFile = readAssetObjectFromFile(file)

            if( file in this.processedFiles )
                return true

            mergeAssetObjIntoDb(assetFromFile.asset, stats)

            // the roles were sorted by name to protect from deadlocks when multiple workers are trying to merge the same asset
            for(role in assetFromFile.roles) {
                if( file in this.processedFiles )
                    return true

                mergeAssetRoleValuesIntoDb(assetFromFile, role, stats)
            }

        } catch(x: IOException) {
            logger.warn { "  couldn't read ${file.path}: ${x.javaClass.name} ${x.message}" }
            return false
        } catch(x: JsonSyntaxException) {
            logger.warn { "  json parse error in ${file.path}:\n  $x" }
            return false
        } catch(x: Exception) {
            logger.warn { "  error processing ${file.path}:\n  $x" }
            return false
        }

        return true
    }

    // merge into ASSETS
    private fun mergeAssetObjIntoDb(asset: Asset, stats: MergeDirectory.Companion.Stats) {
        Preconditions.checkNotNull(asset)
        Preconditions.checkNotNull(asset.assetId)
        Preconditions.checkNotNull(asset.name)

        val dbAsset = Asset(asset.assetId!!)

        var cnt = SelectStatement(dbAsset).select(dbAsset.allColsButPk!!).byPk().run(this.conn.get())
        if( cnt > 1 )
            throw StatusException(500, "DB integrity compromised: $cnt Asset records for PK ${asset.assetId}")


        if( cnt == 0 ) {
            try {
                cnt = InsertStatement(asset).run(conn.get())
                if( cnt == 1) {
                    stats.inserted.incrementAndGet()
                    logger.info { "inserted into db: asset name ${asset.name}, id ${asset.assetId}" }
                    return
                }
            } catch(x: SQLException) {
                if( x.sqlState != "23000" )    // Duplicate entry XXX for key YYY
                    throw x

                logger.info { "  duplicate key on insert of asset ${asset.name}, id ${asset.assetId} trying to fetch it by id" }
            }

            cnt = SelectStatement(dbAsset).select(dbAsset.allColsButPk).byPk().run(this.conn.get())
            if( cnt != 1 )
                throw StatusException(500, "After a failed insert attempt, couldn't find asset by its id ${asset.assetId}: $cnt records found")
        }

        var changed = false
        val updStmt = UpdateStatement(asset).byPkAndOptLock()

        if( asset.name != dbAsset.name ) {
            changed = true
            updStmt.update(asset::name)
            logger.info { "  asset name differs, marked for rename" }
        }

        if( asset.description != dbAsset.description ) {
            changed = true
            updStmt.update(asset::description)
            logger.info { "  asset description differs, marked for update" }
        }

        if( !changed ) {
            stats.unchanged.incrementAndGet()
            logger.info { "asset hasn't changed, skipping" }
            return
        }

        asset.modifiedTs = dbAsset.modifiedTs
        cnt = updStmt.run(this.conn.get())

        if( cnt != 1 )
            throw StatusException(409, "optlock failure updating asset ${asset.name} with id ${asset.assetId}: $cnt rows updated")

        stats.updated.incrementAndGet()
        logger.info { "updated asset with (maybe new) name ${asset.name} and id ${asset.assetId}" }
    }


    // merge into ASSET_ROLE_VALUES
    private fun mergeAssetRoleValuesIntoDb(assetFromFile: AssetWithRoles, role: RoleValues, stats: MergeDirectory.Companion.Stats) {
        Preconditions.checkNotNull(assetFromFile)
        Preconditions.checkNotNull(assetFromFile.asset)
        Preconditions.checkNotNull(assetFromFile.asset.assetId)
        Preconditions.checkNotNull(role)
        Preconditions.checkNotNull(role.roleName)
        Preconditions.checkNotNull(role.valuesAsJsonStr)

        val mmARV = AssetRoleValues(assetFromFile.asset.assetId!!, role.roleName, role.valuesAsJsonStr)
        val dbARV = AssetRoleValues(assetFromFile.asset.assetId!!, role.roleName)

        var cnt = SelectStatement(dbARV).select(dbARV.allColsButPk!!).byPk().run(this.conn.get())
        if( cnt > 1 )
            throw StatusException(500, "DB integrity compromised: $cnt AssetRoleValues records for PK (${dbARV.assetId}, ${dbARV.roleName})")

        if( cnt == 0 ) {
            try {
                cnt = InsertStatement(mmARV).run(conn.get())
                if( cnt == 1) {
                    stats.inserted.incrementAndGet()
                    logger.info { "inserted into db: asset id ${mmARV.assetId}, role name ${mmARV.roleName}" }
                    return
                }
            } catch(x: SQLException) {
                if( x.sqlState != "23000" )     // Duplicate entry XXX for key 'PRIMARY'
                    throw x

                logger.info { "  duplicate key on insert of AssetRoleValues (${mmARV.assetId}, ${mmARV.roleName}), trying to fetch it by id" }
            }

            cnt = SelectStatement(dbARV).select(dbARV.allColsButPk).byPk().run(this.conn.get())
            if( cnt != 1 )
                throw StatusException(500, "After a failed insert attempt, couldn't find assetRoleValues by its PK: $cnt records found")
        }

        if( jsonsMatch(mmARV.assetVals!!, dbARV.assetVals!!) ) {
            stats.unchanged.incrementAndGet()
            logger.info { "skipped unchanged asset role value (${mmARV.assetId}, ${mmARV.roleName})" }
        } else {
            mmARV.modifiedTs = dbARV.modifiedTs
            cnt = UpdateStatement(mmARV).update(mmARV::assetVals).byPkAndOptLock().run(this.conn.get())
            if( cnt != 1 )
                throw StatusException(409, "optlock failure updating assetRoleVals (${mmARV.assetId}, ${mmARV.roleName}): $cnt rows updated")
            stats.updated.incrementAndGet()
            logger.info { "updated assetRoleVals with PK (${mmARV.assetId}, ${mmARV.roleName})" }
        }
    }


    private fun processYaml(file: File, stats: MergeDirectory.Companion.Stats): Boolean {
        stats.unchanged.incrementAndGet()
        throw UnsupportedOperationException("extension ${file.extension} will be supported later")
    }

    private fun processXml(file: File, stats: MergeDirectory.Companion.Stats): Boolean {
        stats.unchanged.incrementAndGet()
        throw UnsupportedOperationException("extension ${file.extension} will be supported later")
    }
}
