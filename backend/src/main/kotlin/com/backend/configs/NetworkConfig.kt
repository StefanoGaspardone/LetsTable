package com.backend.configs

import org.slf4j.LoggerFactory
import org.springframework.boot.CommandLineRunner
import org.springframework.core.env.Environment
import org.springframework.stereotype.Component
import java.net.InetAddress

@Component
class NetworkConfig(private val environment: Environment): CommandLineRunner {

    private val logger = LoggerFactory.getLogger(javaClass)

    override fun run(vararg args: String) {
        val port = environment.getProperty("server.port")
        val ip = InetAddress.getLocalHost().hostAddress

        logger.info("\n\t[INFO] [server_address] The service is running locally on http://localhost:$port or on the network at http://$ip:$port")
    }
}