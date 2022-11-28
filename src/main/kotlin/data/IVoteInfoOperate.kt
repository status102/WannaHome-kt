package cn.status102.data

interface IVoteInfoOperate {
	/**
	 * 数据来源站名称
	 */
	val sourceName : String

	/**
	 * @param lastTurnStart 上一轮摇号开始，秒为单位
	 * @param thisTurnStart 当前轮摇号开始，秒为单位
	 */
	suspend fun run(serverId: Int, lastTurnStart: Long, thisTurnStart: Long): Map<String, PlotInfo>
}