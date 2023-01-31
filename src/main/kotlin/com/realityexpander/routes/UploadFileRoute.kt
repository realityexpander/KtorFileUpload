package com.realityexpander.routes

import com.google.gson.Gson
import com.natpryce.konfig.*
import com.natpryce.konfig.ConfigurationProperties.Companion.systemProperties
import com.sendgrid.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.io.File
import java.io.IOException
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

    // Send an email
    get("email") {
        val from = Email("realityexpanderdev@gmail.com").apply { name = "Chris Athanas" }
        val subject = "Sending with SendGrid is Fun"
//        val to = Email("realityexpander@gmail.com")
        val to = Email(call.parameters["email"]!!)
        val content = Content("text/plain", "and easy to do anywhere, even with Kotlin")
        val mail = Mail(from, subject, to, content)

        try {

            val sendgridApiKey = Key("SENDGRID_API_KEY", stringType)
            val config = systemProperties() overriding
                    EnvironmentVariables() overriding
                    ConfigurationProperties.fromFile(File("sendgrid.properties"))  overriding
                    ConfigurationProperties.fromResource("defaults.properties")
            val sendGrid = SendGrid(config[sendgridApiKey])

            val response = sendGrid.api(
                Request().apply {
                    method = Method.POST
                    endpoint = "mail/send"
                    body = mail.build()
                })

            println(response.statusCode)
            println(response.body)
            println(response.headers)

            call.respond(HttpStatusCode.OK)
        } catch (ex: Exception) {
            println(ex.message)
            call.respond(HttpStatusCode.InternalServerError)
        }
    }

    get("email2") {
        val from = Email("realityexpanderdev@gmail.com")
        val subject = "Sending with SendGrid is Fun"
        val to = Email("test@example.com")
        val content = Content("text/plain", "and easy to do anywhere, even with Java")
        val mail = Mail(from, subject, to, content)

//        val sg = SendGrid(System.getenv("SENDGRID_API_KEY"))
        val sendgridApiKey = Key("SENDGRID_API_KEY", stringType)
        val config = systemProperties() overriding
                EnvironmentVariables() overriding
                ConfigurationProperties.fromFile(File("sendgrid.properties"))  overriding
                ConfigurationProperties.fromResource("defaults.properties")
        val sg = SendGrid(config[sendgridApiKey])


        val request = Request()
        try {
            request.method = Method.POST
            request.endpoint = "mail/send"
            request.body = mail.build()
            val response = sg.api(request)
            println(response.statusCode)
            println(response.body)
            println(response.headers)
        } catch (ex: IOException) {
            throw ex
        }
    }
}