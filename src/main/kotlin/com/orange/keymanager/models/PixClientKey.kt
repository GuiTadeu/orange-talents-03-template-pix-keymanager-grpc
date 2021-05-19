package com.orange.keymanager.models

import javax.persistence.*
import javax.persistence.GenerationType.IDENTITY
import javax.validation.constraints.NotBlank
import javax.validation.constraints.NotNull
import javax.validation.constraints.Size

@Entity
class PixClientKey(
    @field:Id @field:GeneratedValue(strategy = IDENTITY) val id: Long? = null,
    @field:NotNull val clientId: String,
    @field:NotNull @Enumerated(EnumType.STRING) val keyType: KeyType,
    @field:NotBlank @field:Size(max = 77) val keyValue: String,
    @field:NotNull @Enumerated(EnumType.STRING) val accountType: AccountType)