package com.forsk.ondevice.domain

import com.google.gson.annotations.SerializedName
import kotlinx.serialization.Serializable

@Serializable
data class ImagePath(
    @SerializedName("x") val x: Int,
    @SerializedName("y") val y: Int
)