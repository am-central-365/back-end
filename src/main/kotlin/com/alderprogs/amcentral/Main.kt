package com.alderprogs.amcentral

fun main(args: Array<String>) {

    println("I am Kotlin's Main")
    parseParams(args)
}


fun parseParams(args: Array<String>): Boolean {
    return args.isNotEmpty()
}
