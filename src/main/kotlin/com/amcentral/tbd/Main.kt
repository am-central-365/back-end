package com.amcentral.tbd

import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

fun main(args: Array<String>) {

    logger.info { "AM-Central-365 version 0.0.1 is starting" }

    parseParams(args)
    val webServer = WebServer()
    webServer.start()

    Thread.sleep(500)  // give the web server some breath time
    logger.info { "AM-Central-365 is ready to serve requests" }
}


fun parseParams(args: Array<String>): Boolean {
    return args.isNotEmpty()
}

fun callMeFromJavaForHighFive(p1: Int) = p1 + 5
