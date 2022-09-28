package cn.status102.data

import cn.status102.WannaHomeKt
import cn.status102.WannaHomeKt.reload
import cn.status102.client
import cn.status102.data.HouseInfo.Companion.Logger.CallTimes
import cn.status102.data.HouseInfo.Companion.Logger.FailTimes
import cn.status102.data.HouseInfo.Companion.Logger.TimeMillis
import kotlinx.coroutines.delay
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

class HouseInfo : VoteInfoOperate {

	companion object {
		init {
			Logger.reload()
		}

		object Logger : AutoSavePluginData("Wanahome_Bingyin_Log") {
			var CallTimes by value(0)
			var FailTimes by value(0)
			var TimeMillis by value(0L)
		}

		var LastModified = ""
		private const val HomeUrl = "https://wanahome.ffxiv.bingyin.org/api/state/"
		private fun newRequest(serverId: Int): Request {
			return Request.Builder().url("$HomeUrl?server=${serverId}&type=0").get()
				.cacheControl(CacheControl.Builder().maxAge(1, TimeUnit.MINUTES).build()).run {
					if (LastModified.isNotEmpty())
						this.addHeader("If-Modified-Since", LastModified)
					this
				}.build()
		}

		private fun newCall(serverId: Int): Call {
			return client.newCall(newRequest(serverId))
		}

		suspend fun call(serverId: Int, reCallTimes: Int = 0, reCallTimesLimit: Int = 3): Response {
			try {
				return newCall(serverId).execute().apply {
					if (networkResponse != null)
						CallTimes++
				}
			} catch (e: IOException) {
				if (reCallTimes < reCallTimesLimit) {
					CallTimes++
					FailTimes++
					delay(3000)
					WannaHomeKt.logger.error { "冰音获取错误：$e\n${e.stackTraceToString()}" }
					return call(serverId, reCallTimes + 1)
				} else
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
	override val sourceName: String = "HouseHelper(冰音)"

	override suspend fun run(serverId: Int, lastTurnStart: Long, thisTurnStart: Long): Map<String, PlotInfo> {
		//WannaHomeKt.logger.info { "开始获取冰音：${SimpleDateFormat("YYYY-MM-dd HH:mm:ss.SSS").format(Calendar.getInstance().time.time)}" }

		val voteInfoMap = mutableMapOf<String, PlotInfo>()
		val startTimeStamp = Calendar.getInstance().timeInMillis
		call(serverId).use { response ->
			val str = response.body?.string() ?: ""
			if (response.networkResponse != null) {
				TimeMillis += Calendar.getInstance().timeInMillis - startTimeStamp

				if (response.headers["last-modified"] != null)
					LastModified = response.headers["last-modified"]!!
			}
			if (response.isSuccessful && str.isNotEmpty()) {
				val saleList = jsonDecoder.decodeFromString<ServerData>(str)
				val lastShow = lastTurnStart + 5 * 24 * 3600
				if (saleList.LastUpdate >= thisTurnStart)
					saleList.OnSale
						//.filter { it.StartSell >= lastShow }
						.forEach {
							voteInfoMap["$serverId-${it.TerritoryId}-${it.WardId}-${it.HouseId}"] =
								PlotInfo(serverId, it.TerritoryId, it.WardId, it.HouseId, it.Size - 1, saleList.LastUpdate)
						}
			} else if (str.isEmpty()) {
				throw IllegalStateException("冰音返回内容为空：Code:${response.code}\nBody:${str}")
			} else {
				throw IllegalStateException("冰音返回无效：Code:${response.code}\nBody:${str}")
			}
		}
		//WannaHomeKt.logger.info { "结束获取冰音：${SimpleDateFormat("YYYY-MM-dd HH:mm:ss.SSS").format(Calendar.getInstance().time.time)}" }
		return voteInfoMap
	}

	@Serializable
	data class ServerData(
		val code: Int,
		val msg: String,
		@SerialName("onsale")
		val OnSale: List<SaleItem>,
		val changes: List<Change>,
		@SerialName("last_update")
		val LastUpdate: Long
	) {
		@Serializable
		data class SaleItem(
			@SerialName("server")
			val ServerId: Int,
			@SerialName("territory_id")
			val TerritoryId: Int,
			@SerialName("ward_id")
			val WardId: Int,
			@SerialName("house_id")
			val HouseId: Int,
			@SerialName("price")
			val Price: Int,
			@SerialName("start_sell")
			var StartSell: Long,
			@SerialName("size")
			val Size: Int,
			@SerialName("owner")
			val Owner: String
		)

		@Serializable
		data class Change(
			val house: House,
			val event_type: String,
			val param1: String,
			val param2: String,
			val record_time: Long
		) {
			@Serializable
			data class House(
				val server: Int,
				val territory_id: Int,
				val ward_id: Int,
				val house_id: Int
			)
		}
	}

}