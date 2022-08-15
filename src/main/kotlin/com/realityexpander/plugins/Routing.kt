package com.realityexpander.plugins

import com.realityexpander.routes.uploadFile
import io.ktor.server.routing.*
import io.ktor.server.application.*

fun Application.configureRouting() {
    routing {
        uploadFile()
    }
}
