package com.orange.keymanager.rest

import io.micronaut.core.annotation.Introspected
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.PathVariable
import io.micronaut.http.client.annotation.Client

@Client("http://localhost:9091/api/v1")
interface ItauErpRestClient {

    @Get("/clientes/{clientId}")
    fun findByClientId(@PathVariable clientId: String) : ItauFoundClientIdResponse
}

@Introspected
class ItauFoundClientIdResponse(val id: String)