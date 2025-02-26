package com.forsk.ondevice.domain

import com.google.gson.annotations.SerializedName
import kotlinx.serialization.Serializable

@Serializable
data class Room(
    @SerializedName("color") val color: String,
    @SerializedName("desc") val desc: String,
    @SerializedName("id") val id: String,
    @SerializedName("image_path") val image_path: List<ImagePath>,
    @SerializedName("image_position") val image_position: ImagePosition,
    @SerializedName("name") val name: String,
    @SerializedName("robot_path") val robot_path: List<RobotPath>,
    @SerializedName("robot_position") val robot_position: RobotPosition
)