package com.ai.assistance.operit.integrations.http

import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Collections

object ExternalChatHttpNetworkInfo {

    fun getLocalIpv4Addresses(): List<String> {
        return runCatching {
            Collections.list(NetworkInterface.getNetworkInterfaces())
                .asSequence()
                .filter { it.isUp && !it.isLoopback && !it.isVirtual }
                .flatMap { networkInterface ->
                    Collections.list(networkInterface.inetAddresses).asSequence()
                }
                .filterIsInstance<Inet4Address>()
                .filterNot { it.isLoopbackAddress }
                .map { it.hostAddress.orEmpty() }
                .filter { it.isNotBlank() }
                .distinct()
                .sorted()
                .toList()
        }.getOrElse { emptyList() }
    }
}
