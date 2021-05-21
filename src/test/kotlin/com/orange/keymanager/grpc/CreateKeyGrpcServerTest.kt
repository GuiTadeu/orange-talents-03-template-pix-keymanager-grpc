package com.orange.keymanager.grpc

import com.orange.keymanager.AccountType.CONTA_CORRENTE
import com.orange.keymanager.CreateKeyRequest
import com.orange.keymanager.CreateKeyServiceGrpc
import com.orange.keymanager.CreateKeyServiceGrpc.CreateKeyServiceBlockingStub
import com.orange.keymanager.KeyType.*
import com.orange.keymanager.models.AccountType
import com.orange.keymanager.models.KeyType
import com.orange.keymanager.models.PixClientRepository
import com.orange.keymanager.rest.ItauErpRestClient
import com.orange.keymanager.rest.ItauFoundClientIdResponse
import io.grpc.ManagedChannel
import io.grpc.Status
import io.grpc.StatusRuntimeException
import io.micronaut.context.annotation.Factory
import io.micronaut.grpc.annotation.GrpcChannel
import io.micronaut.grpc.server.GrpcServerChannel
import io.micronaut.test.annotation.MockBean
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito
import java.util.*
import javax.inject.Singleton

@MicronautTest(transactional = false)
internal class CreateKeyGrpcServerTest(
    private val grpcClient: CreateKeyServiceBlockingStub,
    private val erpClient: ItauErpRestClient,
    private val pixClientRepository: PixClientRepository
) {

    private var clientId: String = UUID.randomUUID().toString()

    @BeforeEach
    fun setup() {
        Mockito.`when`(erpClient.findByClientId(clientId))
            .thenReturn(ItauFoundClientIdResponse(clientId))

        pixClientRepository.deleteAll()
    }

    @AfterEach
    fun cleanUp() {
        pixClientRepository.deleteAll()
    }

    @Test
    internal fun `deve dar erro ao tentar cadastrar chave ja existente`() {
        val grpcRequest = CreateKeyRequest.newBuilder()
            .setClientId(clientId)
            .setKeyType(CPF)
            .setKeyValue("308.972.740-40")
            .setAccountType(CONTA_CORRENTE)
            .build()

        val grpcResponse = grpcClient.createKey(grpcRequest)
        val savedPixKey = pixClientRepository.findById(grpcResponse.keyId).get()

        assertEquals(savedPixKey.clientId, grpcRequest.clientId)
        assertEquals(savedPixKey.keyValue, grpcRequest.keyValue)
        assertEquals(savedPixKey.keyType, KeyType.valueOf(grpcRequest.keyType.toString()))
        assertEquals(savedPixKey.accountType, AccountType.valueOf(grpcRequest.accountType.toString()))

        val error = assertThrows<StatusRuntimeException> {
            grpcClient.createKey(grpcRequest)
        }

        with(error) {
            assertEquals(Status.ALREADY_EXISTS.code, status.code)
            assertEquals("Pix key already registered", status.description)
        }
    }

    @Test
    internal fun `deve dar NOT_FOUND caso o cliente nao exista no ERP do Itau`() {
        val grpcRequest = CreateKeyRequest.newBuilder()
            .setClientId(clientId)
            .setKeyType(CPF)
            .setKeyValue("308.972.740-40")
            .setAccountType(CONTA_CORRENTE)
            .build()

        Mockito.`when`(erpClient.findByClientId(clientId)).thenThrow(RuntimeException())

        val error = assertThrows<StatusRuntimeException> {
            grpcClient.createKey(grpcRequest)
        }

        with(error) {
            assertEquals(Status.NOT_FOUND.code, status.code)
            assertEquals("Client ID not exists", status.description)
        }
    }

    @Test
    internal fun `deve criar chave CPF se o keyValue for valido`() {
        val grpcRequest = CreateKeyRequest.newBuilder()
            .setClientId(clientId)
            .setKeyType(CPF)
            .setKeyValue("308.972.740-40")
            .setAccountType(CONTA_CORRENTE)
            .build()

        val grpcResponse = grpcClient.createKey(grpcRequest)
        val savedPixKey = pixClientRepository.findById(grpcResponse.keyId).get()

        assertEquals(savedPixKey.clientId, grpcRequest.clientId)
        assertEquals(savedPixKey.keyValue, grpcRequest.keyValue)
        assertEquals(savedPixKey.keyType, KeyType.valueOf(grpcRequest.keyType.toString()))
        assertEquals(savedPixKey.accountType, AccountType.valueOf(grpcRequest.accountType.toString()))
    }

    @Test
    internal fun `nao deve criar chave CPF se o keyValue for invalido`() {
        val grpcRequest = CreateKeyRequest.newBuilder()
            .setClientId(clientId)
            .setKeyType(CPF)
            .setKeyValue("123456789101")
            .setAccountType(CONTA_CORRENTE)
            .build()

        val error = assertThrows<StatusRuntimeException> {
            grpcClient.createKey(grpcRequest)
        }

        with(error) {
            assertEquals(Status.INVALID_ARGUMENT.code, status.code)
            assertEquals("Invalid key value", status.description)
        }
    }

    @Test
    internal fun `deve criar chave PHONE_NUMBER se o keyValue for valido`() {
        val grpcRequest = CreateKeyRequest.newBuilder()
            .setClientId(clientId)
            .setKeyType(PHONE_NUMBER)
            .setKeyValue("+5511940028922")
            .setAccountType(CONTA_CORRENTE)
            .build()

        val grpcResponse = grpcClient.createKey(grpcRequest)
        val savedPixKey = pixClientRepository.findById(grpcResponse.keyId).get()

        assertEquals(savedPixKey.clientId, grpcRequest.clientId)
        assertEquals(savedPixKey.keyValue, grpcRequest.keyValue)
        assertEquals(savedPixKey.keyType, KeyType.valueOf(grpcRequest.keyType.toString()))
        assertEquals(savedPixKey.accountType, AccountType.valueOf(grpcRequest.accountType.toString()))
    }

    @Test
    internal fun `nao deve criar chave PHONE_NUMBER se o keyValue for invalido`() {
        val grpcRequest = CreateKeyRequest.newBuilder()
            .setClientId(clientId)
            .setKeyType(PHONE_NUMBER)
            .setKeyValue("308.972.740-40")
            .setAccountType(CONTA_CORRENTE)
            .build()

        val error = assertThrows<StatusRuntimeException> {
            grpcClient.createKey(grpcRequest)
        }

        with(error) {
            assertEquals(Status.INVALID_ARGUMENT.code, status.code)
            assertEquals("Invalid key value", status.description)
        }
    }

    @Test
    internal fun `deve criar chave EMAIL se o keyValue for valido`() {
        val grpcRequest = CreateKeyRequest.newBuilder()
            .setClientId(clientId)
            .setKeyType(EMAIL)
            .setKeyValue("jubileu@gmail.com")
            .setAccountType(CONTA_CORRENTE)
            .build()

        val grpcResponse = grpcClient.createKey(grpcRequest)
        val savedPixKey = pixClientRepository.findById(grpcResponse.keyId).get()

        assertEquals(savedPixKey.clientId, grpcRequest.clientId)
        assertEquals(savedPixKey.keyValue, grpcRequest.keyValue)
        assertEquals(savedPixKey.keyType, KeyType.valueOf(grpcRequest.keyType.toString()))
        assertEquals(savedPixKey.accountType, AccountType.valueOf(grpcRequest.accountType.toString()))
    }

    @Test
    internal fun `nao deve criar chave EMAIL se o keyValue for invalido`() {
        val grpcRequest = CreateKeyRequest.newBuilder()
            .setClientId(clientId)
            .setKeyType(EMAIL)
            .setKeyValue("+5511940028922")
            .setAccountType(CONTA_CORRENTE)
            .build()

        val error = assertThrows<StatusRuntimeException> {
            grpcClient.createKey(grpcRequest)
        }

        with(error) {
            assertEquals(Status.INVALID_ARGUMENT.code, status.code)
            assertEquals("Invalid key value", status.description)
        }
    }

    @Test
    internal fun `deve criar chave RANDOM se o keyValue for vazio`() {
        val grpcRequest = CreateKeyRequest.newBuilder()
            .setClientId(clientId)
            .setKeyType(RANDOM)
            .setKeyValue("")
            .setAccountType(CONTA_CORRENTE)
            .build()

        val grpcResponse = grpcClient.createKey(grpcRequest)
        val savedPixKey = pixClientRepository.findById(grpcResponse.keyId).get()

        assertEquals(savedPixKey.clientId, grpcRequest.clientId)
        assertTrue(savedPixKey.keyValue.matches(Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")))
        assertEquals(savedPixKey.keyType, KeyType.valueOf(grpcRequest.keyType.toString()))
        assertEquals(savedPixKey.accountType, AccountType.valueOf(grpcRequest.accountType.toString()))
    }

    @Test
    internal fun `nao deve criar chave aleatoria se o keyValue for preenchido`() {
        val grpcRequest = CreateKeyRequest.newBuilder()
            .setClientId(clientId)
            .setKeyType(RANDOM)
            .setKeyValue("NOT_PERMITTED")
            .setAccountType(CONTA_CORRENTE)
            .build()

        val error = assertThrows<StatusRuntimeException> {
            grpcClient.createKey(grpcRequest)
        }

        with(error) {
            assertEquals(Status.INVALID_ARGUMENT.code, status.code)
            assertEquals("Invalid key value", status.description)
        }
    }

    @MockBean(ItauErpRestClient::class)
    fun erpClientMock(): ItauErpRestClient {
        return Mockito.mock(ItauErpRestClient::class.java)
    }

    @Factory
    class CreateKeyClient {

        @Singleton
        fun blockingStub(@GrpcChannel(GrpcServerChannel.NAME) channel: ManagedChannel) : CreateKeyServiceBlockingStub?{
            return CreateKeyServiceGrpc.newBlockingStub(channel)
        }
    }
}