package com.orange.keymanager.grpc

import com.orange.keymanager.CreateKeyMessage.AccountType.CONTA_CORRENTE
import com.orange.keymanager.CreateKeyMessage.CreateKeyRequest
import com.orange.keymanager.CreateKeyMessage.KeyType.*
import com.orange.keymanager.CreateKeyServiceGrpc
import com.orange.keymanager.CreateKeyServiceGrpc.CreateKeyServiceBlockingStub
import com.orange.keymanager.models.AccountType
import com.orange.keymanager.models.KeyType
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
import io.micronaut.test.extensions.kotlintest.MicronautKotlinTestExtension.getMock
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDateTime
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@MicronautTest
class CreateKeyGrpcServerTest(
    private val grpcClient: CreateKeyServiceBlockingStub,
    private val pixClientRepository: PixClientRepository): BehaviorSpec() {

    @Inject lateinit var erpClient: ItauErpRestClient
    @Inject lateinit var bcbClient: BcbPixRestClient

    private var clientId: String = UUID.randomUUID().toString()

    @BeforeEach
    fun setup() {
        val itauFoundClientAccountResponse = ItauFoundClientAccountResponse(
            agencia = "0001",
            numero = "291900",
            tipo = AccountType.CONTA_CORRENTE,
            instituicao = Bank(nome = "ITAÚ UNIBANCO S.A.", ispb = "60701190"),
            titular = ItauOwner(id = "c56dfef4-7901-44fb-84e2-a2cefb157890", nome = "Jubileu Irineu da Silva", cpf = "02467781054")
        )

        every { getMock(erpClient).getClientByIdAndAccountType(clientId, AccountType.CONTA_CORRENTE) }
            .returns(itauFoundClientAccountResponse)

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

        every { bcbClient.saveKey(any()) } answers {
            BcbSaveKeyResponse(
                KeyType.CPF, "308.972.740-40",
                BankAccount("60701190", "0001", "291900", BcbAccountType.CACC),
                BcbOwner(PersonType.LEGAL_PERSON, "Jubileu Irineu da Silva", "02467781054"),
                LocalDateTime.now()
            )
        }

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

        every { erpClient.getClientByIdAndAccountType(clientId, AccountType.CONTA_CORRENTE) }
            .throws(RuntimeException())

        val error = assertThrows<StatusRuntimeException> {
            grpcClient.createKey(grpcRequest)
        }

        with(error) {
            assertEquals(Status.NOT_FOUND.code, status.code)
            assertEquals("Client not exists with this accountType", status.description)
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

        every { bcbClient.saveKey(any()) } answers {
            BcbSaveKeyResponse(
                KeyType.CPF, "308.972.740-40",
                BankAccount("60701190", "0001", "291900", BcbAccountType.CACC),
                BcbOwner(PersonType.LEGAL_PERSON, "Jubileu Irineu da Silva", "02467781054"),
                LocalDateTime.now()
            )
        }

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

        every { bcbClient.saveKey(any()) } answers {
            BcbSaveKeyResponse(
                KeyType.PHONE_NUMBER, "+5511940028922",
                BankAccount("60701190", "0001", "291900", BcbAccountType.CACC),
                BcbOwner(PersonType.LEGAL_PERSON, "Jubileu Irineu da Silva", "02467781054"),
                LocalDateTime.now()
            )
        }

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

        every { bcbClient.saveKey(any()) } answers {
            BcbSaveKeyResponse(
                KeyType.EMAIL, "jubileu@gmail.com",
                BankAccount("60701190", "0001", "291900", BcbAccountType.CACC),
                BcbOwner(PersonType.LEGAL_PERSON, "Jubileu Irineu da Silva", "02467781054"),
                LocalDateTime.now()
            )
        }

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

        every { bcbClient.saveKey(any()) } answers {
            BcbSaveKeyResponse(
                KeyType.RANDOM, UUID.randomUUID().toString(),
                BankAccount("60701190", "0001", "291900", BcbAccountType.CACC),
                BcbOwner(PersonType.LEGAL_PERSON, "Jubileu Irineu da Silva", "02467781054"),
                LocalDateTime.now()
            )
        }

        val grpcResponse = grpcClient.createKey(grpcRequest)
        val savedPixKey = pixClientRepository.findById(grpcResponse.keyId).get()

        assertEquals(savedPixKey.clientId, grpcRequest.clientId)
        assertTrue(savedPixKey.keyValue.matches(Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")))
        assertEquals(savedPixKey.keyType, KeyType.valueOf(grpcRequest.keyType.toString()))
        assertEquals(savedPixKey.accountType, AccountType.valueOf(grpcRequest.accountType.toString()))
    }

    @Test
    internal fun `nao deve criar chave RANDOM se o keyValue for preenchido`() {
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

    @Test
    internal fun `deve abortar a operacao caso o BCB nao salve a chave`() {
        val grpcRequest = CreateKeyRequest.newBuilder()
            .setClientId(clientId)
            .setKeyType(EMAIL)
            .setKeyValue("jubileu@gmail.com")
            .setAccountType(CONTA_CORRENTE)
            .build()

        every { bcbClient.saveKey(any()) }.throws(RuntimeException())

        val error = assertThrows<StatusRuntimeException> {
            grpcClient.createKey(grpcRequest)
        }

        with(error) {
            assertEquals(Status.ABORTED.code, status.code)
            assertEquals("Error trying to save on bcb", status.description)
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

    @Factory
    class CreateKeyClient {

        @Singleton
        fun blockingStub(@GrpcChannel(GrpcServerChannel.NAME) channel: ManagedChannel) : CreateKeyServiceBlockingStub?{
            return CreateKeyServiceGrpc.newBlockingStub(channel)
        }
    }
}