package com.orange.keymanager.models

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

internal class GenerateKeyValueTest {

    @Test
    fun `deve retornar um erro caso o keyValue seja vazio e o keyType diferente de RANDOM`() {

        val keyValuesWithoutRandom = KeyType.values().filter { keyType -> keyType != KeyType.RANDOM }.toTypedArray()

        keyValuesWithoutRandom.forEach { keyType ->
            val error = assertThrows<Exception> {
                GenerateKeyValue.generate(keyType, "")
            }
            assertEquals("Key value is required", error.message)
        }
    }

    @Test
    fun `deve retornar um UUID se o tipo for RANDOM`() {
        val keyValue = GenerateKeyValue.generate(KeyType.RANDOM, "")
        assertTrue(keyValue.matches(Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")))
    }

    @Test
    fun `deve retornar o proprio keyValue se passar na validacao`() {

        val keyCpfValue = GenerateKeyValue.generate(KeyType.CPF, "308.972.740-40")
        val keyPhoneNumberValue = GenerateKeyValue.generate(KeyType.CPF, "+5511940028922")
        val keyEmailValue = GenerateKeyValue.generate(KeyType.EMAIL, "jubileu@gmail.com")

        assertEquals("308.972.740-40", keyCpfValue)
        assertEquals("+5511940028922", keyPhoneNumberValue)
        assertEquals("jubileu@gmail.com", keyEmailValue)
    }
}