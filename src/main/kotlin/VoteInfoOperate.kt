package cn.status102

interface VoteInfoOperate {
    suspend fun run(serverId: String): Map<String, PlotInfo>
}