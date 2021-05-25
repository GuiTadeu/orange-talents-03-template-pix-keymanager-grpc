package com.orange.keymanager.models

import com.orange.keymanager.rest.BcbAccountType

enum class AccountType {

    CONTA_CORRENTE {
        override fun getBcbAccountType(): BcbAccountType {
            return BcbAccountType.CACC
        }
    },

    CONTA_POUPANCA {
        override fun getBcbAccountType(): BcbAccountType {
            return BcbAccountType.SVGS
        }
    };

    abstract fun getBcbAccountType(): BcbAccountType
}