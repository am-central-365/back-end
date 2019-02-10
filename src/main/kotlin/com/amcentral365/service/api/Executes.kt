package com.amcentral365.service.api

import mu.KotlinLogging
import spark.Request
import spark.Response

private val logger = KotlinLogging.logger {}

class Executes { companion object {

    fun list(req: Request, rsp: Response): String {
        rsp.type("application/json")
        return """{"text": "not implemented"}"""
    }

    fun start(req: Request, rsp: Response): String {
        rsp.type("application/json")
        return """{"text": "not implemented"}"""
    }

    fun getInfo(req: Request, rsp: Response): String {
        rsp.type("application/json")
        return """{"text": "not implemented"}"""
    }

    fun getLog(req: Request, rsp: Response): String {
        rsp.type("text/plain")
        return "not implemented"
    }

    fun getOutput(req: Request, rsp: Response): String {
        rsp.type("text/plain")
        return "not implemented"
    }

}}
