package com.amcentral365.service

import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

lateinit internal var config: Configuration
internal var authUser: AuthenticatedUser = AuthenticatedUser("amcentral365", "none@amcentral365.com", "Internal User")
internal val authorizer: Authorization = Authorization()

var keepRunning = true  /** Global 'lights out' flag */

val databaseStore = DatabaseStore()
val webServer = WebServer()

fun main(args: Array<String>) {

    logger.info { "AM-Central-365 version 0.0.1 is starting" }

    logger.info { "parsing arguments" }
    config = Configuration(args)

    webServer.start(config.bindPort)

    Thread.sleep(500)  // give the web server some breath time
    logger.info { "AM-Central-365 is ready to serve requests at http://${config.clusterFQDN.first}:${config.bindPort}" }
}


fun callMeFromJavaForHighFive(p1: Int) = p1 + 5
