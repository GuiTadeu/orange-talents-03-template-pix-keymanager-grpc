package com.orange.keymanager.models

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

internal class KeyTypeTest {

    @Nested
    inner class Cpf {

        @Test
        fun `deve ser valido quando chave CPF estiver no formato correto`() {
            with(KeyType.CPF) {
                assertTrue(isValid("308.972.740-40"))
                assertTrue(isValid("364.766.130-90"))
                assertTrue(isValid("21507511000"))
                assertTrue(isValid("04758705062"))
            }
        }

        @Test
        fun `nao deve ser valido quando chave CPF estiver incorreta`() {
            with(KeyType.CPF) {
                assertFalse(isValid(""))
                assertFalse(isValid("123456789101"))
                assertFalse(isValid("04758715162"))
                assertFalse(isValid("email@gmail.com"))
                assertFalse(isValid("1234567891012345"))
            }
        }
    }

    @Nested
    inner class PhoneNumber {

        @Test
        fun `deve ser valido quando chave PHONE_NUMBER estiver no formato correto`() {
            with(KeyType.PHONE_NUMBER) {
                assertTrue(isValid("+5511970707070"))
            }
        }

        @Test
        fun `nao deve ser valido quando chave PHONE_NUMBER estiver incorreta`() {
            with(KeyType.PHONE_NUMBER) {
                assertFalse(isValid(""))
                assertFalse(isValid("70707070"))
                assertFalse(isValid("1234567891012345"))
            }
        }
    }

    @Nested
    inner class Email {

        @Test
        fun `deve ser valido quando chave EMAIL estiver no formato correto`() {
            with(KeyType.EMAIL) {
                assertTrue(isValid("jubileu@gmail.com"))
                assertTrue(isValid("jubileu@outlook.com"))
                assertTrue(isValid("jubileu.silva@protonmail.com"))
                assertTrue(isValid("jubileu.silva@bol.com.br"))
                assertTrue(isValid("jubileu2010@bol.com.br"))
            }
        }

        @Test
        fun `nao deve ser valido quando chave EMAIL estiver incorreta`() {
            with(KeyType.EMAIL) {
                assertFalse(isValid(""))
                assertFalse(isValid("KKKKKKKKKKKKKKKKKKKKKK_SouHacker"))
                assertFalse(isValid("jubileu@@gmail.com"))
                assertFalse(isValid("@gmail.com"))
            }
        }
    }

    @Nested
    inner class Random {

        @Test
        fun `deve ser valido quando chave RANDOM estiver no formato correto`() {
            with(KeyType.RANDOM) {
                assertTrue(isValid(""))
            }
        }

        @Test
        fun `nao deve ser valido quando chave RANDOM estiver incorreta`() {
            with(KeyType.RANDOM) {
                assertFalse(isValid("jubileu@gmail.com"))
                assertFalse(isValid("+5511970707070"))
                assertFalse(isValid("308.972.740-40"))
            }
        }

    }

}