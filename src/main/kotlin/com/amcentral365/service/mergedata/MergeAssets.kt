package com.amcentral365.service.mergedata

import com.amcentral365.pl4kotlin.InsertStatement
import com.amcentral365.pl4kotlin.SelectStatement
import com.amcentral365.pl4kotlin.UpdateStatement
import com.amcentral365.service.StatusException
import com.amcentral365.service.api.SchemaUtils
import com.amcentral365.service.api.loadSchemaFromDb
import mu.KotlinLogging

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException

import java.io.File
import java.io.IOException

import java.lang.UnsupportedOperationException

import com.amcentral365.service.config
import com.amcentral365.service.dao.Asset
import com.amcentral365.service.dao.Role
import com.amcentral365.service.databaseStore

import com.google.common.annotations.VisibleForTesting
import com.google.common.base.Predicate

import java.sql.SQLException
import java.util.Stack
import java.util.UUID


private val logger = KotlinLogging.logger {}


open class MergeAssets(private val baseDirName: String) {

    // All variables are shared between threads
    private val gson = Gson()  // thread safe
    private var processedFiles = mutableSetOf<File>()   // read and updated by all threads
    private lateinit var files: List<File>   // set and populated by `merge` once, read by all threads
    private val schemaUtils = SchemaUtils(500)

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
            if( file in processedFiles )
                logger.info { "skipping already processed ${file.path}" }
            else {
                //logger.info { "starting processing ${file.path}" }
                val succeeded = this.process(file, stats)

                //logger.info { "finished processing ${file.path}" }
                this.processedFiles.add(file)

                if( succeeded )
                    stats.processed.incrementAndGet()
                else
                    stats.failed.incrementAndGet()
            }
        }

        with(stats) {
            logger.info { "Merge completed for $all assets with $processed processed successfully and $failed failed" }
            logger.info { "  process stats: added $inserted, updated $updated, skipped $unchanged unchanged" }
        }

        return stats.failed.get()
    }


    // Called from different threads. Should not write the object state
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

    // Move basic ops out to protected methods so we can test process() by overriding them

    private fun readFile(file: File): String = file.readText(config.charSet)

    private fun parseJson(content: String): Map<String, Any> = gson.fromJson<Map<String, Any>>(content, Map::class.java)

    private fun fileNameToRoleName(file: File) =
        file.invariantSeparatorsPath
            .replace(Regex("^$MERGE_DATA_ROOT_DIR/$baseDirName/"), "")
            .replace(Regex("\\.${file.extension}$"), "")
            .replace('/', '.')


    private fun roleNameToInvariantPath(roleName: String) =
            "$MERGE_DATA_ROOT_DIR/$baseDirName/" + roleName.replace('.', '/')


    @VisibleForTesting fun findRoleFile(roleName: String, files: List<File>): File? =
        this.roleNameToInvariantPath(roleName).let { rolePath ->
            files.find {
                it.invariantSeparatorsPath.substring(0, it.invariantSeparatorsPath.lastIndexOf('.')) == rolePath
            }
        }


    private fun findRoleFile(roleName: String): File? = findRoleFile(roleName, this.files)

    private fun readRoleFromDb(roleName: String): Role? {
        val role = Role(roleName = roleName)
        databaseStore.getGoodConnection().use { conn ->
            val cnt = SelectStatement(role).select(role.allColsButPk!!).by(role::roleName).run(conn)
            if( cnt == 0 )
                return null
        }
        return role
    }

    private fun serializeToJsonObj(v: Any): String = gson.toJson(v)

    private fun schemasMatch(fileSchema: String, dbSchema: String): Boolean =
            parseJson(fileSchema) == parseJson(dbSchema)

    data class PermissiveRoleValues(val roleName: String, var values: Map<String, Any>?)
    data class PermissiveAssetWithRoles(val asset: Asset, var roles: List<PermissiveRoleValues>?)

    data class RoleValues(val roleName: String, val valuesAsJsonStr: String)
    data class AssetWithRoles(val asset: Asset, val roles: List<RoleValues>)

    private fun readAssetObjectFromFile(file: File, parser: (fileText: String) -> Map<String, Any>): AssetWithRoles {
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

        return AssetWithRoles(fileAsset.asset, collectedRoles)
    }


    private fun checkAssetIdAndName(mergeAsset: Asset): String? {
        val mergeAssetIdIsPresent = mergeAsset.assetId != null
        val mergeAssetNameIsPresent = !mergeAsset.name.isNullOrBlank()

        var dbNameFromId: String? = null
        var dbIdFromName: UUID? = null

        if( mergeAssetIdIsPresent || mergeAssetNameIsPresent )
            databaseStore.getGoodConnection().use { conn ->
                val dbAsset = Asset()
                if(mergeAssetIdIsPresent) {
                    logger.info { "  fetching " }
                    dbAsset.assetId = mergeAsset.assetId
                    val count = SelectStatement(dbAsset).select(dbAsset::name).by(dbAsset::assetId).run(conn)
                    if(count == 1)
                        dbNameFromId = dbAsset.name
                }

                if(mergeAssetNameIsPresent) {
                    dbAsset.name = mergeAsset.name
                    val count = SelectStatement(dbAsset).select(dbAsset::assetId).by(dbAsset::name).run(conn)
                    if(count == 1)
                        dbIdFromName = dbAsset.assetId
                }
            }

        return checkMergeAssetAgainstDb(mergeAsset, dbIdFromName, dbNameFromId)
    }

    @VisibleForTesting
    fun checkMergeAssetAgainstDb(mergeAsset: Asset, dbIdFromName: UUID?, dbNameFromId: String?): String? {
        val mergeAssetIdIsPresent = mergeAsset.assetId != null
        val mergeAssetNameIsPresent = !mergeAsset.name.isNullOrBlank()

        if( mergeAssetIdIsPresent ) {
            if( mergeAssetNameIsPresent ) {
                if( dbIdFromName == null )
                    logger.debug { "  no database asset for name ${mergeAsset.name}" }
                else if( dbIdFromName == mergeAsset.assetId )
                    logger.debug { "  database asset id matches the input" }
                else
                    return "Database id $dbIdFromName of asset ${mergeAsset.name} does not match one in the mergefile: ${mergeAsset.assetId}"

                if( dbNameFromId == null )
                    logger.debug { "  no database asset for if ${mergeAsset.assetId}" }
                else if( dbNameFromId == mergeAsset.name )
                    logger.debug { "  database asset name matches the input" }
                else {
                    logger.warn { "  asset ${mergeAsset.assetId} is changing name from db's $dbNameFromId to ${mergeAsset.name}" }
                }
            } else {
                if( dbIdFromName != null )
                    return "coding error: database id $dbIdFromName was somehow fetched even though asset name wasn't provided"

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
            return "neither asset id nor name is present"
        }

        //mergeAsset.assetId = dbIdFromName
        //logger.debug { "  using database id ${mergeAsset.assetId} for asset ${mergeAsset.name}" }

        return null
    }

    data class RoleAndItsFile(val role: Role, val file: File)


    private fun processJson(file: File, stats: MergeDirectory.Companion.Stats): Boolean {
        logger.info { "processing JSON file ${file.path}" }
        try {
            val assetFromFile = readAssetObjectFromFile(file, ::parseJson)
logger.debug { "read file ${file.path}: $assetFromFile" }
/*
            val roleNameFromFile = fileNameToRoleName(file)
            if( roleNameFromFile != role.roleName )
                throw Exception("file name '$roleNameFromFile' does not match the role name '${role.roleName}'")

            val dbMergeObjs = Stack<RoleAndItsFile>()
            dbMergeObjs.push(RoleAndItsFile(role, file))



            SchemaUtils { roleName ->
                logger.debug { "processing referenced role $roleName" }
                val roleFile = findRoleFile(roleName)
                if( roleFile == null ) {
                    logger.debug { "  role file was not deteced for $roleName, attempting to find in the DB" }
                    loadSchemaFromDb(roleName)      // the role isn't known, but may be loaded by other means
                } else {
                    logger.info { "  file for role $roleName is ${roleFile.path}" }
                    if( roleFile in this.processedFiles ) {
                        logger.debug { "  $roleName is already processed, loading from the DB" }
                        loadSchemaFromDb(roleName)    // already loaded by us or another thread
                    } else {
                        logger.debug { "  reading and processing $roleName from file $roleFile" }
                        val roleFromFile = readRoleObjectFromFile(roleFile, ::parseJson)
                        dbMergeObjs.push(RoleAndItsFile(roleFromFile, roleFile))
                        roleFromFile.roleSchema     // return
                    }
                }
            }.validateAndCompile(role.roleName!!, role.roleSchema!!)

            logger.info { "considering ${dbMergeObjs.size} roles for DB merge" }
            while(dbMergeObjs.isNotEmpty()) {
                val (validatedRole, roleFile) = dbMergeObjs.pop()
                if( roleFile in this.processedFiles )
                    logger.info { "  skipping already processed role ${validatedRole.roleName}" }
                else {
                    val dbChanged = mergeRoleIntoDB(validatedRole, dbMergeObjs.isEmpty(), stats)
                    if(dbChanged) {
                        this.processedFiles.add(roleFile)
                    }
                }
            }
*/
        } catch(x: IOException) {
            logger.warn { "  couldn't read ${file.path}: ${x.javaClass.name} ${x.message}" }
            return false
        } catch(x: JsonSyntaxException) {
            logger.warn { "  json parse error in ${file.path}:\n  $x" }
            return false
        } catch(x: Exception) {
            logger.warn { "  error processing ${file.path}:\n  ${x}" }
            return false
        }

        return true
    }


    private fun mergeRoleIntoDB(role: Role, isTopLevelRole: Boolean, stats: MergeDirectory.Companion.Stats): Boolean {
        val dbRole = readRoleFromDb(role.roleName!!)
        if( dbRole == null ) {
            logger.info { "  role ${role.roleName} wasn't found in the DB, attempting to insert" }
            databaseStore.getGoodConnection().use { conn ->
                var cnt = 0
                try {
                    cnt = InsertStatement(role).run(conn)
                } catch(x: SQLException) {
                    if( x.sqlState != "23000" )     // Duplicate entry XXX for key 'PRIMARY'
                        throw x
                    logger.info { "  duplicate key on insert of ${role.roleName}, assuming another worker processed it" }
                }

                if( cnt == 0 ) {
                    conn.rollback()
                    logger.error { "  optlock failure creating role ${role.roleName}" }
                    return false
                }

                conn.commit()

            }

            logger.info { "  successfully created role ${role.roleName}" }
            stats.inserted.incrementAndGet()
            return true

        } else {
            logger.info { "  role ${role.roleName} was found in the DB, checking it for changes" }
            role.modifiedTs = dbRole.modifiedTs
            val stmt = UpdateStatement(role).byPkAndOptLock()

            var hasUpdates = false

            if( schemasMatch(role.roleSchema!!, dbRole.roleSchema!!) ) {
                logger.info("    the schemas match")
            } else {
                stmt.update(role::roleSchema)
                logger.info("    updating role schema")
                hasUpdates = true
            }

            if( dbRole.roleClass == role.roleClass ) {
                logger.info("    the classes match")
            } else {
                logger.info("    updating role class")
                stmt.update(role::roleClass)
                hasUpdates = true
            }

            if( dbRole.description == role.description ) {
                logger.info("    the role descriptions match")
            } else {
                logger.info("    updating role description")
                stmt.update(role::description)
                hasUpdates = true
            }

            if( hasUpdates ) {
                databaseStore.getGoodConnection().use { conn ->
                    val cnt = stmt.run(conn)
                    if( cnt == 0 ) {
                        conn.rollback()
                        logger.warn { "  optlock failure updating role ${role.roleName}" }
                    } else {
                        conn.commit()
                        logger.info { "  successfully updated role ${role.roleName}" }
                        stats.updated.incrementAndGet()
                    }
                }

            } else {
                logger.info("  skipping matching role ${role.roleName}")
                if( isTopLevelRole )
                    stats.unchanged.incrementAndGet()
            }

        }

        return false
    }


    private fun processYaml(file: File, stats: MergeDirectory.Companion.Stats): Boolean {
        throw UnsupportedOperationException("extension ${file.extension} will be supported later")
    }

    private fun processXml(file: File, stats: MergeDirectory.Companion.Stats): Boolean {
        throw UnsupportedOperationException("extension ${file.extension} will be supported later")
    }
}
