package cn.status102.data

import kotlinx.serialization.json.Json

interface IVoteInfoOperate {
	/**
	 * 数据来源站名称
	 */
	val sourceName : String

	/**
	 * @param lastTurnStart 上一轮摇号开始，秒为单位
	 * @param thisTurnStart 当前轮摇号开始，秒为单位
	 */
	suspend fun run(serverId: Int): Map<String, PlotInfo>

}

abstract class VoteInfoOperate :IVoteInfoOperate{

	val jsonDecoder: Json by lazy { Json { ignoreUnknownKeys = true } }
}