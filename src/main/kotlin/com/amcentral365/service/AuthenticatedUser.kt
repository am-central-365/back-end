package com.amcentral365.service

data class AuthenticatedUser(val userId: String, var email: String?, var fullName: String?)

class Authorization {
    companion object {
        val admins: List<String> = listOf("amcentral365")
    }


    fun isAdmin(usr: AuthenticatedUser) = Authorization.admins.contains(usr.userId)
}