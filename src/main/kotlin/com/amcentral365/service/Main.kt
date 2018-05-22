package com.amcentral365.service

import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

lateinit internal var config: Configuration

fun main(args: Array<String>) {

    logger.info { "AM-Central-365 version 0.0.1 is starting" }

    logger.info { "parsing arguments" }
    config = Configuration(args)


    val webServer = WebServer()
    webServer.start(config.bindPort)

    Thread.sleep(500)  // give the web server some breath time
    logger.info { "AM-Central-365 is ready to serve requests at http://${config.clusterFQDN}:${config.bindPort}" }
}


fun parseParams(args: Array<String>): Boolean {
    return args.isNotEmpty()
}

fun callMeFromJavaForHighFive(p1: Int) = p1 + 5
