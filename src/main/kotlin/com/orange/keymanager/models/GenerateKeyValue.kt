package com.orange.keymanager.models

import com.orange.keymanager.models.KeyType.RANDOM
import java.util.*

class GenerateKeyValue {

    companion object {
        fun generate(keyType: KeyType, keyValue: String): String {
            if (keyType != RANDOM && keyValue.isBlank())
                throw Exception("Key value is required")

            if (keyType == RANDOM)
                return UUID.randomUUID().toString()

            return keyValue
        }
    }
}