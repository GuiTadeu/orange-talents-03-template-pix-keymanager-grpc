package com.orange.keymanager.grpc

import com.orange.keymanager.SearchKeyMessage.*
import com.orange.keymanager.SearchKeyMessage.ClientKeysResponse.Key
import com.orange.keymanager.SearchKeyServiceGrpc.SearchKeyServiceImplBase
import com.orange.keymanager.models.PixClientRepository
import com.orange.keymanager.rest.BcbPixRestClient
import com.orange.keymanager.rest.BcbSearchKeyResponse
import com.orange.keymanager.rest.ItauErpRestClient
import com.orange.keymanager.rest.ItauFoundClientAccountResponse
import io.grpc.Status
import io.grpc.stub.StreamObserver
import java.lang.Exception
import javax.inject.Singleton

@Singleton
class SearchKeyGrpcServer(
    private val itauErpRestClient: ItauErpRestClient,
    private val pixClientRepository: PixClientRepository,
    private val bcbPixRestClient: BcbPixRestClient): SearchKeyServiceImplBase() {

    override fun clientKeys(request: ClientKeysRequest, responseObserver: StreamObserver<ClientKeysResponse>) {

        try {
            itauErpRestClient.findByClientId(request.clientId)
        } catch (exception: Exception) {
            throwClientsKeysError(Status.NOT_FOUND, "Client not exists with this accountType", responseObserver)
        }

        val keys = pixClientRepository.findByClientId(request.clientId)

        val keysResponse = keys.map { key ->
            Key.newBuilder()
                .setKeyId(key.id!!)
                .setKeyValue(key.keyValue)
                .setKeyType(KeyType.valueOf(key.keyType.toString()))
                .setAccountType(AccountType.valueOf(key.accountType.toString()))
                .setCreatedAt(key.createdAt.toString())
                .build()
        }.toList()

        val response = ClientKeysResponse.newBuilder()
            .setClientId(request.clientId)
            .addAllKeys(keysResponse)
            .build()

        responseObserver.onNext(response)
        responseObserver.onCompleted()
    }

    override fun internalSearchKey(request: InternalSearchKeyRequest, responseObserver: StreamObserver<InternalSearchKeyResponse>) {
        val keyId = request.keyId

        val possiblePixClient = pixClientRepository.findById(keyId)

        if (possiblePixClient.isEmpty) {
            throwInternalError(Status.NOT_FOUND, "Key does not exists", responseObserver)
            return
        }

        val foundPixClient = possiblePixClient.get()

        if (foundPixClient.clientId != request.clientId) {
            throwInternalError(Status.PERMISSION_DENIED, "Key does not belong to clientId", responseObserver)
            return
        }

        lateinit var bcbClient: BcbSearchKeyResponse

        // Só pode retornar chave se estiver no BCB
        try {
            bcbClient = bcbPixRestClient.searchKeyValue(foundPixClient.keyValue)
        } catch (exception: Exception) {
            throwInternalError(Status.ABORTED, "Key value does not exists in BCB", responseObserver)
            return
        }

        val convertedKeyType = KeyType.valueOf(foundPixClient.keyType.toString())

        val itauClientOwner = PixOwner.newBuilder()
            .setCpf(bcbClient.owner.taxIdNumber)
            .setName(bcbClient.owner.name)
            .build()

        val itauClientBank = PixBank.newBuilder()
            .setBranch(bcbClient.bankAccount.branch)
            .setAccountNumber(bcbClient.bankAccount.accountNumber)
            .setAccountType(AccountType.valueOf(foundPixClient.accountType.toString()))
            .setName("ITAÚ UNIBANCO S.A.")

        val response = InternalSearchKeyResponse.newBuilder()
            .setKeyId(foundPixClient.id!!)
            .setClientId(foundPixClient.clientId)
            .setKeyType(convertedKeyType)
            .setKeyValue(foundPixClient.keyValue)
            .setOwner(itauClientOwner)
            .setBank(itauClientBank)
            .setCreatedAt(foundPixClient.createdAt.toString())
            .build()

        responseObserver.onNext(response)
        responseObserver.onCompleted()
    }

    override fun searchKey(request: SearchKeyRequest, responseObserver: StreamObserver<SearchKeyResponse>) {

        if (!request.isValid()) {
            throwError(Status.INVALID_ARGUMENT, "Invalid arguments", responseObserver)
        }

        val keyValue = request.keyValue
        val possiblePixClient = pixClientRepository.findByKeyValue(keyValue)

        // Se não estiver disponível no banco da aplicação, buscar no BCB
        if (possiblePixClient.isEmpty) {

            lateinit var pixKeyBcb: BcbSearchKeyResponse
            try {
                pixKeyBcb = bcbPixRestClient.searchKeyValue(keyValue)
            } catch (exception: Exception) {
                throwError(Status.NOT_FOUND, "Key does not exists on bcb", responseObserver)
            }

            val bcbOwner = PixOwner.newBuilder()
                .setCpf(pixKeyBcb.owner.taxIdNumber)
                .setName(pixKeyBcb.owner.name)
                .build()

            val convertedAccountType = AccountType.valueOf(
                pixKeyBcb.bankAccount.accountType.getApplicationAccountType().toString()
            )

            val bcbOwnerBank = PixBank.newBuilder()
                .setBranch(pixKeyBcb.bankAccount.branch)
                .setAccountNumber(pixKeyBcb.bankAccount.accountNumber)
                .setAccountType(convertedAccountType)
                .setName("ITAÚ UNIBANCO S.A.")

            val response = SearchKeyResponse.newBuilder()
                .setKeyType(KeyType.valueOf(pixKeyBcb.keyType.toString()))
                .setKeyValue(pixKeyBcb.key)
                .setOwner(bcbOwner)
                .setBank(bcbOwnerBank)
                .setCreatedAt(pixKeyBcb.createdAt.toString())
                .build()

            responseObserver.onNext(response)
            responseObserver.onCompleted()

            return
        }

        val foundPixClient = possiblePixClient.get()

        lateinit var itauClient: ItauFoundClientAccountResponse
        try {
            itauClient = itauErpRestClient.getClientByIdAndAccountType(foundPixClient.clientId, foundPixClient.accountType)
        } catch (exception: Exception) {
            throwError(Status.NOT_FOUND, "Client not exists with this accountType", responseObserver)
        }

        val itauClientOwner = PixOwner.newBuilder()
            .setCpf(itauClient.getTitularCpf())
            .setName(itauClient.getTitularNome())
            .build()

        val itauClientBank = PixBank.newBuilder()
            .setBranch(itauClient.agencia)
            .setAccountNumber(itauClient.numero)
            .setAccountType(AccountType.valueOf(itauClient.tipo.toString()))
            .setName(itauClient.instituicao.nome)

        val convertedKeyType = KeyType.valueOf(foundPixClient.keyType.toString())

        val response = SearchKeyResponse.newBuilder()
            .setKeyType(convertedKeyType)
            .setKeyValue(foundPixClient.keyValue)
            .setOwner(itauClientOwner)
            .setBank(itauClientBank)
            .setCreatedAt(foundPixClient.createdAt.toString())
            .build()

        responseObserver.onNext(response)
        responseObserver.onCompleted()
    }

    private fun SearchKeyRequest.isValid(): Boolean {
        return !keyValue.isNullOrBlank() && keyValue.length <= 77
    }

    private fun throwError(status: Status, description: String, responseObserver: StreamObserver<SearchKeyResponse>) {
        responseObserver.onError(
            status
                .withDescription(description)
                .asRuntimeException()
        )
    }

    private fun throwInternalError(status: Status, description: String, responseObserver: StreamObserver<InternalSearchKeyResponse>) {
        responseObserver.onError(
            status
                .withDescription(description)
                .asRuntimeException()
        )
    }

    private fun throwClientsKeysError(status: Status, description: String, responseObserver: StreamObserver<ClientKeysResponse>) {
        responseObserver.onError(
            status
                .withDescription(description)
                .asRuntimeException()
        )
    }
}