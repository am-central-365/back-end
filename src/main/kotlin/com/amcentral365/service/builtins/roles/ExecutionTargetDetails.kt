package com.amcentral365.service.builtins.roles

data class ExecutionTargetDetails(
    val workDirBase: String? = null,
    val commandToCreateWorkDir:    List<String>? = null,
    val commandToRemoveWorkDir:    List<String>? = null,
    val commandToCreateSubDir:     List<String>? = null,
    val commandToCreateFile:       List<String>? = null,
    var commandToCreateExecutable: List<String>? = null
): AnAsset(null)
