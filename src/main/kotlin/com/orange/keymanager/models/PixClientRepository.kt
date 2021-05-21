package com.orange.keymanager.models

import io.micronaut.context.annotation.Executable
import io.micronaut.data.annotation.Repository
import io.micronaut.data.repository.CrudRepository
import java.util.*

@Repository
interface PixClientRepository : CrudRepository<PixClientKey, Long> {

    @Executable
    fun findByKeyValue(keyValue: String): Optional<PixClientKey>

    @Executable
    fun findByIdAndClientId(id: Long, clientId: String): Optional<PixClientKey>
}