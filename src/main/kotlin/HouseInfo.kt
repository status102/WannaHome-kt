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

class HouseInfo : VoteInfoOperate {

	companion object {
		private const val HomeUrl = "https://wanahome.ffxiv.bingyin.org/api/state/"
		private fun newRequest(serverId: String): Request {
			return Request.Builder().url("$HomeUrl?server=${serverId}&type=0").get().build()
		}

		private fun newCall(serverId: String): Call {
			return client.newCall(newRequest(serverId))
		}

		fun call(serverId: String, reCallTimes: Int = 0, reCallTimesLimit: Int = 3): Response {
			try {
				return newCall(serverId).execute()
			} catch (e: IOException) {
				if (reCallTimes < reCallTimesLimit) {
					WannaHomeKt.logger.error { "冰音获取错误：$e\n${e.stackTraceToString()}" }
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
		//WannaHomeKt.logger.info { "开始获取冰音：${SimpleDateFormat("YYYY-MM-dd HH:mm:ss.SSS").format(Calendar.getInstance().time.time)}" }

		val now = Calendar.getInstance().time.time
		val start = Calendar.getInstance().apply { time = strTimeToDate("2022-08-08 23:00:00") }
		val diff = floor((now - start.time.time) / 1000 / 60.0 / 60 / 24).toInt()
		val turn = diff / 9 + 1
		val thisTurnStart = (start.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, (turn - 1) * 9) }

		val voteInfoMap = mutableMapOf<String, PlotInfo>()
		try {
			val resWH = call(serverId)
			val strWH = resWH.body?.string() ?: ""
			if (resWH.isSuccessful && strWH.isNotEmpty()) {
				val saleList = jsonDecoder.decodeFromString<ServerData>(strWH)
				if (saleList.LastUpdate >= thisTurnStart.time.time / 1000)
					saleList.OnSale.forEach {
						voteInfoMap["${it.TerritoryId}-${it.WardId}-${it.HouseId}"] =
							PlotInfo(it.TerritoryId, it.WardId, it.HouseId, it.Size - 1, saleList.LastUpdate)
					}
			} else {
				WannaHomeKt.logger.error { "冰音获取错误：Code:${resWH.code}\nBody:${strWH}" }
			}
		} catch (e: Exception) {
			WannaHomeKt.logger.error { "冰音获取错误：$e\n${e.stackTraceToString()}" }
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