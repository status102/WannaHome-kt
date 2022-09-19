package cn.status102.data

import kotlinx.serialization.Serializable

@Serializable
data class PlotInfo(
    val ServerId: Int,
    val TerritoryId: Int,
    val WardId: Int,
    val HouseId: Int,
    val Size: Int,
    var Update: Long,
    var SaleState: Int = 0,
    var VoteCount: Int = 0,
    var WinnerIndex: Int = 0,
)
