package com.orange.keymanager.grpc

import com.orange.keymanager.DeleteKeyRequest
import com.orange.keymanager.DeleteKeyResponse
import com.orange.keymanager.DeleteKeyServiceGrpc.DeleteKeyServiceImplBase
import com.orange.keymanager.models.PixClientRepository
import com.orange.keymanager.rest.BcbDeleteKeyRequest
import com.orange.keymanager.rest.BcbPixRestClient
import com.orange.keymanager.rest.ItauErpRestClient
import io.grpc.Status
import io.grpc.Status.*
import io.grpc.stub.StreamObserver
import java.lang.Exception
import javax.inject.Singleton

@Singleton
class DeleteKeyGrpcServer(
    private val itauErpRestClient: ItauErpRestClient,
    private val bcbPixRestClient: BcbPixRestClient,
    private val pixClientRepository: PixClientRepository): DeleteKeyServiceImplBase() {

    override fun deleteKey(request: DeleteKeyRequest, responseObserver: StreamObserver<DeleteKeyResponse>) {

        if(!request.isValid()) {
            throwError(INVALID_ARGUMENT, "Invalid arguments", responseObserver)
        }

        val foundPixClient = pixClientRepository.findByIdAndClientId(request.keyId, request.clientId)
        if(foundPixClient.isEmpty) {
            throwError(PERMISSION_DENIED, "Key does not belong to clientId", responseObserver)
            return
        }

        val pixClient = foundPixClient.get()

        try {
            bcbPixRestClient.existsKeyValue(pixClient.keyValue)
        } catch (exception: Exception) {
            throwError(ABORTED, "Key value does not exists in BCB", responseObserver)
            return
        }

        try {
            bcbPixRestClient.deleteKeyValue(pixClient.keyValue, BcbDeleteKeyRequest(pixClient.keyValue, "60701190"))
        } catch (exception: Exception) {
            throwError(ABORTED, "It was not possible to delete the key in the bcb, try again...", responseObserver)
            return
        }

        pixClientRepository.deleteById(request.keyId)

        val response = DeleteKeyResponse.newBuilder()
            .setDeletedKeyId(request.keyId)
            .build()

        responseObserver.onNext(response)
        responseObserver.onCompleted()
    }

    private fun DeleteKeyRequest.isValid(): Boolean {
        return keyId != 0L && !clientId.isNullOrBlank()
    }

    private fun throwError(status: Status, description: String, responseObserver: StreamObserver<DeleteKeyResponse>?) {
        responseObserver?.onError(
            status
                .withDescription(description)
                .asRuntimeException()
        )
    }

}