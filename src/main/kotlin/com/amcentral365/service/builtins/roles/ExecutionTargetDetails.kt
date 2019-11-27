package com.amcentral365.service.builtins.roles

data class ExecutionTargetDetails(
    val workDirBase: String? = null,
    val commandToCreateWorkDir:    List<String>? = null,
    val commandToRemoveWorkDir:    List<String>? = null,
    val commandToExecuteMain:      List<String>? = null
): AnAsset(null)
