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

class VoteInfoCha : VoteInfoOperate {

	companion object {
		private const val HomeUrl = "https://home-api.iinformation.info/v2/data/"
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
					WannaHomeKt.logger.error { "猹获取错误：$e\n${e.stackTraceToString()}" }
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
		//WannaHomeKt.logger.info { "开始获取猹：${SimpleDateFormat("YYYY-MM-dd HH:mm:ss.SSS").format(Calendar.getInstance().time.time)}" }
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

				voteInfoList.filter {
					strTimeToUnix(it.updateTimeStr) >= thisTurnStart.time.time / 1000 || (strTimeToUnix(it.updateTimeStr) >= lastTurnStart.time.time / 1000 && it.IsSell == 3)
				}.forEach {
					voteInfoMap.run {
						if (containsKey("${it.TerritoryId}-${it.WardId}-${it.HouseId}")) {
							if (this["${it.TerritoryId}-${it.WardId}-${it.HouseId}"]!!.Update < strTimeToUnix(it.updateTimeStr) || this["${it.TerritoryId}-${it.WardId}-${it.HouseId}"]!!.SaleState == 0) {
								this["${it.TerritoryId}-${it.WardId}-${it.HouseId}"]!!.VoteCount = it.VoteCount
								this["${it.TerritoryId}-${it.WardId}-${it.HouseId}"]!!.WinnerIndex = it.WinnerIndex
								this["${it.TerritoryId}-${it.WardId}-${it.HouseId}"]!!.SaleState = it.IsSell
								this["${it.TerritoryId}-${it.WardId}-${it.HouseId}"]!!.Update = strTimeToUnix(it.updateTimeStr)
							}
						} else {
							this["${it.TerritoryId}-${it.WardId}-${it.HouseId}"] = PlotInfo(it.TerritoryId, it.WardId, it.HouseId, it.Size, strTimeToUnix(it.updateTimeStr), it.IsSell, it.VoteCount, it.WinnerIndex)
						}
					}
				}
			} else {
				WannaHomeKt.logger.error { "猹获取错误：Code:${resC.code}\nBody:${resC}" }
			}
		} catch (e: Exception) {
			WannaHomeKt.logger.error { "猹获取错误：$e\n${e.stackTraceToString()}" }
		}
		//WannaHomeKt.logger.info { "结束获取猹：${SimpleDateFormat("YYYY-MM-dd HH:mm:ss.SSS").format(Calendar.getInstance().time.time)}" }
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
