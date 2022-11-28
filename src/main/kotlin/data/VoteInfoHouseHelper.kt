package cn.status102.data

import cn.status102.*
import cn.status102.WannaHomeKt.reload
import cn.status102.data.HouseHelpeLogger.CallTimes
import cn.status102.data.HouseHelpeLogger.FailTimes
import cn.status102.data.HouseHelpeLogger.MaxMillis
import cn.status102.data.HouseHelpeLogger.MinMillis
import cn.status102.data.HouseHelpeLogger.TimeMillis
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import net.mamoe.mirai.console.data.AutoSavePluginData
import net.mamoe.mirai.console.data.value
import net.mamoe.mirai.utils.warning
import okhttp3.CacheControl
import okhttp3.Call
import okhttp3.Request
import okhttp3.Response
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.*
import java.util.concurrent.TimeUnit

object HouseHelpeLogger : AutoSavePluginData("VoteInfo_HouseHelper_Log") {
	var CallTimes by value(0)
	var FailTimes by value(0)
	var TimeMillis by value(0L)
	var MaxMillis by value(Long.MIN_VALUE)
	var MinMillis by value(Long.MAX_VALUE)
}

class VoteInfoHouseHelper : IVoteInfoOperate {

	companion object {
		init {
			Logger.reload()
		}

		val Logger get() = HouseHelpeLogger
		var LastModified = ""
		private const val HomeUrl = "https://househelper.ffxiv.cyou/api/sales?server="
		private fun newRequest(serverId: Int): Request {
			return Request.Builder().url(HomeUrl + serverId).get()
				.cacheControl(CacheControl.Builder().maxAge(1, TimeUnit.MINUTES).build()).run {
					//if (LastModified.isNotEmpty())
						//this.addHeader("If-Modified-Since", LastModified)
					this
				}.build()
		}

		private fun newCall(serverId: Int): Call {
			return client.newCall(newRequest(serverId))
		}

		suspend fun call(serverId: Int, reCallTimes: Int = 0, reCallTimesLimit: Int = 3): Response {
			try {
				return newCall(serverId).execute().apply {
					if (this.networkResponse != null)
						CallTimes++
				}
			}  catch (e: Exception) {
				CallTimes++
				FailTimes++
				if((e is SocketTimeoutException || e is UnknownHostException) && reCallTimes < reCallTimesLimit){
					delay(1500)
					WannaHomeKt.logger.warning { "Jim尝试第${reCallTimes + 1}次获取[${serverNameMap[serverId]}]失败：$e" }
					return call(serverId, reCallTimes + 1)
				}else
					throw e
			}
		}
	}

	private val jsonDecoder: Json by lazy {
		Json { ignoreUnknownKeys = true }
	}
	override val sourceName: String = "Jim"

	override suspend fun run(serverId: Int, lastTurnStart: Long, thisTurnStart: Long): Map<String, PlotInfo> {
		//WannaHomeKt.logger.info { "开始获取HouseHelper[${serverNameMap[serverId]}]" }

		val voteInfoMap = mutableMapOf<String, PlotInfo>()
		val startTimeStamp = Calendar.getInstance().timeInMillis
		withContext(Dispatchers.IO) {
			call(serverId).use { response ->
				val str = response.body?.string() ?: ""
				if (response.networkResponse != null) {
					TimeMillis += Calendar.getInstance().timeInMillis - startTimeStamp
					(Calendar.getInstance().timeInMillis - startTimeStamp).run {
						TimeMillis += this
						if (this > MaxMillis) MaxMillis = this
						if (this < MinMillis) MinMillis = this
					}
					if (response.headers["last-modified"] != null)
						LastModified = response.headers["last-modified"]!!
				}
				if (response.isSuccessful && str.isNotEmpty()) {
					val voteInfoList = jsonDecoder.decodeFromString<List<VoteInfo>>(str)

					voteInfoList.filter { voteInfo ->
						voteInfo.LastSeen >= thisTurnStart || (voteInfo.UpdateTime >= lastTurnStart && voteInfo.State == 3)
					}.forEach {
						it.TerritoryId = territoryMap.keys.toList()[it.TerritoryId]//将房区序号改为房区ID
						voteInfoMap.run {
							//if (it.WardId == 16 && it.houseNum == 36)
							//WannaHomeKt.logger.info { "临时测试：${it.TerritoryId} ${it.WardId + 1}-${it.houseNum} ${it.State}(${it.VoteCount}/${it.WinnerIndex})：${unixTimeToStr(it.UpdateTime)}" }

							/*if (containsKey("${it.TerritoryId}-${it.WardId}-${it.houseNum - 1}")) {
								if (this["$serverId-${it.TerritoryId}-${it.WardId}-${it.houseNum - 1}"]!!.Update < it.UpdateTime || this["${it.TerritoryId}-${it.WardId}-${it.houseNum - 1}"]!!.SaleState == 0) {
									this["$serverId-${it.TerritoryId}-${it.WardId}-${it.houseNum - 1}"]!!.VoteCount = it.VoteCount
									this["$serverId-${it.TerritoryId}-${it.WardId}-${it.houseNum - 1}"]!!.WinnerIndex = it.WinnerIndex
									this["$serverId-${it.TerritoryId}-${it.WardId}-${it.houseNum - 1}"]!!.SaleState = it.State
									this["$serverId-${it.TerritoryId}-${it.WardId}-${it.houseNum - 1}"]!!.Update = it.UpdateTime
								}
							} else {*/
							if (it.UpdateTime < thisTurnStart && it.LastSeen >= thisTurnStart)
								this["$serverId-${it.TerritoryId}-${it.WardId}-${it.houseNum - 1}"] = PlotInfo(serverId, it.TerritoryId, it.WardId, it.houseNum - 1, it.Size, it.LastSeen, 0, 0, 0)
							else
								this["$serverId-${it.TerritoryId}-${it.WardId}-${it.houseNum - 1}"] = PlotInfo(serverId, it.TerritoryId, it.WardId, it.houseNum - 1, it.Size, it.UpdateTime, it.State, it.VoteCount, it.WinnerIndex)
							//}
						}
					}
				} else if (str.isEmpty()) {
					throw IllegalStateException("HouseHelper返回内容为空：Code:${response.code}\nBody:${str}")
				} else {
					throw IllegalStateException("HouseHelper返回无效：Code:${response.code}\nBody:${str}")
				}
			}
		}
		//WannaHomeKt.logger.info { "结束获取HouseHelper[${serverNameMap[serverId]}]：耗时${diffTimeToStr(Calendar.getInstance().timeInMillis - startTimeStamp)}" }
		return voteInfoMap
	}

	@Serializable
	data class VoteInfo(
		val Server: Int,
		@SerialName("Area")
		var TerritoryId: Int,
		@SerialName("Slot")
		val WardId: Int,
		/**
		 * 房屋号，从1开始
		 */
		@SerialName("ID")
		val houseNum: Int,
		val Price: Int,
		val Size: Int,
		/**
		 * 房屋在大水晶被看到，秒为单位
		 */
		val FirstSeen: Long,
		/**
		 * 房屋在大水晶被看到，秒为单位
		 */
		val LastSeen: Long,
		val State: Int,
		@SerialName("Participate")
		val VoteCount: Int,
		@SerialName("Winner")
		val WinnerIndex: Int,
		val EndTime: Int,
		/**
		 * 房屋详细信息更新时间，秒为单位
		 */
		val UpdateTime: Long,
		val PurchaseType: Int,
		val RegionType: Int,
	)

}