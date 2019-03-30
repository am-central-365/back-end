package com.amcentral365.service

import mu.KotlinLogging

import java.net.InetAddress
import java.net.UnknownHostException
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.SocketException



private val logger = KotlinLogging.logger {}

/**
 * get ip addresses of the given host. The host is typically an FQDN name.
 * Safe to call with an ip address: the very same ip is returned.
 *
 * @param fqdn the FQDN
 * @return Array ofip addresses the name resolves to, or an empty array if it couldn't be resoled
 */
fun resolveFQDN(fqdn: String): List<String> {
    try {
        logger.debug { "resolving $fqdn" }
        return InetAddress.getAllByName(fqdn).map { it.hostAddress }
    } catch (e: UnknownHostException) {
        logger.error { "could not resolve $fqdn: ${e.javaClass.name} ${e.message}" }
        return emptyList()
    }
}


/**
 * Find a local, non-loopback, IPv4 address of the machine.
 *
 * @return The first non-loopback IPv4 address found, or `null` if no such addresses was found
 * @throws SocketException If there was a problem querying the network interfaces
 */
@Throws(SocketException::class)
fun getLocalIpAddress(): InetAddress? {
    val ifaces = NetworkInterface.getNetworkInterfaces()
    while( ifaces.hasMoreElements() ) {
        val iface = ifaces.nextElement()
        val addresses = iface.inetAddresses

        while( addresses.hasMoreElements() ) {
            val addr = addresses.nextElement()
            if( addr is Inet4Address && !addr.isAnyLocalAddress )
                return addr
        }
    }

    return null
}
