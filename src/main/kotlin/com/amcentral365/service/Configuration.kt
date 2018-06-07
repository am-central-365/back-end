package com.amcentral365.service

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.*
import com.github.ajalt.clikt.parameters.types.int
import mu.KotlinLogging


private val logger = KotlinLogging.logger {}

class Configuration(val args: Array<String>): CliktCommand(name = "amcentral365-service") {
    val DBSTORE_RECONNECT_DELAY_SEC: Int = 2


    val verbosity: Int by option("-v", "--verbosity").int().default(2).validate {
        if( it < 0 || it > 4 )
            fail("verbosity must be between 0 to 4, got $it")
    }

    val bindPort: Int by option("-b", "--bind-port"
            ,help="port for the service to listen on. Must be the same on all nodes if cluster-fqdn was specified")
            .int()
            .default(24941  /* 0x616d = 'am'*/)

    val dbUsr: String by option("-u", "--user", help="database user").default("amcentral365")
    val dbPwd: String by option("-p", "--pass", help="database password").default("amcentral365")
    val dbUrl: String by option("-c", "--conn",
            help = "JDBC connection string for the back-end MySql database. Format: [jdbc:mysql://]host[:port=3306]/database")
            .convert { if( it.matches(Regex("^jdbc:[^/@]+(//|@).+")) ) it else "jdbc:mysql://" + it }
            .default("jdbc:mysql://127.0.0.1/amcentral365")

    val clusterNodeNames: MutableList<String> = mutableListOf()
    private val rawClusterNodeNames: List<String> by option("-n", "--node", "--nodes",
            help = """
                |Comma-separated list of amcentral365 cluster nodes.
                |The nodes can be specified by fqdn name or an IPv4 address. The parameter may be
                |specified multiple times, all values are combined into a single list.
                |When omitted, the values are obtained by resolving --cluster-fqdn.
                |
                |The addresses are used for node to communicate with each other.
                """.trimMargin()
            ).multiple()

    lateinit var clusterFQDN: String private set
    private val rawClusterFQDN: String by option("-a", "--cluster-fqdn",
            help = """
                |Passed to the scripts which call this address for amCentral services.
                |That way the scripts utilize any cluster node, not just the one invoked them.
                |When omitted, the local node address is used.
                """.trimMargin()
            ).default("")


    override fun run() {

        //
        this.clusterFQDN =
            if( this.rawClusterFQDN.isNotBlank() )
                this.rawClusterFQDN
            else
                getLocalIpAddress()?.hostAddress ?: throw Exception("couldn't get local ip address")


        // massage cluster node list: split on comma, resolve, and combine multiple parameters
        this.rawClusterNodeNames.forEach {
            it.split(',').forEach {
                val resolved = resolveFQDN(it)
                this.clusterNodeNames.addAll(resolved)
                logger.info { "$it resolved to: ${resolved.joinToString(", ")}" }
            }
        }

        // when parameters were not specified, resolve FQDN
        if( this.clusterNodeNames.isEmpty() ) {
            logger.info { "using FQDN to obtain list of cluster nodes" }
            val resolved = resolveFQDN(this.clusterFQDN)
            this.clusterNodeNames.addAll(resolved)
            logger.info { "${this.clusterFQDN} resolved to: ${resolved.joinToString(", ")}" }
        }

    }

    init {
        this.main(args)    // calls run() if everything is ok

        logger.info { """
             parameters:
                verbosity:    $verbosity
                bind port:    $bindPort
                db:           $dbUrl as $dbUsr
                nodes:        ${this.clusterNodeNames.joinToString(", ")}
                cluster FQDN: $clusterFQDN
            """
                .trimIndent()
        }

    }

}