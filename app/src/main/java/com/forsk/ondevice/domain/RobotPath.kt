package com.forsk.ondevice.domain

import com.google.gson.annotations.SerializedName
import kotlinx.serialization.Serializable

@Serializable
data class RobotPath(
    @SerializedName("x") val x: Double,
    @SerializedName("y") val y: Double
)