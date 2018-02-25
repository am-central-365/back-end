package com.amcentral.tbd

fun main(args: Array<String>) {

    println("I am Kotlin's Main")

    println("Calling SomeJavaClass.m1...")
    var v = SomeJavaClass.m1(5, "this is a string")
    println("  .. the returned value was ${v}")

    println("Making Java call us...")
    val sjc = SomeJavaClass()
    v = sjc.callingKotlin(2)
    println("  .. the returned value was ${v}")

    parseParams(args)
}


fun parseParams(args: Array<String>): Boolean {
    return args.isNotEmpty()
}

fun callMeFromJavaForHighFive(p1: Int) = p1 + 5
