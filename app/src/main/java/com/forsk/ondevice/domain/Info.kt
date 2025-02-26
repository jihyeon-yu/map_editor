package com.forsk.ondevice.domain

import com.google.gson.annotations.SerializedName
import kotlinx.serialization.Serializable

@Serializable
data class Info(
    @SerializedName("modified") val modified: String,
    @SerializedName("version") val version: String
)