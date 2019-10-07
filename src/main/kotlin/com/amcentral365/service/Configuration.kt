package com.amcentral365.service

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.validate
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.versionOption
import com.github.ajalt.clikt.parameters.types.int
import com.github.ajalt.clikt.parameters.types.long

import mu.KotlinLogging
import java.lang.Math.max
import java.nio.charset.Charset


private val logger = KotlinLogging.logger {}

class Configuration(val args: Array<String>): CliktCommand(name = "amcentral365-service") {
    val DBSTORE_RECONNECT_DELAY_SEC: Int = 2
    val SystemTempDirName = System.getProperty("java.io.tmpdir")

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
            help = """JDBC connection string for the back-end MySql/MariaDB database.
                    | Format: [jdbc:mariadb://]host[:port=3306]/database[?param=val&...]
                    | See https://mariadb.com/kb/en/library/pool-datasource-implementation
                    | for the connection pool paramters
                   """.trimMargin())
            .convert { if( it.matches(Regex("^jdbc:[^/@]+:?(//|@).+")) ) it else "jdbc:mariadb://$it" }
            .default("jdbc:mariadb://127.0.0.1/amcentral365?useSSL=false&minPoolSize=10")

    val clusterNodeNames: MutableList<Pair<String, Short>> = mutableListOf()
    private val rawClusterNodeNames: List<String> by option("-n", "--node", "--nodes",
            help = """
                |Comma-separated list of the worker nodes.
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
                |The format is FQDN[:port], the port defaults to the --bind-port value.
                """.trimMargin()
            ).default("")

/*
    lateinit var assetId: UUID
    private val rawAssetId: UUID? by option("--asset-id", help="this worker asset id")
            .convert { UUID.fromString(it) }
            .default(AMCWorkerAssetId)
*/
    val schemaCacheSizeInNodes: Long by option("--schema-cache-size-in-nodes").long().default(1000).validate {
        if( it < 100 )
            fail("schema-cache-size-in-nodes must be 100 or greater")
    }

    val localScriptExecBaseDir: String by option("--local-script-exec-base--dir",
            help = "Base directory for running scripts meant to be executed on the am-centrral worker machine")
            .default("/tmp")

    val defaultScriptExecTimeoutSec: Int by option("--default-script-exec-timeout-sec",
            help = "How long, in seconds, a script is allowed to run before it is cancelled. Zero for no limit.")
            .int()
            .default(0)

    val defaultScriptIdleTimeoutSec: Int by option("--default-script-idle-timeout-msec",
            help = "How long, in seconds, a script is allowed to run without producing output. Zero for no limit.")
            .int()
            .default(0)

    val scriptOutputPollIntervalMsec: Long by option("--script-output-poll-interval-msec",
            help = "How frequent do we check for script output, in milliseconds")
            .long()
            .default(200)

    lateinit var charSet: Charset private set
    val charSetName: String by option("--charset", help = "Character Set used for almost everything")
            .default("UTF-8")
            .validate {
                charSet = Charset.forName(it)  // throws IllegalCharsetNameException if charSetName is invalid
            }

    // --------------------------- Merge
    val mergeRoles:  Boolean by option("--merge-roles",  help="Scan mergedata/roles and update the cataloged roles")
                              .flag("--no-merge-roles",  default = false)
    val mergeAssets: Boolean by option("--merge-assets", help="Scan mergedata/assets and update the cataloged assets")
                              .flag("--no-merge-assets", default = false)

    var mergeThreads: Int = 0
        private set
    private val rawMergeThreads: Int by option("--merge-threads",
            help="How many threads merge should use. When 0, the number of cores less one is used."+
                 "Note that priority order is not guaranteed with multiple threads.")
            .int()
            .default(0)

    val mergeTimeLimitSec: Long by option("--merge-time-limit-sec",
            help = "Limit merge operation time to the number of seconds. 0 for no limit")
            .long()
            .default(0)

    // --------------------------- SSH
    val sshPrivateKeyFile: String by option("--ssh-pvt-key-file", help="file storing AM Central private key for SSH authentications").default("ssh-key")

    lateinit var sshPublicKeyFile: String private set
    val _sshPublicKeyFile: String by option("--ssh-pub-key-file", help="file storing AM Central public key for SSH authentications. Defaults to ").default("")

    override fun run() {
        mergeThreads = if( rawMergeThreads > 0 ) rawMergeThreads
                     else max(1, Runtime.getRuntime().availableProcessors()-1)

        fun addressPortToPair(ap: String): Pair<String, Short> {
            val p = ap.indexOf(':')
            return if( p < 1 ) Pair(ap, this.bindPort.toShort())
                   else        Pair(ap.substring(0, p), ap.substring(p+1).toInt().toShort())
        }

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

        this.sshPublicKeyFile = if( this._sshPublicKeyFile.isBlank() ) sshPrivateKeyFile + ".pub" else this._sshPublicKeyFile
    }

    init {
        this.versionOption("0.0.1")
        this.main(args)    // calls run() if everything is ok

        logger.info { """
             parameters:
                verbosity:    $verbosity
                bind port:    $bindPort
                db:           $dbUrl as $dbUsr
                nodes:        ${this.clusterNodeNames.joinToString(", ")}
                cluster FQDN: $clusterFQDN

                merge roles, assets:  $mergeRoles, $mergeAssets
                  threads to use:     $mergeThreads
                  time limit (sec):   $mergeTimeLimitSec
            """
            .trimIndent()
        }

    }

}
