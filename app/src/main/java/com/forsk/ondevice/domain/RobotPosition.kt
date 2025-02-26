package com.forsk.ondevice.domain

import com.google.gson.annotations.SerializedName
import kotlinx.serialization.Serializable

@Serializable
data class RobotPosition(
    @SerializedName("is_set_theta")  val is_set_theta: Boolean,
    @SerializedName("theta")  val theta: Double,
    @SerializedName("x")  val x: Double,
    @SerializedName("y")  val y: Double
)