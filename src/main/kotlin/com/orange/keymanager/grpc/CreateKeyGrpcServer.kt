package com.orange.keymanager.grpc

import com.orange.keymanager.CreateKeyRequest
import com.orange.keymanager.CreateKeyResponse
import com.orange.keymanager.CreateKeyServiceGrpc.CreateKeyServiceImplBase
import com.orange.keymanager.models.*
import com.orange.keymanager.rest.*
import com.orange.keymanager.rest.PersonType.*
import io.grpc.Status
import io.grpc.Status.*
import io.grpc.stub.StreamObserver
import javax.inject.Singleton

@Singleton
class CreateKeyGrpcServer(
    private val itauErpRestClient: ItauErpRestClient,
    private val bcbPixRestClient: BcbPixRestClient,
    private val pixClientRepository: PixClientRepository) : CreateKeyServiceImplBase() {

    override fun createKey(request: CreateKeyRequest, responseObserver: StreamObserver<CreateKeyResponse>?) {

        val convertedKeyType = request.getConvertedKeyType()
        val convertedAccountType = request.getConvertedAccountType()
        var itauClient: ItauFoundClientAccountResponse? = null

        if (!convertedKeyType.isValid(request.keyValue)) {
            throwError(INVALID_ARGUMENT, "Invalid key value", responseObserver)
        }

        if (convertedKeyType != KeyType.RANDOM) {
            if (pixClientRepository.findByKeyValue(request.keyValue).isPresent) {
                throwError(ALREADY_EXISTS, "Pix key already registered", responseObserver)
            }
        }

        try {
            itauClient = itauErpRestClient.getClientByIdAndAccountType(request.clientId, convertedAccountType)
        } catch (exception: Exception) {
            throwError(NOT_FOUND, "Client not exists with this accountType", responseObserver)
        }

        val bcbSaveKeyRequest = BcbSaveKeyRequest(
            keyType = convertedKeyType,
            key = request.keyValue,
            bankAccount = BankAccount(
                participant = "60701190",
                branch = itauClient!!.agencia,
                accountNumber = itauClient.numero,
                accountType = itauClient.getBcbAccountType()
            ),
            owner = BcbOwner(
                type = NATURAL_PERSON,
                name = itauClient.getTitularNome(),
                taxIdNumber = itauClient.getTitularCpf()
            )
        )

        val pixClientKey = PixClientKey(
            clientId = request.clientId,
            keyValue = request.keyValue,
            keyType = convertedKeyType,
            accountType = convertedAccountType
        )

        try {
            val bcbClient = bcbPixRestClient.saveKey(bcbSaveKeyRequest)

            if (convertedKeyType == KeyType.RANDOM)
                pixClientKey.keyValue = bcbClient.key

        } catch (exception: Exception) {
            throwError(ABORTED, "Error trying to save on bcb", responseObserver)
        }

        pixClientRepository.save(pixClientKey)

        val response = CreateKeyResponse.newBuilder()
            .setKeyId(pixClientKey.id!!)
            .setKeyValue(pixClientKey.keyValue)
            .setKeyType(request.keyType)
            .setAccountType(request.accountType)
            .build()

        responseObserver!!.onNext(response)
        responseObserver.onCompleted()
    }

    private fun CreateKeyRequest.getConvertedKeyType(): KeyType {
        return KeyType.valueOf(this.keyType.toString())
    }

    private fun CreateKeyRequest.getConvertedAccountType(): AccountType {
        return AccountType.valueOf(this.accountType.toString())
    }

    private fun throwError(status: Status, description: String, responseObserver: StreamObserver<CreateKeyResponse>?) {
        responseObserver?.onError(
            status
                .withDescription(description)
                .asRuntimeException()
        )
        return
    }
}