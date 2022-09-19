package cn.status102.data

import cn.status102.WannaHomeKt
import cn.status102.WannaHomeKt.reload
import cn.status102.client
import cn.status102.data.VoteInfoHouseHelper.Companion.Logger.CallTimes
import cn.status102.data.VoteInfoHouseHelper.Companion.Logger.FailTimes
import cn.status102.data.VoteInfoHouseHelper.Companion.Logger.TimeMillis
import cn.status102.territoryMap
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import net.mamoe.mirai.console.data.AutoSavePluginData
import net.mamoe.mirai.console.data.value
import net.mamoe.mirai.utils.error
import okhttp3.CacheControl
import okhttp3.Call
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.*
import java.util.concurrent.TimeUnit

class VoteInfoHouseHelper : VoteInfoOperate {

	companion object {
		init {
			Logger.reload()
		}

		object Logger : AutoSavePluginData("VoteInfo_HouseHelper_Log") {
			var CallTimes by value(0)
			var FailTimes by value(0)
			var TimeMillis by value(0L)
		}

		var LastModified = ""
		private const val HomeUrl = "https://househelper.ffxiv.cyou/api/sales?server="
		private fun newRequest(serverId: Int): Request {
			return Request.Builder().url(HomeUrl + serverId).get()
				.cacheControl(CacheControl.Builder().maxAge(1, TimeUnit.MINUTES).build()).run {
					if (LastModified.isNotEmpty())
						this.addHeader("If-Modified-Since", LastModified)
					this
				}.build()
		}

		private fun newCall(serverId: Int): Call {
			return client.newCall(newRequest(serverId))
		}

		fun call(serverId: Int, reCallTimes: Int = 0, reCallTimesLimit: Int = 3): Response {
			try {
				return newCall(serverId).execute().apply {
					if (this.networkResponse != null)
						CallTimes++
				}
			} catch (e: IOException) {
				if (reCallTimes < reCallTimesLimit) {
					CallTimes++
					FailTimes++
					WannaHomeKt.logger.error { "HouseHelper获取错误：$e\n${e.stackTraceToString()}" }
					return call(serverId, reCallTimes + 1)
				}
				throw e
			} catch (e: Exception) {
				CallTimes++
				FailTimes++
				throw e
			}
		}
	}

	private val jsonDecoder: Json by lazy {
		Json { ignoreUnknownKeys = true }
	}

	override suspend fun run(serverId: Int, lastTurnStart: Long, thisTurnStart: Long): Map<String, PlotInfo> {
		//WannaHomeKt.logger.info { "开始获取HouseHelper：${SimpleDateFormat("YYYY-MM-dd HH:mm:ss.SSS").format(Calendar.getInstance().time.time)}" }

		val voteInfoMap = mutableMapOf<String, PlotInfo>()
		try {
			val startTimeStamp = Calendar.getInstance().timeInMillis
			call(serverId).use { response ->
				val str = response.body?.string() ?: ""
				if (response.networkResponse != null) {
					TimeMillis += Calendar.getInstance().timeInMillis - startTimeStamp
					if (response.headers["last-modified"] != null)
						LastModified = response.headers["last-modified"]!!
				}
				if (response.isSuccessful && str.isNotEmpty()) {
					val voteInfoList = jsonDecoder.decodeFromString<List<VoteInfo>>(str)

					voteInfoList.filter { voteInfo ->
						voteInfo.LastSeen >= thisTurnStart || (voteInfo.UpdateTime >= lastTurnStart && voteInfo.State in 2..3)
					}.forEach {
						it.TerritoryId = territoryMap.keys.toList()[it.TerritoryId]//将房区序号改为房区ID
						voteInfoMap.run {
							if (containsKey("${it.TerritoryId}-${it.WardId}-${it.houseNum - 1}")) {
								if (this["$serverId-${it.TerritoryId}-${it.WardId}-${it.houseNum - 1}"]!!.Update < it.UpdateTime || this["${it.TerritoryId}-${it.WardId}-${it.houseNum - 1}"]!!.SaleState == 0) {
									this["$serverId-${it.TerritoryId}-${it.WardId}-${it.houseNum - 1}"]!!.VoteCount = it.VoteCount
									this["$serverId-${it.TerritoryId}-${it.WardId}-${it.houseNum - 1}"]!!.WinnerIndex = it.WinnerIndex
									this["$serverId-${it.TerritoryId}-${it.WardId}-${it.houseNum - 1}"]!!.SaleState = it.State
									this["$serverId-${it.TerritoryId}-${it.WardId}-${it.houseNum - 1}"]!!.Update = it.UpdateTime
								}
							} else {
								this["$serverId-${it.TerritoryId}-${it.WardId}-${it.houseNum - 1}"] = PlotInfo(serverId, it.TerritoryId, it.WardId, it.houseNum - 1, it.Size, it.UpdateTime, it.State, it.VoteCount, it.WinnerIndex)
							}
						}
					}
				} else {
					WannaHomeKt.logger.error { "HouseHelper获取错误：Code:${response.code}\nBody:${response}" }
				}
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