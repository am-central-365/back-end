package com.amcentral365.service

import com.amcentral365.service.api.SchemaUtils
import com.amcentral365.service.mergedata.MergeRoles
import com.amcentral365.service.mergedata.MergeAssets
import mu.KotlinLogging

private const val VERSION = "0.0.1"
private val logger = KotlinLogging.logger {}

internal lateinit var config: Configuration
internal var authUser: AuthenticatedUser = AuthenticatedUser("amcentral365", "none@amcentral365.com", "Internal User")
internal val authorizer: Authorization = Authorization()
//internal lateinit var thisWorkerAsset: Asset

val webServer = WebServer()

lateinit var schemaUtils: SchemaUtils
lateinit var databaseStore: DatabaseStore

@Volatile var keepRunning = true  /** Global 'lights out' flag */


fun main(args: Array<String>) {

    logger.info { "AM-Central-365 version $VERSION is starting" }

    logger.info { "parsing arguments" }
    config = Configuration(args)

    logger.info { "initializing globals" }
    databaseStore = DatabaseStore()
    schemaUtils = SchemaUtils(config.schemaCacheSizeInNodes)

    logger.info { "merging data" }
    if( !mergeData() )
        return

    logger.info { "starting the Web server" }
    webServer.start(config.bindPort)

    Thread.sleep(500)  // give the web server some breath time
    logger.info { "AM-Central-365 is ready to serve requests at http://${config.clusterFQDN.first}:${config.bindPort}" }
}


fun callMeFromJavaForHighFive(p1: Int) = p1 + 5


private fun mergeData(): Boolean {
    if( config.mergeRoles ) {
        val failures = MergeRoles("roles").merge()
        if( failures > 0 ) {
            logger.error { "Roles merge failed with $failures error(s). Can't start until all problems are fixed. To bypass the merge, use --no-merge-roles parameter" }
            return false
        }
    } else {
        logger.info("Merge roles is disabled, skipping")
    }

    if( config.mergeAssets ) {
        val failures = MergeAssets("assets").merge()
        if( failures > 0 ) {
            logger.error { "Assets merge failed with $failures error(s). Can't start until all problems are fixed. To bypass the merge, use --no-merge-assets parameter" }
            return false
        }
    } else {
        logger.info("Merge assets is disabled, skipping")
    }

    return true
}
