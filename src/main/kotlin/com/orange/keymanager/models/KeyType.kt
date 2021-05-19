package com.orange.keymanager.models

enum class KeyType {

    CPF {
        override fun isValid(value: String?): Boolean {
            value ?: return false
            return value.matches(Regex("^[0-9]{11}\$"))
        }
    },

    PHONE_NUMBER {
        override fun isValid(value: String?): Boolean {
            value ?: return false
            return value.matches(Regex("^\\+[1-9][0-9]\\d{1,14}\$"))
        }
    },

    EMAIL {
        override fun isValid(value: String?): Boolean {
            value ?: return false
            return value.matches(Regex("^[A-Za-z0-9+_.-]+@(.+)\$"))
        }
    },

    RANDOM {
        override fun isValid(value: String?): Boolean {
            return value.isNullOrBlank()
        }
    };

    abstract fun isValid(value: String?): Boolean
}