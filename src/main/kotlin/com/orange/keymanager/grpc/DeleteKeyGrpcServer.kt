package com.orange.keymanager.grpc

import com.orange.keymanager.DeleteKeyRequest
import com.orange.keymanager.DeleteKeyResponse
import com.orange.keymanager.DeleteKeyServiceGrpc.DeleteKeyServiceImplBase
import com.orange.keymanager.models.PixClientRepository
import com.orange.keymanager.rest.ItauErpRestClient
import io.grpc.Status
import io.grpc.Status.*
import io.grpc.stub.StreamObserver
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeleteKeyGrpcServer(
    private val itauErpRestClient: ItauErpRestClient,
    @Inject val pixClientRepository: PixClientRepository): DeleteKeyServiceImplBase() {

    override fun deleteKey(request: DeleteKeyRequest, responseObserver: StreamObserver<DeleteKeyResponse>) {

        if(!request.isValid()) {
            throwError(INVALID_ARGUMENT, "Invalid arguments", responseObserver)
        }

        try {
            itauErpRestClient.findByClientId(request.clientId)
        } catch(exception: Exception) {
            throwError(NOT_FOUND, "Client ID not exists", responseObserver)
            return
        }

        if(pixClientRepository.findById(request.keyId).isEmpty) {
            throwError(NOT_FOUND, "Key ID not exists", responseObserver)
            return
        }

        if(pixClientRepository.findByIdAndClientId(request.keyId, request.clientId).isEmpty) {
            throwError(PERMISSION_DENIED, "Key does not belong to clientId", responseObserver)
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