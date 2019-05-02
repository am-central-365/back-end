package com.amcentral365.service.data

import java.nio.file.Paths

class FileWalker { companion object {
    fun walk(baseDirName: String) {
        val path = Paths.get("data", baseDirName)
    }
}}
