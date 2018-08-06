package com.amcentral365.service.dao

import com.amcentral365.pl4kotlin.Entity
import com.amcentral365.pl4kotlin.Table
import kotlin.reflect.KClass
import kotlin.reflect.full.findAnnotation

class Meta {

    companion object {
        val entities: List<KClass<out Entity>> = listOf(
            Role::class
          , ScriptStore::class
        ).sortedBy { Meta.tableName(it) }

        fun tableName(clazz: KClass<out Entity>) = clazz.findAnnotation<Table>()!!.tableName
    }

}