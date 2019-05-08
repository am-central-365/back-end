package com.amcentral365.service.mergedata

import com.amcentral365.pl4kotlin.InsertStatement
import com.amcentral365.pl4kotlin.SelectStatement
import com.amcentral365.pl4kotlin.UpdateStatement
import mu.KotlinLogging

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException

import java.io.File
import java.io.IOException

import java.lang.UnsupportedOperationException

import com.amcentral365.service.config
import com.amcentral365.service.schemaUtils
import com.amcentral365.service.dao.Role
import com.amcentral365.service.databaseStore


private val logger = KotlinLogging.logger {}


open class MergeRoles { companion object {

    // All variables are shared between threads
    private val id get() = Thread.currentThread().name     // a function, unique value for each thread
    private val gson = Gson()  // thread safe
    private var baseDirName: String = ""  // set once by `merge`


    fun merge(baseDirName: String) {
        val files = MergeDirectory.list(baseDirName)
        this.baseDirName = baseDirName
        val stats = MergeDirectory.process(files) { file, processedFiles, stats ->
            if( file in processedFiles )
                logger.info { "$id: skipping already processed ${file.path}" }
            else {
                //logger.info { "$id: starting processing ${file.path}" }
                val succeeded = process(file, stats)

                //logger.info { "$id: finished processing ${file.path}" }
                processedFiles.add(file)

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


    private fun processJson(file: File, stats: MergeDirectory.Companion.Stats): Boolean {
        logger.info { "$id:  processing JSON file ${file.path}" }
        try {
            val fileText = readFile(file)
            val fileObj = parseJson(fileText)

            val roleSchema = fileObj["roleSchema"] ?: throw Exception("element 'roleSchema' is missing, roles must have schema")

            fun getWithCheck(elmName: String): String {
                val elmVal = fileObj.getOrDefault(elmName, "").toString()
                if( elmVal.isBlank() )
                    throw Exception("element '$elmName' is missing or blank")
                return elmVal.trim()
            }

            val role = Role()
            role.roleName  = getWithCheck("roleName")

            val roleNameFromFile = fileNameToRoleName(file)
            if( roleNameFromFile != role.roleName )
                throw Exception("file name '$roleNameFromFile' does not match the role name '${role.roleName}'")

            val dbRole = readRoleFromDb(role.roleName!!)
            if( dbRole == null ) {

                role.roleClass = getWithCheck("class")
                role.description = fileObj.getOrDefault("description", "").toString()
                role.roleSchema = serializeToJsonObj(roleSchema)

                schemaUtils.validateAndCompile(role.roleName!!, role.roleSchema!!)

                databaseStore.getGoodConnection().use { conn ->
                    val cnt = InsertStatement(role).run(conn)
                    if( cnt == 0 ) {
                        conn.rollback()
                        logger.warn { "$id:  failed creating role ${role.roleName}" }
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

                if( schemasMatch(roleSchema, dbRole.roleSchema!!) ) {
                    logger.info("$id:    the schemas match")
                } else {
                    role.roleSchema = serializeToJsonObj(roleSchema)
                    schemaUtils.validateAndCompile(role.roleName!!, role.roleSchema!!)

                    stmt.update(role::roleSchema)
                    logger.info("$id:    updating role schema")
                    hasUpdates = true
                }

                role.roleClass = getWithCheck("class")
                if( dbRole.roleClass == role.roleClass ) {
                    logger.info("$id:    the classes match")
                } else {
                    logger.info("$id:    updating role class")
                    stmt.update(role::roleClass)
                    hasUpdates = true
                }

                role.description = fileObj.getOrDefault("description", "").toString()
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
            logger.warn { "$id:  couldn't read ${file.path}: ${x.javaClass.name} ${x.message}" }
            return false
        } catch(x: JsonSyntaxException) {
            logger.warn { "$id:  json parse error in ${file.path}: ${x.javaClass.name} ${x.message}" }
            return false
        } catch(x: Exception) {
            logger.warn { "$id:  error processing ${file.path}: ${x.javaClass.name} ${x.message}" }
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
