package com.realityexpander.routes

import com.google.gson.Gson
import com.natpryce.konfig.*
import com.natpryce.konfig.ConfigurationProperties.Companion.systemProperties
import com.realityexpander.routes.Config.jwtSecret
import com.realityexpander.routes.Config.sendgridApiKey
import com.sendgrid.*
import io.fusionauth.jwt.domain.JWT
import io.fusionauth.jwt.hmac.HMACSigner
import io.fusionauth.jwt.hmac.HMACVerifier
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.date.*
import io.ktor.util.pipeline.*
import java.io.File
import java.io.IOException
import java.time.ZonedDateTime
import kotlin.random.Random

typealias JWTToken = String

const val JWT_COOKIE_NAME = "realityexpander_jwt"

data class User(
    val id: String = Random.nextInt().toString(),
    val email: String,
    val username: String,
    val token: String? = null,         // JWT token - only set if user is logged in
//    val image: ByteArray?
    val avatarFileName: String?
)

var users_db = loadUsersFromJsonFile() ?: mutableListOf(
    User(
        "1",
        "chris.athanas.now@gmail.com",
        "Chris A Now",
        avatarFileName = "image_1.png"
    )
)

// Example ExtraJson class
data class ExtraJson(
    val key: String
)

object Config { // defines configuration keys for .properties files
    val sendgridApiKey = Key("SENDGRID_API_KEY", stringType)
    val jwtSecret = Key("JWT_SECRET", stringType)
}
//val sendgridApiKey = Key("SENDGRID_API_KEY", stringType)
//val appJWTSecret = Key("SENDGRID_API_KEY", stringType)
val config = systemProperties() overriding
        EnvironmentVariables() overriding
        ConfigurationProperties.fromFile(File("sendgrid.properties"))  overriding
        ConfigurationProperties.fromResource("defaults.properties")

