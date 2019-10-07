package com.amcentral365.service.builtins.roles

import com.amcentral365.service.dao.Asset

data class TargetSSH (
    var hostname: String?,
    var port:     Int?,
    var loginUser: String?
): AnAsset(null)
