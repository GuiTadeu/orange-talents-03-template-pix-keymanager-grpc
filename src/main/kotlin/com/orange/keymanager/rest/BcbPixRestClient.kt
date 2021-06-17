package com.orange.keymanager.rest

import com.orange.keymanager.models.AccountType
import com.orange.keymanager.models.AccountType.CONTA_CORRENTE
import com.orange.keymanager.models.AccountType.CONTA_POUPANCA
import com.orange.keymanager.models.KeyType
import io.micronaut.core.annotation.Introspected
import io.micronaut.http.MediaType.APPLICATION_XML
import io.micronaut.http.annotation.*
import io.micronaut.http.client.annotation.Client
import java.time.LocalDateTime

@Consumes(APPLICATION_XML)
@Produces(APPLICATION_XML)
@Client(value = "\${services.bcb}")
interface BcbPixRestClient {

    @Post("/api/v1/pix/keys")
    fun saveKey(@Body request: BcbSaveKeyRequest): BcbSaveKeyResponse

    @Get("/api/v1/pix/keys/{keyValue}")
    fun searchKeyValue(@PathVariable keyValue: String): BcbSearchKeyResponse

    @Delete("/api/v1/pix/keys/{keyValue}")
    fun deleteKeyValue(@PathVariable keyValue: String, @Body request: BcbDeleteKeyRequest)
}

@Introspected
class BcbDeleteKeyRequest(
    val key: String,
    val participant: String
)

@Introspected
class BcbSaveKeyRequest(
    val keyType: KeyType,
    val key: String,
    val bankAccount: BankAccount,
    val owner: BcbOwner
)

@Introspected
class BcbSaveKeyResponse(
    val keyType: KeyType,
    val key: String,
    val bankAccount: BankAccount,
    val owner: BcbOwner,
    val createdAt: LocalDateTime
)

@Introspected
class BcbSearchKeyResponse(
    val keyType: KeyType,
    val key: String,
    val bankAccount: BankAccount,
    val owner: BcbOwner,
    val createdAt: LocalDateTime
)

@Introspected
class BankAccount(
    val participant: String,
    val branch: String,
    val accountNumber: String,
    val accountType: BcbAccountType
)

@Introspected
class BcbOwner(
    val type: PersonType,
    val name: String,
    val taxIdNumber: String
)

@Introspected
enum class PersonType {
    NATURAL_PERSON,
    LEGAL_PERSON
}

@Introspected
enum class BcbAccountType {
    CACC {
        override fun getApplicationAccountType(): AccountType {
            return CONTA_CORRENTE
        }
    }, SVGS {
        override fun getApplicationAccountType(): AccountType {
            return CONTA_POUPANCA
        }
    };

    abstract fun getApplicationAccountType(): AccountType
}