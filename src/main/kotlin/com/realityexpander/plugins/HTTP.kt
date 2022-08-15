package com.realityexpander.plugins

import io.ktor.server.plugins.defaultheaders.*
import io.ktor.server.application.*

fun Application.configureHTTP() {
    install(DefaultHeaders) {
        header("X-Engine", "Ktor") // will send this header with each response
    }

}
