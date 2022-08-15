package com.realityexpander

import io.ktor.server.engine.*
import io.ktor.server.netty.*
import com.realityexpander.plugins.*

fun main() {
    embeddedServer(Netty, port = 8080, host = "0.0.0.0") {
        configureRouting()
        configureHTTP()
        configureMonitoring()
        configureSerialization()
    }.start(wait = true)
}
