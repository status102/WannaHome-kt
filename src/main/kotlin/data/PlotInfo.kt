package cn.status102.data

import kotlinx.serialization.Serializable

@Serializable
data class PlotInfo(
    /**
     * 服务器id
     */
    val ServerId: Int,
    /**
     * 房区id：339, 340, ...
     */
    val TerritoryId: Int,
    /**
     * 房区号：0, 1, ...
     */
    val WardId: Int,
    /**
     * 房屋号：0, 1, ...
     */
    val HouseId: Int,
    /**
     * 地皮型号：0-s，1-m，2-L
     */
    val Size: Int,
    /**
     * 数据更新时间，秒
     */
    var Update: Long,
    /**
     * 出售状态：0-未知，1-可摇号，2-结果已出，3-准备中
     */
    var SaleState: Int = 0,
    var VoteCount: Int = 0,
    var WinnerIndex: Int = 0,
)
