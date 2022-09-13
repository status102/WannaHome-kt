package cn.status102

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import net.mamoe.mirai.utils.error
import okhttp3.Call
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.*
import kotlin.math.floor

class VoteInfoHouseHelper : VoteInfoOperate {
	companion object {
		private const val HomeUrl = "https://househelper.ffxiv.cyou/api/sales?server="
		private fun newRequest(serverId: String): Request {
			return Request.Builder().url(HomeUrl + serverId).get().build()
		}

		private fun newCall(serverId: String): Call {
			return client.newCall(newRequest(serverId))
		}

		fun call(serverId: String, reCallTimes: Int = 0, reCallTimesLimit: Int = 3): Response {
			try {
				return newCall(serverId).execute()
			} catch (e: IOException) {
				if (reCallTimes < reCallTimesLimit) {
					WannaHomeKt.logger.error { "HouseHelper 获取错误：$e\n${e.stackTraceToString()}" }
					return call(serverId, reCallTimes + 1)
				}
				throw e
			} catch (e: Exception) {
				throw e
			}
		}
	}

	private val jsonDecoder: Json by lazy {
		Json { ignoreUnknownKeys = true }
	}

	override suspend fun run(serverId: String): Map<String, PlotInfo> {
		//WannaHomeKt.logger.info { "开始获取HouseHelper：${SimpleDateFormat("YYYY-MM-dd HH:mm:ss.SSS").format(Calendar.getInstance().time.time)}" }
		val now = Calendar.getInstance().time.time
		val start = Calendar.getInstance().apply { time = strTimeToDate("2022-08-08 23:00:00") }
		val diff = floor((now - start.time.time) / 1000 / 60.0 / 60 / 24).toInt()
		val turn = diff / 9 + 1
		val lastTurnStart = (start.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, (turn - 2) * 9) }
		val thisTurnStart = (start.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, (turn - 1) * 9) }

		val voteInfoMap = mutableMapOf<String, PlotInfo>()
		try {
			val resC = call(serverId)
			val strC = resC.body?.string() ?: ""
			if (resC.isSuccessful && strC.isNotEmpty()) {
				val voteInfoList = jsonDecoder.decodeFromString<List<VoteInfo>>(strC)

				voteInfoList.filter { it.LastSeen >= thisTurnStart.time.time / 1000 || (it.UpdateTime >= lastTurnStart.time.time / 1000 && it.State == 3) }
					.forEach {
						it.TerritoryId = territoryMap.keys.toList()[it.TerritoryId]//将房区序号改为房区ID
						voteInfoMap.run {
							if (containsKey("${it.TerritoryId}-${it.WardId}-${it.houseNum - 1}")) {
								if (this["${it.TerritoryId}-${it.WardId}-${it.houseNum - 1}"]!!.Update < it.UpdateTime || this["${it.TerritoryId}-${it.WardId}-${it.houseNum - 1}"]!!.SaleState == 0) {
									this["${it.TerritoryId}-${it.WardId}-${it.houseNum - 1}"]!!.VoteCount = it.VoteCount
									this["${it.TerritoryId}-${it.WardId}-${it.houseNum - 1}"]!!.WinnerIndex = it.WinnerIndex
									this["${it.TerritoryId}-${it.WardId}-${it.houseNum - 1}"]!!.SaleState = it.State
									this["${it.TerritoryId}-${it.WardId}-${it.houseNum - 1}"]!!.Update = it.UpdateTime
								}
							} else {
								this["${it.TerritoryId}-${it.WardId}-${it.houseNum - 1}"] = PlotInfo(it.TerritoryId, it.WardId, it.houseNum - 1, it.Size, it.UpdateTime, it.State, it.VoteCount, it.WinnerIndex)
							}
						}
					}
			} else {
				WannaHomeKt.logger.error { "HouseHelper获取错误：Code:${resC.code}\nBody:${resC}" }
			}
		} catch (e: Exception) {
			WannaHomeKt.logger.error { "HouseHelper获取错误：$e\n${e.stackTraceToString()}" }
		}
		//WannaHomeKt.logger.info { "结束获取HouseHelper：${SimpleDateFormat("YYYY-MM-dd HH:mm:ss.SSS").format(Calendar.getInstance().time.time)}" }
		return voteInfoMap
	}

	@Serializable
	data class VoteInfo(
		val Server: Int,
		@SerialName("Area")
		var TerritoryId: Int,
		@SerialName("Slot")
		val WardId: Int,
		@SerialName("ID")
		val houseNum: Int,
		val Price: Int,
		val Size: Int,
		val FirstSeen: Long,
		val LastSeen: Long,
		val State: Int,
		@SerialName("Participate")
		val VoteCount: Int,
		@SerialName("Winner")
		val WinnerIndex: Int,
		val EndTime: Int,
		val UpdateTime: Long,
		val PurchaseType: Int,
		val RegionType: Int,
	)

}