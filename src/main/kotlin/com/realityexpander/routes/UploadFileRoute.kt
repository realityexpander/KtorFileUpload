package com.realityexpander.routes

import com.google.gson.Gson
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.io.File
import kotlin.random.Random

// Example ExtraJson class
data class ExtraJson(
    val key: String
)

fun Route.uploadFile() {

    // Upload an image
    post("image") {
        val multipart = call.receiveMultipart()

        multipart.forEachPart { part ->
            when (part) {
                is PartData.FormItem -> {
                    println("${part.name} ==> ${part.value}")

                    // Show how to extract json from the form data
                    if(part.name == "extraJson") {
                        println("key = ${Gson().fromJson(part.value, ExtraJson::class.java).key}")
                    }
                }
                is PartData.FileItem -> {
                    if(part.name == "image_file") {
                        println("part.originalFileName = ${part.originalFileName}")
                        part.save("build/resources/main/static/images/",
                            part.originalFileName
                                ?: "image_file-${Random.nextLong().toString()}.jpg"
                        )
                    }
                }
                else -> Unit
            }
        }

        call.respond(HttpStatusCode.OK)
    }

    // Download an image
    get("download") {
        // get filename from request url
        val filename = call.parameters["fileName"]!!

        // construct reference to file
        // ideally this would use a different filename
        val file = File("filesToServe/$filename")

        if(file.exists()) {
            call.respondFile(file)
        }
        else call.respond(HttpStatusCode.NotFound)
    }
}