fun Route.uploadFile() {

    get("/{path}") {
        val path = call.parameters["path"]
        val file = File("public/$path")
        if (file.exists()) {
            call.respondFile(file)
        } else {
            //call.respond(HttpStatusCode.NotFound)
        }
    }

    get("/avatars/{path}") {
        val path = call.parameters["path"]
        val file = File("public/avatars/$path")
        if (file.exists()) {
            call.respondFile(file)
        } else {
            //call.respond(HttpStatusCode.NotFound)
        }
    }

    get("/") {
//        val src = File("README.md").readText()  // read the markdown file
//        val flavour = CommonMarkFlavourDescriptor()  // create a flavour descriptor
//        val parsedTree = MarkdownParser(flavour).buildMarkdownTreeFromString(src)  // parse the markdown file
//        val html = HtmlGenerator(src, parsedTree, flavour).generateHtml() // generate the html

        val token = call.request.cookies[JWT_COOKIE_NAME]
        token?.let {
            // check if token is valid
            val decodedToken = try {
                val verifier = HMACVerifier.newVerifier(config[jwtSecret])
                JWT.getDecoder().decode(token, verifier)
            } catch(e: Exception) {
//                val html = File("public/error.html").readText()
//                val htmlHydrated = html.replace("{{error}}", e.localizedMessage ?: "Unknown error ${e.toString()}")
//                call.respondText(htmlHydrated, ContentType.Text.Html, HttpStatusCode.BadRequest)

                revokeCookieAndUserToken()
                call.respondRedirect("logout", true)
                return@get
            }

            // Sanity check that user is in the database
            val user = users_db.find { it.email == decodedToken.allClaims["email"] as String }
            if(user == null) {
                respondErrorPage("User is not registered", HttpStatusCode.BadRequest)
                return@get
            }

            // Logout check
            if(user.token == null) {
                //respondErrorPage("User is not logged in", HttpStatusCode.BadRequest)
                revokeCookieAndUserToken()
                call.respondRedirect("/", true)
                return@get
            }

            // Check if token is expired
            val expirationTime = (decodedToken.allClaims["exp"]  as ZonedDateTime).toEpochSecond()
            if(expirationTime < System.currentTimeMillis()/1000) {
                respondErrorPage("Token is expired", HttpStatusCode.BadRequest)
                return@get
            }

            val html = File("public/home.html").readText()
            val htmlHydrated = html
                .replace("{{username}}", user.username)
                .replace("{{avatarFileName}}", user.avatarFileName ?: "")
            call.respondText(htmlHydrated, ContentType.Text.Html, HttpStatusCode.OK)

            return@get
        }

        // Show login page
        val html = File("public/index.html").readText()
        call.respondText(html, ContentType.Text.Html, HttpStatusCode.OK)
    }

    // Dump the users database
    get("/users") {
        call.respond(users_db)
    }

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
        val file = File("public/$filename")

        if(file.exists()) {
            call.respondFile(file)
        }
        else call.respond(HttpStatusCode.NotFound)
    }

    fun buildOKHTMLPage(): String {
        return """
            <!DOCTYPE html>
            <html>
            <head>
            <title>Email Sent</title>
            </head>
            <body>
            
            <h1>Email was sent</h1>
            <p>Sending email completed.</p>
            
            </body>
            </html>
        """.trimIndent()
    }

    // Send an email
    get("email") {
        val from = Email("realityexpandermail@gmail.com").apply { name = "Chris Athanas" }
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

            println("statusCode:${response.statusCode}" +
                    "body:${response.body}" +
                    "headers:${response.headers}"
            )

            call.respondText(buildOKHTMLPage(), ContentType.Text.Html, HttpStatusCode.OK)
        } catch (ex: Exception) {
            println(ex.message)
            call.respond(HttpStatusCode.InternalServerError)
        }
    }

    // The Login Page Form POSTs to `/login` to send the magic link
    post("login") {

        // Get user email from the form
        val params = call.receiveParameters()
        val email = params["email"] ?: run {
            val html = File("public/error.html").readText()
            val htmlHydrated = html.replace("{{error}}", "Email is required")
            call.respondText(htmlHydrated, ContentType.Text.Html, HttpStatusCode.BadRequest)

            return@post
        }

        // check if user is registered
        val user = users_db.find { it.email == email }
        if(user == null) {
            respondErrorPage("User is not registered", HttpStatusCode.BadRequest)
            return@post
        }

        sendMagicLinkToEmail(email, user)
    }

    // Performs the actual login using the token & sets the cookie for the client
    // URL is of the form `/login?token=...` & is sent to the client using the magic link from email
    get("login") {
        // Get the token from the url
        val token = call.request.queryParameters["token"] ?: run {
            respondErrorPage("Token is required", HttpStatusCode.BadRequest)
            return@get
        }

        // check if token is valid
        val decodedToken = try {
            val verifier = HMACVerifier.newVerifier(config[jwtSecret])
            JWT.getDecoder().decode(token, verifier)
        } catch(e: Exception) {
            respondErrorPage(e.localizedMessage ?: e.toString(), HttpStatusCode.BadRequest)
            return@get
        }
        println("decodedToken = $decodedToken")

        // check if token is expired
        val expirationTime = (decodedToken.allClaims["exp"]  as ZonedDateTime).toEpochSecond()
        if(expirationTime < System.currentTimeMillis()/1000) {
            respondErrorPage("Token is expired", HttpStatusCode.BadRequest)
            return@get
        }

        // check if token is for a valid user
        val email = decodedToken.allClaims["email"] as String
        val user = users_db.find { it.email == email }
        if(user == null) {
            respondErrorPage("User is not registered", HttpStatusCode.BadRequest)
            return@get
        }

//        // Check if user is already logged in (allows only one global login at a time)
//        if(user.token != null) {
//            respondErrorPage("User is already logged in", HttpStatusCode.BadRequest)
//            return@get
//        }

        // check if token is already in use
        if(user.token == token) {
            respondErrorPage("Token is already in use", HttpStatusCode.BadRequest)
            return@get
        }

        // save the token for the user
        users_db = users_db.toMutableList().apply {
            val index = indexOf(user)
            this[index] = user.copy(token = token)
        }

        // set the cookie
        call.response.cookies.append(JWT_COOKIE_NAME, token, httpOnly = true)

        call.respondRedirect("/", true)
    }

    // Route to Register - Form Posts to `/Register` to add a user to the database
    post("register") {
        val multipart = call.receiveMultipart()
        var username = ""
        var email = ""
        var image: ByteArray? = null
        multipart.forEachPart { part ->
            when (part) {
                is PartData.FormItem -> {
                    println("${part.name} ==> ${part.value}")
                    when(part.name) {
                        "username" -> username = part.value
                        "email" -> email = part.value
                    }
                }
                is PartData.FileItem -> {
                    println("${part.name} ==> ${part.originalFileName}")
                    image = part.streamProvider().readBytes()
                }
                else -> Unit
            }
        }

        // check if user is already registered
        val existingUser = users_db.find { it.email == email }
        if(existingUser != null) {
            val html = File("public/error.html").readText()
            val htmlHydrated = html.replace("{{error}}", "User is already registered")
            call.respondText(htmlHydrated, ContentType.Text.Html, HttpStatusCode.BadRequest)

            return@post
        }

        // save user to database
        val userId = users_db.size + 1
        val avatarFilename = "image_$userId.png"
        val serverFile = "public/avatars/$avatarFilename"
        val newUser = User(
            userId.toString(),
            username = username,
            email = email,
            token = null,
            avatarFileName = avatarFilename
        )
        users_db.add(newUser)

        // save image to file on server
        if(image != null) {
            val file = File(serverFile)
            file.writeBytes(image!!)
        }

        saveUsersToJsonFile(users_db)

        sendMagicLinkToEmail(email, newUser)
    }

    get("logout") {
        // Expire the cookie
        revokeCookieAndUserToken()

        val response = Gson().toJson(
            object {
                val message = "Logged out"
            }
        )
        call.respondText(response, ContentType.Text.JavaScript, HttpStatusCode.OK)
    }

    // Send verification email to SendGrid for Setup
    get("email2") {
        val from = Email("realityexpandermail@gmail.com")
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

            call.respond(HttpStatusCode.OK)
            println(response.headers)
        } catch (ex: IOException) {
            throw ex
        }
    }
}

