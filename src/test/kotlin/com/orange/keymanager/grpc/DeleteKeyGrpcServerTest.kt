package com.orange.keymanager.grpc

import com.orange.keymanager.*
import com.orange.keymanager.DeleteKeyServiceGrpc.DeleteKeyServiceBlockingStub
import com.orange.keymanager.models.AccountType.CONTA_CORRENTE
import com.orange.keymanager.models.KeyType.EMAIL
import com.orange.keymanager.models.PixClientKey
import com.orange.keymanager.models.PixClientRepository
import com.orange.keymanager.rest.BcbPixRestClient
import com.orange.keymanager.rest.ItauErpRestClient
import com.orange.keymanager.rest.ItauFoundClientIdResponse
import io.grpc.ManagedChannel
import io.grpc.Status
import io.grpc.StatusRuntimeException
import io.kotlintest.specs.BehaviorSpec
import io.micronaut.context.annotation.Factory
import io.micronaut.grpc.annotation.GrpcChannel
import io.micronaut.grpc.server.GrpcServerChannel
import io.micronaut.test.annotation.MockBean
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.*
import javax.inject.Singleton
import org.junit.jupiter.api.BeforeEach
import java.lang.RuntimeException
import javax.inject.Inject

@MicronautTest
class DeleteKeyGrpcServerTest(
    private val grpcClient: DeleteKeyServiceBlockingStub): BehaviorSpec() {

    @Inject lateinit var erpClient: ItauErpRestClient
    @Inject lateinit var bcbClient: BcbPixRestClient
    @Inject lateinit var pixClientRepository: PixClientRepository

    private var clientId: String = UUID.randomUUID().toString()

    @BeforeEach
    fun setup() {
        every { erpClient.findByClientId(clientId) }
            .returns(ItauFoundClientIdResponse(clientId))
    }

    @Test
    fun `deve retornar erro quando a request estiver invalida`() {
        val grpcRequest = DeleteKeyRequest.newBuilder()
            .setClientId("KKKKKKK")
            .setKeyId(0L)
            .build()

        val error = assertThrows<StatusRuntimeException> {
            grpcClient.deleteKey(grpcRequest)
        }

        with(error) {
            assertEquals(Status.INVALID_ARGUMENT.code, status.code)
            assertEquals("Invalid arguments", status.description)
        }
    }

    @Test
    fun `deve retornar erro quando a chave Pix nao pertencer ao cliente`() {
        val grpcRequest = DeleteKeyRequest.newBuilder()
            .setClientId(clientId)
            .setKeyId(1L)
            .build()

        every { pixClientRepository.findByIdAndClientId(any(), clientId) }
            .returns(Optional.empty())

        val error = assertThrows<StatusRuntimeException> {
            grpcClient.deleteKey(grpcRequest)
        }

        with(error) {
            assertEquals(Status.PERMISSION_DENIED.code, status.code)
            assertEquals("Key does not belong to clientId", status.description)
        }
    }

    @Test
    fun `deve deletar chave Pix na aplicacao`() {
        val savedPixKey = PixClientKey(clientId, EMAIL, "jubileu@gmail.com", CONTA_CORRENTE)
        savedPixKey.id = 42L

        every { pixClientRepository.findByIdAndClientId(any(), clientId) }
            .returns(Optional.of(savedPixKey))

        every { bcbClient.existsKeyValue(any()) }.answers{}
        every { bcbClient.deleteKeyValue(any(), any()) }.answers{}
        every { pixClientRepository.deleteById(any()) }.answers{}

        val grpcRequest = DeleteKeyRequest.newBuilder()
            .setClientId(clientId)
            .setKeyId(savedPixKey.id!!)
            .build()

        val grpcResponse = grpcClient.deleteKey(grpcRequest)
        assertTrue(grpcResponse.deletedKeyId == savedPixKey.id)
    }

    @Test
    fun `deve abortar a operacao caso nao exista a chave no BCB`() {
        val savedPixKey = PixClientKey(clientId, EMAIL, "jubileu@gmail.com", CONTA_CORRENTE)
        savedPixKey.id = 42L

        every { pixClientRepository.findByIdAndClientId(any(), clientId) }
            .returns(Optional.of(savedPixKey))

        every { bcbClient.existsKeyValue(any()) }.throws(RuntimeException())

        val grpcRequest = DeleteKeyRequest.newBuilder()
            .setClientId(clientId)
            .setKeyId(savedPixKey.id!!)
            .build()

        val error = assertThrows<StatusRuntimeException> {
            grpcClient.deleteKey(grpcRequest)
        }

        with(error) {
            assertEquals(Status.ABORTED.code, status.code)
            assertEquals("Key value does not exists in BCB", status.description)
        }
    }

    @Test
    fun `deve abortar a operacao caso o BCB nao delete a chave`() {
        val savedPixKey = PixClientKey(clientId, EMAIL, "jubileu@gmail.com", CONTA_CORRENTE)
        savedPixKey.id = 42L

        every { pixClientRepository.findByIdAndClientId(any(), clientId) }
            .returns(Optional.of(savedPixKey))

        every { bcbClient.existsKeyValue(any()) }.answers{}

        every { bcbClient.deleteKeyValue(any(), any()) }
            .throws(RuntimeException())

        val grpcRequest = DeleteKeyRequest.newBuilder()
            .setClientId(clientId)
            .setKeyId(savedPixKey.id!!)
            .build()

        val error = assertThrows<StatusRuntimeException> {
            grpcClient.deleteKey(grpcRequest)
        }

        with(error) {
            assertEquals(Status.ABORTED.code, status.code)
            assertEquals("It was not possible to delete the key in the bcb, try again...", status.description)
        }
    }

    @MockBean(ItauErpRestClient::class)
    fun erpClientMock(): ItauErpRestClient {
        return mockk()
    }

    @MockBean(BcbPixRestClient::class)
    fun bcbClientMock(): BcbPixRestClient {
        return mockk()
    }

    @MockBean(PixClientRepository::class)
    fun pixClientRepositoryMock(): PixClientRepository {
        return mockk()
    }

    @Factory
    class DeleteKeyClient {

        @Singleton
        fun blockingStub(@GrpcChannel(GrpcServerChannel.NAME) channel: ManagedChannel) : DeleteKeyServiceBlockingStub? {
            return DeleteKeyServiceGrpc.newBlockingStub(channel)
        }
    }
}