package com.amcentral365.service

import com.amcentral365.service.api.SchemaUtils
import com.amcentral365.service.api.catalog.Assets
import com.amcentral365.service.dao.Asset
import com.amcentral365.service.mergedata.MergeRoles
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

internal lateinit var config: Configuration
internal var authUser: AuthenticatedUser = AuthenticatedUser("amcentral365", "none@amcentral365.com", "Internal User")
internal val authorizer: Authorization = Authorization()
internal lateinit var thisWorkerAsset: Asset

var keepRunning = true  /** Global 'lights out' flag */

val databaseStore = DatabaseStore()
val webServer     = WebServer()
lateinit var schemaUtils: SchemaUtils


fun main(args: Array<String>) {

    logger.info { "AM-Central-365 version 0.0.1 is starting" }

    logger.info { "parsing arguments" }
    config = Configuration(args)

    logger.info { "initializing globals" }
    schemaUtils = SchemaUtils()

    if( config.mergeRoles )
        MergeRoles.merge("roles")

    logger.info { "starting the Web server" }
    webServer.start(config.bindPort)

    Thread.sleep(500)  // give the web server some breath time
    logger.info { "AM-Central-365 is ready to serve requests at http://${config.clusterFQDN.first}:${config.bindPort}" }
}


fun callMeFromJavaForHighFive(p1: Int) = p1 + 5


// No try/catch failures are fatal at this stage
private fun initialize() {
    thisWorkerAsset = Assets.getAssetById(config.assetId)
}
