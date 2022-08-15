package com.realityexpander.routes

import io.ktor.http.content.*
import java.io.File

fun PartData.FileItem.save(path: String, fileName: String): String {
    val fileBytes = streamProvider().readBytes()
    val folder = File(path)
    folder.mkdirs()
    File("$path$fileName").writeBytes(fileBytes)
    return fileName
}