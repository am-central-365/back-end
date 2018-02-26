package com.amcentral.tbd

fun main(args: Array<String>) {

    parseParams(args)
    val webServer = WebServer()
    webServer.start()
}


fun parseParams(args: Array<String>): Boolean {
    return args.isNotEmpty()
}

fun callMeFromJavaForHighFive(p1: Int) = p1 + 5
