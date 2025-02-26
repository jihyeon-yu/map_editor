package com.forsk.ondevice.domain

import com.google.gson.annotations.SerializedName
import kotlinx.serialization.Serializable

@Serializable
data class BlockArea(
    @SerializedName("id") val id: String,
    @SerializedName("image_path") val image_path: List<ImagePath>,
    @SerializedName("robot_path") val robot_path: List<RobotPath>
)