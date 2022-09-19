package cn.status102.data

interface VoteInfoOperate {
	suspend fun run(serverId: Int, lastTurnStart: Long, thisTurnStart: Long): Map<String, PlotInfo>
}