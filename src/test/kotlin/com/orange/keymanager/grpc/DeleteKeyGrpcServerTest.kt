package com.orange.keymanager.grpc

import com.orange.keymanager.*
import com.orange.keymanager.DeleteKeyServiceGrpc.DeleteKeyServiceBlockingStub
import com.orange.keymanager.models.AccountType.CONTA_CORRENTE
import com.orange.keymanager.models.KeyType.EMAIL
import com.orange.keymanager.models.PixClientKey
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
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito
import java.util.*
import javax.inject.Singleton
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach

@MicronautTest(transactional = false)
class DeleteKeyGrpcServerTest(
    private val grpcClient: DeleteKeyServiceBlockingStub,
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
    fun `deve retornar erro quando nao encontrar a chave Pix`() {
        val grpcRequest = DeleteKeyRequest.newBuilder()
            .setClientId(clientId)
            .setKeyId(Long.MIN_VALUE)
            .build()

        val error = assertThrows<StatusRuntimeException> {
            grpcClient.deleteKey(grpcRequest)
        }

        with(error) {
            assertEquals(Status.NOT_FOUND.code, status.code)
            assertEquals("Key ID not exists", status.description)
        }
    }

    @Test
    fun `deve retornar erro quando a chave Pix nao pertencer ao cliente`() {

        val savedPixKey = pixClientRepository.save(PixClientKey(clientId, EMAIL, "jubileu@gmail.com", CONTA_CORRENTE))

        val grpcRequest = DeleteKeyRequest.newBuilder()
            .setClientId(UUID.randomUUID().toString())
            .setKeyId(savedPixKey.id!!)
            .build()

        val error = assertThrows<StatusRuntimeException> {
            grpcClient.deleteKey(grpcRequest)
        }

        with(error) {
            assertEquals(Status.PERMISSION_DENIED.code, status.code)
            assertEquals("Key does not belong to clientId", status.description)
        }
    }

    @Test
    fun `deve deletar chave Pix no banco`() {
        val savedPixKey = pixClientRepository.save(PixClientKey(clientId, EMAIL, "jubileu@gmail.com", CONTA_CORRENTE))
        assertTrue(pixClientRepository.findById(savedPixKey.id!!).isPresent)

        val grpcRequest = DeleteKeyRequest.newBuilder()
            .setClientId(clientId)
            .setKeyId(savedPixKey.id!!)
            .build()

        val grpcResponse = grpcClient.deleteKey(grpcRequest)
        assertTrue(pixClientRepository.findById(grpcResponse.deletedKeyId).isEmpty)
    }

    @MockBean(ItauErpRestClient::class)
    fun erpClientMock(): ItauErpRestClient {
        return Mockito.mock(ItauErpRestClient::class.java)
    }

    @Factory
    class DeleteKeyClient {

        @Singleton
        fun blockingStub(@GrpcChannel(GrpcServerChannel.NAME) channel: ManagedChannel) : DeleteKeyServiceBlockingStub? {
            return DeleteKeyServiceGrpc.newBlockingStub(channel)
        }
    }

}