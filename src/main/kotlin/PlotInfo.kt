package cn.status102

import kotlinx.serialization.Serializable

@Serializable
data class PlotInfo(
    val TerritoryId: Int,
    val WardId: Int,
    val HouseId: Int,
    val Size: Int,
    var Update: Long,
    var SaleState: Int = 0,
    var VoteCount: Int = 0,
    var WinnerIndex: Int = 0,
)
