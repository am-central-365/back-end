package com.amcentral365.service

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.validate
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.types.int
import com.github.ajalt.clikt.parameters.types.long

import mu.KotlinLogging


private val logger = KotlinLogging.logger {}

class Configuration(val args: Array<String>): CliktCommand(name = "amcentral365-service") {
    val DBSTORE_RECONNECT_DELAY_SEC: Int = 2


    val verbosity: Int by option("-v", "--verbosity").int().default(2).validate {
        if( it < 0 || it > 4 )
            fail("verbosity must be between 0 to 4, got $it")
    }

    val inDevelopment: Boolean by option("--develop").flag("--no-develop", default = false)

    val bindPort: Int by option("-b", "--bind-port"
            ,help="port for the service to listen on. Must be the same on all nodes if cluster-fqdn was specified")
            .int()
            .default(24941  /* 0x616d = 'am'*/)
            .validate {
                if( it < 1024  || it > 65535 )
                    fail("bind-port must be between 1024 and 65535, got $it")
            }

    val dbUsr: String by option("-u", "--user", help="database user").default("amcentral365")
    val dbPwd: String by option("-p", "--pass", help="database password").default("a")
    val dbUrl: String by option("-c", "--conn",
            help = "JDBC connection string for the back-end MySql database. Format: [jdbc:mysql://]host[:port=3306]/database")
            .convert { if( it.matches(Regex("^jdbc:[^/@]+(//|@).+")) ) it else "jdbc:mysql://$it" }
            .default("jdbc:mysql://127.0.0.1/amcentral365?useSSL=false")

    val clusterNodeNames: MutableList<Pair<String, Short>> = mutableListOf()
    private val rawClusterNodeNames: List<String> by option("-n", "--node", "--nodes",
            help = """
                |Comma-separated list of amcentral365 cluster nodes.
                |The parameter may be specified multiple times, all values are combined into a single list.
                |When omitted, the values are obtained by resolving --cluster-fqdn.
                |
                |The node format is address[:port] wjere address can be an fqdn name or IPv4 address.
                |The port defaults to the value of --bind-port parameter.
                """.trimMargin()
            ).multiple()

    lateinit var clusterFQDN: Pair<String, Short> private set
    private val rawClusterFQDN: String by option("-a", "--cluster-fqdn",
            help = """
                |Passed as the callback address to the user scripts.
                |When omitted, the invoking node address is used.
                |
                |The format is FQDN[:port], port defaults to --bind-port value.
                """.trimMargin()
            ).default("")


    val schemaCacheSizeInNodes: Long by option("--schema-cache-size-in-nodes").long().default(1000).validate {
        if( it < 100 )
            fail("schema-cache-size-in-nodes must be 100 or greater")
    }

    override fun run() {

        fun addressPortToPair(ap: String): Pair<String, Short> {
            val p = ap.indexOf(':')
            return if( p < 1 ) Pair(ap, this.bindPort.toShort())
                   else        Pair(ap.substring(0, p), ap.substring(p+1).toInt().toShort())
        }

        //
        this.clusterFQDN = addressPortToPair(
            if( this.rawClusterFQDN.isNotBlank() )
                this.rawClusterFQDN
            else
                getLocalIpAddress()?.hostAddress ?: throw Exception("couldn't get local ip address")
        )


        // massage cluster node list: split on comma, resolve, and combine multiple parameters
        this.rawClusterNodeNames.forEach { rawClusterNodeName ->
            rawClusterNodeName.split(',').forEach { clusterNodePair ->
                val hostport = addressPortToPair(clusterNodePair)
                val resolved = resolveFQDN(hostport.first)
                resolved.forEach {
                    this.clusterNodeNames.add(Pair(it, hostport.second))
                }
                logger.info { "$clusterNodePair resolved to: ${resolved.joinToString(", ")}" }
            }
        }

        // when parameters were not specified, resolve FQDN
        if( this.clusterNodeNames.isEmpty() ) {
            logger.info { "using FQDN to obtain list of cluster nodes" }
            val resolved = resolveFQDN(this.clusterFQDN.first)
            resolved.forEach {
                this.clusterNodeNames.add(Pair(it, this.clusterFQDN.second))
            }
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
