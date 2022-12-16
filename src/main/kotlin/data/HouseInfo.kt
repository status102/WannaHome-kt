package cn.status102.data

import cn.status102.TimeCalculator
import cn.status102.WannaHomeKt
import cn.status102.WannaHomeKt.reload
import cn.status102.client
import cn.status102.data.BingyinLogger.CallTimes
import cn.status102.data.BingyinLogger.FailTimes
import cn.status102.data.BingyinLogger.MaxMillis
import cn.status102.data.BingyinLogger.MinMillis
import cn.status102.data.BingyinLogger.TimeMillis
import cn.status102.serverNameMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
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

object BingyinLogger : AutoSavePluginData("Wanahome_Bingyin_Log") {
	var CallTimes by value(0)
	var FailTimes by value(0)
	var TimeMillis by value(0L)
	var MaxMillis by value(Long.MIN_VALUE)
	var MinMillis by value(Long.MAX_VALUE)
}

class HouseInfo : VoteInfoOperate() {

	companion object {
		init {
			Logger.reload()
		}

		val Logger get() = BingyinLogger

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
			return try {
				newCall(serverId).execute().apply {
					if (networkResponse != null)
						CallTimes++
				}
			} catch (e: Exception) {
				CallTimes++
				FailTimes++
				if((e is SocketTimeoutException || e is UnknownHostException) && reCallTimes < reCallTimesLimit){
					delay(1500)
					WannaHomeKt.logger.warning { "冰音尝试第${reCallTimes + 1}次获取[${serverNameMap[serverId]}]失败：$e" }
					call(serverId, reCallTimes + 1)
				}else
					throw e
			}
		}
	}

	override val sourceName: String = "冰音"

	override suspend fun run(serverId: Int): Map<String, PlotInfo> {
		//WannaHomeKt.logger.info { "开始获取${sourceName}[${serverNameMap[serverId]}]" }
		val time = TimeCalculator.getInstance()
		val voteInfoMap = mutableMapOf<String, PlotInfo>()
		val startTimeStamp = Calendar.getInstance().timeInMillis

		withContext(Dispatchers.IO) {
			call(serverId).use { response ->
				val str = response.body?.string() ?: ""
				if (response.networkResponse != null) {
					(Calendar.getInstance().timeInMillis - startTimeStamp).run {
						TimeMillis += this
						if (this > MaxMillis) MaxMillis = this
						if (this < MinMillis) MinMillis = this
					}
					if (response.headers["last-modified"] != null)
						LastModified = response.headers["last-modified"]!!
				}
				if (response.isSuccessful && str.isNotEmpty()) {
					val saleList = jsonDecoder.decodeFromString<ServerData>(str)
					val lastShow = time.lastTurnStart + 5 * 24 * 3600
					//WannaHomeKt.logger.info  { "冰音：${saleList.OnSale.size}，${saleList.LastUpdate}-${thisTurnStart}" }
					if (saleList.LastUpdate >= (Calendar.getInstance().timeInMillis) / 1000 - 5 * 24 * 3600)
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
		}
		//WannaHomeKt.logger.info { "结束获取冰音[${serverNameMap[serverId]}]：耗时${diffTimeToStr(Calendar.getInstance().timeInMillis - startTimeStamp)}" }
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