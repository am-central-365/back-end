package com.amcentral365.service.mergedata

import com.amcentral365.pl4kotlin.InsertStatement
import com.amcentral365.pl4kotlin.SelectStatement
import com.amcentral365.pl4kotlin.UpdateStatement
import com.amcentral365.service.api.SchemaUtils
import com.amcentral365.service.api.loadSchemaFromDb
import mu.KotlinLogging

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException

import java.io.File
import java.io.IOException

import java.lang.UnsupportedOperationException

import com.amcentral365.service.config
import com.amcentral365.service.dao.Role
import com.amcentral365.service.databaseStore
import com.google.common.annotations.VisibleForTesting
import java.sql.SQLException
import java.util.Stack


private val logger = KotlinLogging.logger {}


open class MergeRoles(private val baseDirName: String) {

    // All variables are shared between threads
    private val gson = Gson()  // thread safe
    private var processedFiles = mutableSetOf<File>()   // read and updated by all threads
    private lateinit var files: List<File>   // set and populated by `merge` once, read by all threads

    /**
     * Merge role definitions into the database
     *
     * Read role definition files under the specified directory (defaulting to mergedata/roles),
     * and merge (add or update) roles into the database table <code>ROLES</code>.
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
            logger.info { "Merge completed for $all roles with $processed processed successfully and $failed failed" }
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

    protected fun readFile(file: File): String = file.readText(config.charSet)

    protected fun parseJson(content: String): Map<String, Any> = gson.fromJson<Map<String, Any>>(content, Map::class.java)

    protected fun fileNameToRoleName(file: File) =
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

    protected fun readRoleFromDb(roleName: String): Role? {
        val role = Role(roleName = roleName)
        databaseStore.getGoodConnection().use { conn ->
            val cnt = SelectStatement(role).select(role.allColsButPk!!).by(role::roleName).run(conn)
            if( cnt == 0 )
                return null
        }
        return role
    }

    protected fun serializeToJsonObj(v: Any): String = gson.toJson(v)

    private fun schemasMatch(fileSchema: String, dbSchema: String): Boolean =
            parseJson(fileSchema) == parseJson(dbSchema)


    private fun readRoleObjectFromFile(file: File, parser: (fileText: String) -> Map<String, Any>): Role {
        val fileText = readFile(file)
        val fileObj = parser(fileText)
        val roleSchemaMap = fileObj["roleSchema"]
                ?: throw Exception("element 'roleSchema' is missing, roles must have schema")

        val role = Role()

        fun getWithCheck(elmName: String): String {
            val elmVal = fileObj.getOrDefault(elmName, "").toString()
            if( elmVal.isBlank() )
                throw Exception("element '$elmName' is missing or blank")
            return elmVal.trim()
        }

        role.roleName    = getWithCheck("roleName")
        role.roleClass   = getWithCheck("class")
        role.description = fileObj.getOrDefault("description", "").toString()
        role.roleSchema  = serializeToJsonObj(roleSchemaMap)

        return role
    }

    data class RoleAndItsFile(val role: Role, val file: File)


    private fun processJson(file: File, stats: MergeDirectory.Companion.Stats): Boolean {
        logger.info { "processing JSON file ${file.path}" }
        try {
            val role = readRoleObjectFromFile(file, ::parseJson)

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

        } catch(x: IOException) {
            logger.error { "  couldn't read ${file.path}: ${x.javaClass.name} ${x.message}" }
            return false
        } catch(x: JsonSyntaxException) {
            logger.error { "  json parse error in ${file.path}: ${x.javaClass.name} ${x.message}" }
            return false
        } catch(x: Exception) {
            logger.error { "  error processing ${file.path}: ${x.javaClass.name} ${x.message}" }
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
