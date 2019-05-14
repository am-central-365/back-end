package com.amcentral365.service.mergedata

import com.amcentral365.pl4kotlin.InsertStatement
import com.amcentral365.pl4kotlin.SelectStatement
import com.amcentral365.pl4kotlin.UpdateStatement
import com.amcentral365.service.StatusException
import com.amcentral365.service.api.SchemaUtils
import mu.KotlinLogging

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException

import java.io.File
import java.io.IOException

import java.lang.UnsupportedOperationException

import com.amcentral365.service.config
import com.amcentral365.service.schemaUtils
import com.amcentral365.service.dao.Role
import com.amcentral365.service.dao.loadRoleObjectFromDB
import com.amcentral365.service.databaseStore
import com.amcentral365.service.mergedata.MergeRoles.Companion.getSchemaForRole


private val logger = KotlinLogging.logger {}


open class MergeRoles { companion object {

    // All variables are shared between threads
    private val id get() = Thread.currentThread().name     // a function, unique value for each thread
    private val gson = Gson()  // thread safe
    private var baseDirName: String = ""  // set once by `merge`
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
    fun merge(baseDirName: String): Int {
        this.files = MergeDirectory.list(baseDirName)
        this.baseDirName = baseDirName
        val stats = MergeDirectory.process(this.files) { file, stats ->
            if( file in processedFiles )
                logger.info { "$id: skipping already processed ${file.path}" }
            else {
                //logger.info { "$id: starting processing ${file.path}" }
                logger.debug { "$id: attempting to lock on ${file.path}" }
                val succeeded = synchronized(file) {
                    if( file in processedFiles )
                        logger.info { "$id: processed while in wait, skipping ${file.path}" }
                    this.process(file, stats)
                }

                //logger.info { "$id: finished processing ${file.path}" }
                this.processedFiles.add(file)

                if( succeeded )
                    stats.processed.incrementAndGet()
                else
                    stats.failed.incrementAndGet()
            }
        }

        with(stats) {
            logger.info { "Merge completed for $all roles with $processed processed successfully and $failed failed" }
            logger.info { "  process stats: skipped $unchanged unchanged, added $inserted, updated $updated" }
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


    private fun findRoleFile(roleName: String): File? {
        return File("$MERGE_DATA_ROOT_DIR/$baseDirName/${roleName}.json")  // FIXME: scan `files` to find the file in subdirs, allow other extensions
    }


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

    private fun schemasMatch(fileSchema: Any, dbSchema: String): Boolean = parseJson(dbSchema) == fileSchema


    private fun getSchemaForRole(roleName: String): String? {
        val roleFile = findRoleFile(roleName)
                ?: throw StatusException(404, "could not identify file for role '$roleName'")
        logger.info { "$id: file for role $roleName is ${roleFile.path}" }

        if( roleFile in this.processedFiles )
            return schemaUtils.loadSchemaFromDb(roleName)

        logger.debug { "$id: trying to lock on ${roleFile.path}" }
        synchronized(roleFile) {
            if( roleFile in this.processedFiles )   // could have been processed while we were waiting for the lock
                return schemaUtils.loadSchemaFromDb(roleName)

            val (role, _) = readRoleObjectFromFile(roleFile, ::parseJson)
            return role.roleSchema
        }
    }


    data class ParsedRoleObj(val role: Role, val roleSchemaMap: Any)

    private fun readRoleObjectFromFile(file: File, parser: (fileText: String) -> Map<String, Any>): ParsedRoleObj {
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

        return ParsedRoleObj(role, roleSchemaMap)
    }


    private fun processJson(file: File, stats: MergeDirectory.Companion.Stats): Boolean {
        logger.info { "$id:  processing JSON file ${file.path}" }
        try {
            val (role, roleSchemaMap) = readRoleObjectFromFile(file, ::parseJson)

            val roleNameFromFile = fileNameToRoleName(file)
            if( roleNameFromFile != role.roleName )
                throw Exception("file name '$roleNameFromFile' does not match the role name '${role.roleName}'")

            val dbRole = readRoleFromDb(role.roleName!!)
            if( dbRole == null ) {
                // throws an exception on failure
                schemaUtils.validateAndCompile(role.roleName!!, role.roleSchema!!, getSchemaStrByRoleName = ::getSchemaForRole)

                databaseStore.getGoodConnection().use { conn ->
                    val cnt = InsertStatement(role).run(conn)
                    if( cnt == 0 ) {
                        conn.rollback()
                        logger.error { "$id:  failed creating role ${role.roleName}" }
                        return false
                    }
                    conn.commit()
                }

                logger.info { "$id:  successfully created role ${role.roleName}" }
                stats.inserted.incrementAndGet()
                return true

            } else {
                role.modifiedTs = dbRole.modifiedTs
                val stmt = UpdateStatement(role).byPkAndOptLock()

                var hasUpdates = false

                if( schemasMatch(roleSchemaMap, dbRole.roleSchema!!) ) {
                    logger.info("$id:    the schemas match")
                } else {
                    schemaUtils.validateAndCompile(role.roleName!!, role.roleSchema!!, getSchemaStrByRoleName = ::getSchemaForRole)

                    stmt.update(role::roleSchema)
                    logger.info("$id:    updating role schema")
                    hasUpdates = true
                }

                if( dbRole.roleClass == role.roleClass ) {
                    logger.info("$id:    the classes match")
                } else {
                    logger.info("$id:    updating role class")
                    stmt.update(role::roleClass)
                    hasUpdates = true
                }

                if( dbRole.description == role.description ) {
                    logger.info("$id:    the role descriptions match")
                } else {
                    logger.info("$id:    updating role description")
                    stmt.update(role::description)
                    hasUpdates = true
                }

                if( hasUpdates ) {
                    databaseStore.getGoodConnection().use { conn ->
                        val cnt = stmt.run(conn)
                        if( cnt == 0 ) {
                            conn.rollback()
                            logger.warn { "$id:  failed to update role ${role.roleName}, likely due to concurrency" }
                        } else {
                            conn.commit()
                            logger.info { "$id:  successfully updated role ${role.roleName}" }
                            stats.updated.incrementAndGet()
                        }
                    }

                } else {
                    logger.info("$id:  skipping matching roles")
                    stats.unchanged.incrementAndGet()
                }

            }

        } catch(x: IOException) {
            logger.error { "$id:  couldn't read ${file.path}: ${x.javaClass.name} ${x.message}" }
            return false
        } catch(x: JsonSyntaxException) {
            logger.error { "$id:  json parse error in ${file.path}: ${x.javaClass.name} ${x.message}" }
            return false
        } catch(x: Exception) {
            logger.error { "$id:  error processing ${file.path}: ${x.javaClass.name} ${x.message}" }
            return false
        }

        return true
    }



    private fun processYaml(file: File, stats: MergeDirectory.Companion.Stats): Boolean {
        throw UnsupportedOperationException("extension ${file.extension} will be supported later")
    }

    private fun processXml(file: File, stats: MergeDirectory.Companion.Stats): Boolean {
        throw UnsupportedOperationException("extension ${file.extension} will be supported later")
    }
}}
