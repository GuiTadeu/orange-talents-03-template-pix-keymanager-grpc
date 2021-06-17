package com.orange.keymanager.rest

import com.orange.keymanager.models.AccountType
import io.micronaut.core.annotation.Introspected
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.PathVariable
import io.micronaut.http.annotation.QueryValue
import io.micronaut.http.client.annotation.Client

@Client(value = "\${services.erp}")
interface ItauErpRestClient {

    @Get("/api/v1/clientes/{clientId}")
    fun findByClientId(@PathVariable clientId: String) : ItauFoundClientIdResponse

    @Get("/api/v1/clientes/{clienteId}/contas")
    fun getClientByIdAndAccountType(@PathVariable clienteId: String, @QueryValue tipo: AccountType?) : ItauFoundClientAccountResponse
}

@Introspected
class ItauFoundClientIdResponse(val id: String)

@Introspected
class ItauFoundClientAccountResponse(
    val tipo: AccountType,
    val instituicao: Bank,
    val agencia: String,
    val numero: String,
    val titular: ItauOwner
) {
    fun getTitularNome(): String {
        return titular.nome
    }

    fun getTitularCpf(): String {
        return titular.cpf
    }

    fun getBcbAccountType(): BcbAccountType {
        return tipo.getBcbAccountType()
    }
}

class Bank(
    val nome: String,
    val ispb: String
)

@Introspected
class ItauOwner(
    val id: String,
    val nome: String,
    val cpf: String
)