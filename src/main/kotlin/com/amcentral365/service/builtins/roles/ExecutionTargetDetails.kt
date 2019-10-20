package com.amcentral365.service.builtins.roles

import com.amcentral365.service.StatusException

data class ExecutionTargetDetails(
    val workDirBase: String? = null,
    val commandToCreateWorkDir:    List<String>? = null,
    val commandToRemoveWorkDir:    List<String>? = null,
    val commandToCreateSubDir:     List<String>? = null,
    val commandToCreateFile:       List<String>? = null,
    val commandToCreateExecutable: List<String>? = null,
    val commandToRemoveFile:       List<String>? = null,
    val commandToVerifyFileExists: List<String>? = null
): AnAsset(null)
