package com.amcentral365.service

import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

class Topology {
    data class Node(val hostname: String, val port: Int) {
        override fun toString(): String = "$hostname:$port"
    }

    val NoNode = Node("~missing~", 0)

    var buildNode = NoNode
    var dbNode = NoNode
    val amcNodes = emptyList<Node>()
    val targetNodes = emptyList<Node>()

    override fun toString(): String =
        "build node: $buildNode, db node: $dbNode, workers: ${amcNodes.joinToString(", ")}, targets: ${targetNodes.joinToString(", ")}"

    fun isValid(node: Node) = node.port > 0
    fun isValid() = isValid(buildNode) && isValid(dbNode)
            && amcNodes.isNotEmpty() && amcNodes.all { isValid(it) }
            && targetNodes.isNotEmpty() && targetNodes.all { isValid(it) }

    companion object {
        fun fromDNS(): Topology {
            try {
                val topology = Topology()
                topology.buildNode = Node(getLocalIpAddress()!!.hostName, 65535)
                topology.dbNode = Node(resolveFQDN("db")[0], 3306)
                topology.amcNodes.toMutableList().addAll(resolveFQDN("worker").map { Node(it, 24941) })
                topology.targetNodes.toMutableList().addAll(resolveFQDN("target").map { Node(it, 22) })
                return topology
            } catch(x: Throwable) {
                logger.warn { "couldn't resolve topology from the DNS: ${x.message}" }
                return Topology()
            }
        }
    }
}
