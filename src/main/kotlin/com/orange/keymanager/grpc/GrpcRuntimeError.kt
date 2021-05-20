package com.orange.keymanager.grpc

import com.orange.keymanager.CreateKeyResponse
import io.grpc.Status
import io.grpc.stub.StreamObserver

class GrpcRuntimeError {

    companion object {

        fun throwError(status: Status, description: String, responseObserver: StreamObserver<CreateKeyResponse>?) {
            responseObserver?.onError(
                status
                    .withDescription(description)
                    .asRuntimeException()
            )
            return
        }
    }
}