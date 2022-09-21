package cn.status102.data

import cn.status102.WannaHomeKt
import cn.status102.WannaHomeKt.reload
import cn.status102.client
import cn.status102.data.VoteInfoCha.Companion.Logger.CallTimes
import cn.status102.data.VoteInfoCha.Companion.Logger.FailTimes
import cn.status102.data.VoteInfoCha.Companion.Logger.TimeMillis
import cn.status102.strTimeToUnix
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import net.mamoe.mirai.console.data.AutoSavePluginData
import net.mamoe.mirai.console.data.value
import net.mamoe.mirai.utils.error
import net.mamoe.mirai.utils.info
import okhttp3.Call
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

class VoteInfoCha : VoteInfoOperate {

	companion object {
		init {
			Logger.reload()
		}

		object Logger : AutoSavePluginData("VoteInfo_Cettiidae_Log") {
			var CallTimes by value(0)
			var FailTimes by value(0)
			var TimeMillis by value(0L)
		}

		var LastModified = ""
		private const val HomeUrl = "https://home-api.iinformation.info/v2/data/"
		private fun newRequest(serverId: Int): Request {
			return Request.Builder().url(HomeUrl + serverId).get()
				//.cacheControl(CacheControl.Builder().maxAge(1, TimeUnit.MINUTES).noCache().build())
				.header("Cache-Control", "public, no-cache")
				.run {
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
					WannaHomeKt.logger.error { "猹获取错误：$e\n${e.stackTraceToString()}" }
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
		WannaHomeKt.logger.info { "开始获取猹：${SimpleDateFormat("YYYY-MM-dd HH:mm:ss.SSS").format(Calendar.getInstance().time.time)}" }

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
						strTimeToUnix(voteInfo.updateTimeStr) >= thisTurnStart || (strTimeToUnix(voteInfo.updateTimeStr) >= lastTurnStart && voteInfo.IsSell in 2..3)
					}.forEach {
						voteInfoMap.run {
							if (containsKey("${it.TerritoryId}-${it.WardId}-${it.HouseId}")) {
								if (this["$serverId-${it.TerritoryId}-${it.WardId}-${it.HouseId}"]!!.Update < strTimeToUnix(it.updateTimeStr) || this["${it.TerritoryId}-${it.WardId}-${it.HouseId}"]!!.SaleState == 0) {
									this["$serverId-${it.TerritoryId}-${it.WardId}-${it.HouseId}"]!!.VoteCount = it.VoteCount
									this["$serverId-${it.TerritoryId}-${it.WardId}-${it.HouseId}"]!!.WinnerIndex = it.WinnerIndex
									this["$serverId-${it.TerritoryId}-${it.WardId}-${it.HouseId}"]!!.SaleState = it.IsSell
									this["$serverId-${it.TerritoryId}-${it.WardId}-${it.HouseId}"]!!.Update = strTimeToUnix(it.updateTimeStr)
								}
							} else {
								this["$serverId-${it.TerritoryId}-${it.WardId}-${it.HouseId}"] = PlotInfo(serverId, it.TerritoryId, it.WardId, it.HouseId, it.Size, strTimeToUnix(it.updateTimeStr), it.IsSell, it.VoteCount, it.WinnerIndex)
							}
						}
					}
				} else {
					WannaHomeKt.logger.error { "猹获取错误：Code:${response.code}\nBody:${response}" }
				}
			}
		} catch (e: Exception) {
			WannaHomeKt.logger.error { "猹获取错误：$e\n${e.stackTraceToString()}" }
		}
		WannaHomeKt.logger.info { "结束获取猹：${SimpleDateFormat("YYYY-MM-dd HH:mm:ss.SSS").format(Calendar.getInstance().time.time)}" }
		return voteInfoMap
	}

	@Serializable
	data class VoteInfo(
		//@SerialName("UNIQUE_ID")
		//val UniqueId: Long,
		@SerialName("SERVER_ID")
		val ServerId: Int,
		@SerialName("TERRITORY")
		val TerritoryId: Int,
		@SerialName("WARD")
		val WardId: Int,
		@SerialName("HOUSE_NUMBER")
		val HouseId: Int,
		@SerialName("SIZE")
		val Size: Int,
		@SerialName("HOUSE_TYPE")
		val HouseType: Int,
		@SerialName("OWNER")
		val Owner: String,
		@SerialName("UPDATED_TIME")
		val UpdateTime: String,
		@SerialName("IS_SELL")
		val IsSell: Int,
		@SerialName("PRICE")
		val Price: Int,
		@SerialName("VOTECOUNT")
		val VoteCount: Int,
		@SerialName("WINNER")
		val WinnerIndex: Int,
	) {
		val updateTimeStr: String get() = UpdateTime.replace("T", " ")
	}
}