private fun PipelineContext<Unit, ApplicationCall>.revokeCookieAndUserToken() {
    // Remove the token from the user
    val token = call.request.cookies[JWT_COOKIE_NAME]
    if(token != null) {
        val user = users_db.find { it.token == token }
        if(user != null) {
            users_db = users_db.toMutableList().apply {
                val index = indexOf(user)
                this[index] = user.copy(token = null)
            }
        }
    }

    call.response.cookies.append(
        JWT_COOKIE_NAME,
        "",
        path = "/",
        expires = GMTDate.START,
        httpOnly = true
    )
}

private suspend fun PipelineContext<Unit, ApplicationCall>.sendMagicLinkToEmail(
    email: String,
    user: User
) {
    // generate token
    val token = generateJWTForUser(user)
    println("email = $email, token = $token")

    val response = sendMagicLinkEmail(email, token, user.username)
    if (response.isSuccess) {
//        val html = File("public/check_email.html").readText()
//        call.respondText(html, ContentType.Text.Html, HttpStatusCode.OK)

        call.respondRedirect("/check_email.html", true)
    } else {
        val html = File("public/error.html").readText()
        val htmlHydrated = html.replace("{{error}}", response.exceptionOrNull()?.message ?: "Unknown Error")
        call.respondText(htmlHydrated, ContentType.Text.Html, HttpStatusCode.InternalServerError)
    }
}

private suspend fun PipelineContext<Unit, ApplicationCall>.respondErrorPage(
    error: String,
    statusCode: HttpStatusCode = HttpStatusCode.BadRequest
) {
    val html = File("public/error.html").readText()
    val htmlHydrated = html.replace("{{error}}", error)
    call.respondText(htmlHydrated, ContentType.Text.Html, statusCode)
}

fun generateToken(): String {
    val random = Random(System.currentTimeMillis())
    val token = StringBuilder()
    for (i in 0..31) {
        token.append(random.nextInt(10))
    }
    return token.toString()
}

fun generateJWTForUser(user: User): JWTToken {
    val jwt = JWT.getEncoder()
    val token = jwt.encode(
        JWT().apply {
            addClaim("id", user.id)
            addClaim("email", user.email)
            addClaim("name", user.username)
            expiration = ZonedDateTime.now().plusMinutes(20)
        },
        HMACSigner.newSHA256Signer(config[jwtSecret])
    )

    return token
}

fun sendMagicLinkEmail(
    email: String,
    token: String,
    username: String
): Result<String> {
    val from = Email("realityexpandermail@gmail.com").apply { name = "Chris Athanas" }
    val subject = "Magic Link for $username"
    val to = Email(email)

    val content = File("public/magic_link_email.html").readText()
    val contentHydrated = content
        .replace("{{email}}", email)
        .replace("{{token}}", token)
        .replace("{{username}}", username)
    val mail = Mail(
        from,
        subject,
        to,
        Content("text/html", contentHydrated)
    )

    println("http://localhost:8080/login?token={{token}}".replace("{{token}}", token))

    try {
        val sendGrid = SendGrid(config[sendgridApiKey])

        val response = sendGrid.api(
            Request().apply {
                method = Method.POST
                endpoint = "mail/send"
                body = mail.build()
            })

        println("statusCode:${response.statusCode}" +
                "body:${response.body}" +
                "headers:${response.headers}"
        )

        return Result.success("Email sent successfully")
    } catch (ex: Exception) {
        println(ex.message)
        return Result.failure(ex)
    }
}

fun loadUsersCSV(): MutableList<User> {
    val users = mutableListOf<User>()
    val file = File("users.csv")
    if(file.exists()) {
        file.forEachLine {
            val parts = it.split(",")
            val user = User(parts[0], parts[1], parts[2], parts[3], parts[4])
            users.add(user)
        }
    }
    return users
}

fun saveUsersCSV(users: List<User>) {
    val file = File("users.csv")
    users.forEach {
        file.appendText("${it.id},${it.username},${it.email},${it.avatarFileName}")
    }
}

fun loadUsersFromJsonFile(): MutableList<User>? {
    try {
        val file = File("users.json")
        if(file.exists()) {
            val json = file.readText()
            return Gson().fromJson(json, Array<User>::class.java).toMutableList()
        }
    } catch (ex: Exception) {
        println(ex.message)
    }

    return null
}

fun saveUsersToJsonFile(users: List<User>) {
    val json = Gson().toJson(users)
    val file = File("users.json")
    file.writeText(json)
}