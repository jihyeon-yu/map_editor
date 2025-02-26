package com.forsk.ondevice.domain

import com.google.gson.annotations.SerializedName
import kotlinx.serialization.Serializable

@Serializable
data class MapMapping(
    @SerializedName("assign_info") val assign_info: AssignInfo,
    @SerializedName("block_area") val block_area: List<BlockArea>,
    @SerializedName("block_wall") val block_wall: List<BlockWall>,
    @SerializedName("info") val info: Info,
    @SerializedName("room_list") val room_list: List<Room>,
    @SerializedName("uid") val uid: String,
    @SerializedName("user_angle") val user_angle: Int
)