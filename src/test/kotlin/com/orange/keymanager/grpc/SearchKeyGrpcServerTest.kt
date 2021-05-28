package com.orange.keymanager.grpc

import com.orange.keymanager.SearchKeyMessage
import com.orange.keymanager.SearchKeyMessage.InternalSearchKeyRequest
import com.orange.keymanager.SearchKeyMessage.SearchKeyRequest
import com.orange.keymanager.SearchKeyServiceGrpc
import com.orange.keymanager.SearchKeyServiceGrpc.SearchKeyServiceBlockingStub
import com.orange.keymanager.models.AccountType.CONTA_CORRENTE
import com.orange.keymanager.models.KeyType
import com.orange.keymanager.models.PixClientKey
import com.orange.keymanager.models.PixClientRepository
import com.orange.keymanager.rest.*
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
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.lang.RuntimeException
import java.time.LocalDateTime
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@MicronautTest
internal class SearchKeyGrpcServerTest(
    private val grpcClient: SearchKeyServiceBlockingStub): BehaviorSpec() {

    @Inject lateinit var bcbClient: BcbPixRestClient
    @Inject lateinit var erpClient: ItauErpRestClient
    @Inject lateinit var pixClientRepository: PixClientRepository

    // BEGIN INTERNAL SEARCH TESTS ################################################################

    @Test
    fun `deve retornar NOT_FOUND caso a keyId nao exista no banco da aplicacao`() {
        val grpcRequest = InternalSearchKeyRequest.newBuilder()
            .setKeyId(42L)
            .build()

        every { pixClientRepository.findById(42L) }.returns(Optional.empty())

        val error = assertThrows<StatusRuntimeException> {
            grpcClient.internalSearchKey(grpcRequest)
        }

        with(error) {
            assertEquals(Status.NOT_FOUND.code, status.code)
            assertEquals("Key does not exists", status.description)
        }
    }

    @Test
    fun `deve retornar PERMISSION_DENIED caso a keyId nao pertenca ao clientId`() {

        val ownerId = UUID.randomUUID().toString()
        val otherClientId = UUID.randomUUID().toString()

        val grpcRequest = InternalSearchKeyRequest.newBuilder()
            .setKeyId(42L)
            .setClientId(otherClientId)
            .build()

        every { pixClientRepository.findById(42L) } answers {
            Optional.of(PixClientKey(ownerId, KeyType.EMAIL, "jubileu@gmail.com", CONTA_CORRENTE))
        }

        val error = assertThrows<StatusRuntimeException> {
            grpcClient.internalSearchKey(grpcRequest)
        }

        with(error) {
            assertEquals(Status.PERMISSION_DENIED.code, status.code)
            assertEquals("Key does not belong to clientId", status.description)
        }
    }

    @Test
    fun `deve retornar ABORTED caso a chave nao esteja cadastrada no BCB`() {

        val ownerId = UUID.randomUUID().toString()

        val grpcRequest = InternalSearchKeyRequest.newBuilder()
            .setKeyId(42L)
            .setClientId(ownerId)
            .build()

        every { pixClientRepository.findById(42L) } answers {
            Optional.of(PixClientKey(ownerId, KeyType.EMAIL, "jubileu@gmail.com", CONTA_CORRENTE))
        }

        every { bcbClient.searchKeyValue("jubileu@gmail.com")}.throws(RuntimeException())

        val error = assertThrows<StatusRuntimeException> {
            grpcClient.internalSearchKey(grpcRequest)
        }

        with(error) {
            assertEquals(Status.ABORTED.code, status.code)
            assertEquals("Key value does not exists in BCB", status.description)
        }
    }

    @Test
    fun `deve retornar chave encontrada no banco da aplicacao passando o keyId e o clientId (Internal)`() {

        val ownerId = UUID.randomUUID().toString()

        val timeSavedOnApplication = LocalDateTime.now()
        val timeSavedOnBcb = LocalDateTime.now().plusSeconds(5L)

        val grpcRequest = InternalSearchKeyRequest.newBuilder()
            .setKeyId(42L)
            .setClientId(ownerId)
            .build()

        every { pixClientRepository.findById(42L) } answers {
            val pixClientKey = PixClientKey(ownerId, KeyType.EMAIL, "jubileu@gmail.com", CONTA_CORRENTE)
            pixClientKey.id = 42L
            pixClientKey.createdAt = timeSavedOnApplication

            Optional.of(pixClientKey)
        }

        every { bcbClient.searchKeyValue("jubileu@gmail.com")} answers {
            BcbSearchKeyResponse(
                KeyType.EMAIL,
                "jubileu@gmail.com",
                BankAccount("123456", "0001", "40028922", BcbAccountType.CACC),
                BcbOwner(PersonType.NATURAL_PERSON, "Jubileu da Silva", "24852806810"),
                timeSavedOnBcb)
        }

        val grpcResponse = grpcClient.internalSearchKey(grpcRequest)

        with(grpcResponse) {
            assertEquals(keyId, 42L)
            assertEquals(keyType, SearchKeyMessage.KeyType.EMAIL)
            assertEquals(keyValue, "jubileu@gmail.com")
            assertEquals(owner.name, "Jubileu da Silva")
            assertEquals(owner.cpf, "24852806810")
            assertEquals(bank.branch, "0001")
            assertEquals(bank.accountNumber, "40028922")
            assertEquals(bank.name, "ITAÚ UNIBANCO S.A.")
            assertEquals(bank.accountType, SearchKeyMessage.AccountType.CONTA_CORRENTE)
            assertEquals(createdAt, timeSavedOnApplication.toString())
        }
    }

    // END INTERNAL SEARCH TESTS ##################################################################

    //////////////////////////////////////////////////////////////////////////////////////////////

    // BEGIN SEARCH TESTS #########################################################################

    @Test
    fun `deve retornar INVALID_ARGUMENTS caso a keyValue tenha mais que 77 caracteres`() {
        val grpcRequest = SearchKeyRequest.newBuilder()
            .setKeyValue(
                "Eu sou como o velho barco que guarda no seu bojo " +
                "O eterno ruído do mar batendo " +
                "No entanto, como está longe o mar e como é dura a terra sob mim " +
                "Felizes são os pássaros que chegam mais cedo que eu à suprema fraqueza " +
                "E voando caem, felizes e abençoados, nos parques onde a primavera é eterna.")
            .build()

        val error = assertThrows<StatusRuntimeException> {
            grpcClient.searchKey(grpcRequest)
        }

        with(error) {
            assertEquals(Status.INVALID_ARGUMENT.code, status.code)
            assertEquals("Invalid arguments", status.description)
        }
    }

    @Test
    fun `deve retornar chave encontrada no BCB caso nao tenha na aplicacao`() {

        val timeSavedOnApplication = LocalDateTime.now()
        val timeSavedOnBcb = LocalDateTime.now().plusSeconds(5L)

        val grpcRequest = SearchKeyRequest.newBuilder()
            .setKeyValue("jubileu@gmail.com")
            .build()

        every { pixClientRepository.findByKeyValue("jubileu@gmail.com") }.returns(Optional.empty())

        every { bcbClient.searchKeyValue("jubileu@gmail.com") } answers {
            BcbSearchKeyResponse(
                KeyType.EMAIL,
                "jubileu@gmail.com",
                BankAccount("123456", "0001", "40028922", BcbAccountType.CACC),
                BcbOwner(PersonType.NATURAL_PERSON, "Jubileu da Silva", "24852806810"),
                timeSavedOnBcb)
        }

        val grpcResponse = grpcClient.searchKey(grpcRequest)

        with(grpcResponse) {
            assertEquals(keyType, SearchKeyMessage.KeyType.EMAIL)
            assertEquals(keyValue, "jubileu@gmail.com")
            assertEquals(owner.name, "Jubileu da Silva")
            assertEquals(owner.cpf, "24852806810")
            assertEquals(bank.branch, "0001")
            assertEquals(bank.accountNumber, "40028922")
            assertEquals(bank.name, "ITAÚ UNIBANCO S.A.")
            assertEquals(bank.accountType, SearchKeyMessage.AccountType.CONTA_CORRENTE)
            assertEquals(createdAt, timeSavedOnBcb.toString())
        }
    }

    @Test
    fun `deve retornar NOT_FOUND caso nao encontre chave na aplicacao e no BCB`() {
        val grpcRequest = SearchKeyRequest.newBuilder()
            .setKeyValue("jubileu@gmail.com")
            .build()

        every { pixClientRepository.findByKeyValue("jubileu@gmail.com") }.returns(Optional.empty())
        every { bcbClient.searchKeyValue("jubileu@gmail.com") }.throws(RuntimeException())

        val error = assertThrows<StatusRuntimeException> {
            grpcClient.searchKey(grpcRequest)
        }

        with(error) {
            assertEquals(Status.NOT_FOUND.code, status.code)
            assertEquals("Key does not exists on bcb", status.description)
        }
    }

    @Test
    fun `deve retornar NOT_FOUND caso nao encontre o cliente no ERP`() {

        val ownerId = UUID.randomUUID().toString()
        val timeSavedOnApplication = LocalDateTime.now()
        val timeSavedOnBcb = LocalDateTime.now().plusSeconds(5L)

        val grpcRequest = SearchKeyRequest.newBuilder()
            .setKeyValue("jubileu@gmail.com")
            .build()

        every { pixClientRepository.findByKeyValue("jubileu@gmail.com") } answers {
            val pixClientKey = PixClientKey(ownerId, KeyType.EMAIL, "jubileu@gmail.com", CONTA_CORRENTE)
            pixClientKey.id = 42L
            pixClientKey.createdAt = timeSavedOnApplication

            Optional.of(pixClientKey)
        }

        every { erpClient.getClientByIdAndAccountType(ownerId, CONTA_CORRENTE) }.throws(RuntimeException())

        val error = assertThrows<StatusRuntimeException> {
            grpcClient.searchKey(grpcRequest)
        }

        with(error) {
            assertEquals(Status.NOT_FOUND.code, status.code)
            assertEquals("Client not exists with this accountType", status.description)
        }
    }

    @Test
    fun `deve retornar chave encontrada no banco da aplicacao passando o keyValue (external services)`() {

        val ownerId = UUID.randomUUID().toString()
        val timeSavedOnApplication = LocalDateTime.now()
        val timeSavedOnBcb = LocalDateTime.now().plusSeconds(5L)

        val grpcRequest = SearchKeyRequest.newBuilder()
            .setKeyValue("jubileu@gmail.com")
            .build()

        every { pixClientRepository.findByKeyValue("jubileu@gmail.com") } answers {
            val pixClientKey = PixClientKey(ownerId, KeyType.EMAIL, "jubileu@gmail.com", CONTA_CORRENTE)
            pixClientKey.id = 42L
            pixClientKey.createdAt = timeSavedOnApplication

            Optional.of(pixClientKey)
        }

        BcbSearchKeyResponse(
            KeyType.EMAIL,
            "jubileu@gmail.com",
            BankAccount("60701190", "0001", "40028922", BcbAccountType.CACC),
            BcbOwner(PersonType.NATURAL_PERSON, "Jubileu da Silva", "24852806810"),
            timeSavedOnBcb
        )

        every { erpClient.getClientByIdAndAccountType(ownerId, CONTA_CORRENTE) } answers {
            ItauFoundClientAccountResponse(
                CONTA_CORRENTE,
                Bank("ITAÚ UNIBANCO S.A.", "60701190"),
                "0001",
                "40028922",
                ItauOwner("", "Jubileu da Silva", "24852806810")
            )
        }

        val grpcResponse = grpcClient.searchKey(grpcRequest)

        with(grpcResponse) {
            assertEquals(keyType, SearchKeyMessage.KeyType.EMAIL)
            assertEquals(keyValue, "jubileu@gmail.com")
            assertEquals(owner.name, "Jubileu da Silva")
            assertEquals(owner.cpf, "24852806810")
            assertEquals(bank.branch, "0001")
            assertEquals(bank.accountNumber, "40028922")
            assertEquals(bank.name, "ITAÚ UNIBANCO S.A.")
            assertEquals(bank.accountType, SearchKeyMessage.AccountType.CONTA_CORRENTE)
            assertEquals(createdAt, timeSavedOnApplication.toString())
        }
    }

    // END SEARCH TESTS #########################################################################

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
    class SearchKeyClient {

        @Singleton
        fun blockingStub(@GrpcChannel(GrpcServerChannel.NAME) channel: ManagedChannel) : SearchKeyServiceBlockingStub? {
            return SearchKeyServiceGrpc.newBlockingStub(channel)
        }
    }

}