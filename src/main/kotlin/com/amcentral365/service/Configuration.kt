package com.amcentral365.service

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.validate
import com.github.ajalt.clikt.parameters.types.int
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

class Configuration(val args: Array<String>): CliktCommand() {

    val verbosity: Int by option("-v", "--verbosity").int().default(2).validate { it >= 0 && it <= 4 }

    val bindPort: Int by option("-b", "--bind-port"
            ,help="port for the service to listen on. Must be the same on all nodes if cluster-fqdn was specified")
            .int()
            .default(24941  /* 0x616d = 'am'*/)

    val dbUsr: String by option("-u", "--user", help="database user").default("amcentral365")
    val dbPwd: String by option("-p", "--pass", help="database password").default("amcentral365")
    val dbUrl: String by option("-c", "--conn",
            help = "JDBC connection string for the back-end MySql database. Format: [jdbc:mysql://]host[:port=3306]/database")
            .convert { if( it.matches(Regex("^jdbc:[^/@]+(//|@).+")) ) it else "jdbc:mysql://" + it }
            .default("127.0.0.1/amcentral365")


    override fun run() {
        logger.info { """
             parameters:
                verbosity: $verbosity
                bind port: $bindPort
                db:        $dbUrl as $dbUsr
            """
            .trimIndent()
        }
    }

    init {
        this.main(args)
    }
}