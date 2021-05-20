package com.orange.keymanager.models


import org.hibernate.validator.internal.constraintvalidators.hv.EmailValidator
import org.hibernate.validator.internal.constraintvalidators.hv.br.CPFValidator

enum class KeyType {

    CPF {
        override fun isValid(value: String?): Boolean {
            if(value.isNullOrBlank()) return false

            return CPFValidator().run {
                initialize(null)
                isValid(value, null)
            }
        }
    },

    PHONE_NUMBER {
        override fun isValid(value: String?): Boolean {
            if(value.isNullOrBlank()) return false

            return value.matches(Regex("^\\+[1-9][0-9]\\d{1,14}\$"))
        }
    },

    EMAIL {
        override fun isValid(value: String?): Boolean {
            if(value.isNullOrBlank()) return false

            return EmailValidator().run {
                initialize(null)
                isValid(value, null)
            }
        }
    },

    RANDOM {
        override fun isValid(value: String?): Boolean {
            return value.isNullOrBlank()
        }
    };

    abstract fun isValid(value: String?): Boolean
}