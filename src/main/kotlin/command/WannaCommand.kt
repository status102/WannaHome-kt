package cn.status102.command

import cn.status102.*
import cn.status102.data.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import net.mamoe.mirai.console.command.CommandContext
import net.mamoe.mirai.console.command.SimpleCommand
import net.mamoe.mirai.console.command.getGroupOrNull
import net.mamoe.mirai.console.command.isNotConsole
import net.mamoe.mirai.console.plugin.name
import net.mamoe.mirai.console.plugin.version
import net.mamoe.mirai.contact.MessageTooLargeException
import net.mamoe.mirai.message.data.MessageSource.Key.quote
import net.mamoe.mirai.message.data.toMessageChain
import net.mamoe.mirai.utils.ExternalResource.Companion.toExternalResource
import net.mamoe.mirai.utils.ExternalResource.Companion.uploadAsImage
import net.mamoe.mirai.utils.error
import net.mamoe.mirai.utils.info
import org.jetbrains.skia.Font
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Surface
import org.jetbrains.skia.TextLine
import java.io.File
import java.util.*
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToLong

object WannaCommand : SimpleCommand(
	WannaHomeKt, "空房", description = "获取服务器空余地块"
) {
	@Handler
	suspend fun handle(commandContext: CommandContext) {

		val font = Font(font?.typefaceOrDefault)
		font.size = 36f

		val time = TimeCalculator.getInstance()

		val output = mutableListOf<String>()
		output.add("使用方法：")
		output.add("/空地：获取基本使用方法")
		output.add("/空地 <服务器/大区名/SML|海|森|白|沙|雪|个人|部队|准备>：获取服务器空地信息")
		output.add("/空地 简称：获取服务器简称列表")
		output.add("")
		if (!time.canEntry)
			output.add(String.format("今日为第%d轮%d天", time.turn, time.diff % 9 + 1) + String.format("下轮参与时间：%s点~%s点", unixTimeToStr(time.nextTurnStart).substring(5, 13), unixTimeToStr(time.nextTurnShow).substring(5, 13)))
		else
			output.add(String.format("今日为第%d轮%d天", time.turn, time.diff % 9 + 1) + String.format("公示时间：%s点~%s点", unixTimeToStr(time.nextEventTime).substring(5, 13), unixTimeToStr(time.nextTurnStart).substring(5, 13)))
		output.add("")
		output.add("感谢提供数据支持：")
		output.add("Cettiidae/猹：https://home.ff14.cn/")
		output.add("（使用第三方插件上传摇号数据，支持记录人数）")
		output.add("冰音：https://wanahome.ffxiv.bingyin.org/")
		output.add("（网站提供ngld悬浮窗上传方式，无需配置额外插件）")
		output.add("Jim：https://house.ffxiv.cyou/")
		output.add("（网站提供ACT插件上传方式，支持记录人数）")
		output.add("最小/最大/平均耗时[失败次数/请求量]：")
		output.add(
			String.format(
				"%.3f/%.3f/%.3fs[%,d/%,d]，%.3f/%.3f/%.3fs[%,d/%,d]，%.3f/%.3f/%.3fs[%,d/%,d]",
				VoteInfoCha.Logger.MinMillis / 1000.0, VoteInfoCha.Logger.MaxMillis / 1000.0, VoteInfoCha.Logger.TimeMillis.toDouble() / 1000.0 / VoteInfoCha.Logger.CallTimes, VoteInfoCha.Logger.FailTimes, VoteInfoCha.Logger.CallTimes,
				HouseInfo.Logger.MinMillis / 1000.0, HouseInfo.Logger.MaxMillis / 1000.0, HouseInfo.Logger.TimeMillis.toDouble() / 1000.0 / HouseInfo.Logger.CallTimes, HouseInfo.Logger.FailTimes, HouseInfo.Logger.CallTimes,
				VoteInfoHouseHelper.Logger.MinMillis / 1000.0, VoteInfoHouseHelper.Logger.MaxMillis / 1000.0, VoteInfoHouseHelper.Logger.TimeMillis.toDouble() / 1000.0 / VoteInfoHouseHelper.Logger.CallTimes, VoteInfoHouseHelper.Logger.FailTimes, VoteInfoHouseHelper.Logger.CallTimes,
			)
		)
		output.add("")
		if (Config.owner < 1)
			output.add("当前未配置插件号主")
		else
			output.add(
				"当前配置号主：" + Config.owner.toString().run { if (this.length > 4) this.take(2) + "*".repeat(this.length - 4) + this.takeLast(2) else this }
			)
		output.add("所有空地信息均由玩家上传，希望更多人能加入上传信息的队列")
		output.add("By status102/${WannaHomeKt.name}/${WannaHomeKt.version}")

		val outputTextLine = output.map { TextLine.make(it, font) }

		val surface = Surface.makeRasterN32Premul((Merge_Left + (outputTextLine.maxOfOrNull { it.width } ?: 0F) + Merge_Right).toInt(), (Merge_Up + outputTextLine.sumOf { ceil(it.height).toInt() } + Merge_Down).toInt())
		surface.canvas.run {
			clear(Paint().setARGB(0xFF, 0xFF, 0xFF, 0xFF).color)
			val black = Paint().setARGB(0xFF, 0, 0, 0)
			var baseHeight = 0F
			outputTextLine.forEach {
				drawTextLine(it, Merge_Left, baseHeight + Merge_Up - font.metrics.ascent, black)
				baseHeight += it.height
			}
			//drawImage(qrEncoder, (surface.width - qrEncoder.width) / 5F * 4, Merge_Up + Merge_Mid + outputTextLine.take(3).sumOf { ceil(it.height).toInt() })
		}

		surface.makeImageSnapshot().encodeToData()?.use {
			it.bytes.inputStream().toExternalResource().use {
				if (commandContext.sender.subject != null) {
					val image = it.uploadAsImage(commandContext.sender.subject!!)
					if (commandContext.sender.isNotConsole())
						commandContext.sender.sendMessage(commandContext.originalMessage.quote() + image)
					else {
						commandContext.sender.sendMessage("控制台：" + image.toMessageChain().contentToString())
					}
				} else {
					surface.makeImageSnapshot().encodeToData()?.use {
						WannaHomeKt.logger.info { "未设置输出群号，导出至Data文件夹tmp.png，大小：${it.size}" }
						File(WannaHomeKt.dataFolderPath.toString(), "tmp.png").writeBytes(it.bytes)
					}
				}
			}
		}
		surface.close()
	}

	@Handler
	suspend fun handle(commandContext: CommandContext, str: String) { // 函数名随意, 但参数需要按顺序放置.
		if (str == "简称") {
			sendServerNickName(commandContext)
			return
		}
		handle(commandContext, str, "")
	}

	@Handler
	suspend fun handle(commandContext: CommandContext, str: String, size: String) {
		if (str.isEmpty() || (!serverIdMap.containsKey(str) && !Config.subNameMap.containsKey(str) && !serverMap.containsKey(str))) {
			commandContext.sender.sendMessage(commandContext.originalMessage.quote() + "<${str}>服务器/大区不存在")
			return
		}
		var serverName = str
		if (Config.subNameMap.containsKey(serverName)) serverName = Config.subNameMap[serverName].toString()
		val realName = serverName
		//if (serverIdMap.containsKey(serverName)) serverName = serverIdMap[serverName].toString()

		val search = serverOrDcNickNameToServerName(serverName).map { serverIdMap[it] ?: 0 }.toSet().filter { it > 0 }

		val now = Calendar.getInstance().timeInMillis
		val start = Calendar.getInstance().apply { time = strTimeToDate("2022-08-08 23:00:00") }

		val diff = floor((now - start.timeInMillis) / 1000 / 60.0 / 60 / 24).toInt()
		val turn = diff / 9 + 1
		val canEntry = (diff % 9) < 5
		val nextEventTime = (start.clone() as Calendar).apply {
			if (canEntry)
				add(Calendar.DAY_OF_MONTH, ((turn - 1) * 9 + 5))
			else
				add(Calendar.DAY_OF_MONTH, turn * 9)
		}

		changeBotGroupNameCard(commandContext.sender.getGroupOrNull())

		val output = getServerData(commandContext.sender.getGroupOrNull()?.id, search, realName, size.uppercase(Locale.getDefault())) { i, _ -> i >= 30 }.trimEnd('\n')
		runBlocking {
			try {
				if (commandContext.sender.isNotConsole())
					commandContext.sender.sendMessage(commandContext.originalMessage.quote() + output)
				else
					commandContext.sender.sendMessage(output)
			} catch (e: MessageTooLargeException) {
				val list = output.split("\n")
				for (i in list.indices step 8) {
					commandContext.sender.sendMessage(commandContext.originalMessage.quote() + "(${i / 8 + 1}/${ceil(list.size / 8.0).toInt()})" + list.slice(i until minOf(i + 8, list.size)).reduce { acc, s -> "$acc\n$s" })
					delay(500)
				}
			}
			synchronized(groupListLock) {
				if (commandContext.sender.getGroupOrNull() != null)
					Data.WannaHomeGroupList.add("${commandContext.sender.bot?.id}-${commandContext.sender.getGroupOrNull()?.id}")
			}
		}
	}

	@Handler
	suspend fun handle(commandContext: CommandContext, serverList: List<String>, limitStr: String) {
		if (serverList.isEmpty() || !serverList.all { serverIdMap.containsKey(it) || Config.subNameMap.containsKey(it) || serverMap.containsKey(it) }) {
			commandContext.sender.sendMessage(commandContext.originalMessage.quote() + "<${serverList.joinTo(java.lang.StringBuilder())}>服务器/大区不存在")
			return
		}
		val search = serverList.fold(mutableListOf<Int>()) { sum, element -> (sum + serverOrDcNickNameToServerName(element).map { serverIdMap[it] ?: 0 }.filter { it > 0 }).toMutableList() }.toSet().toList()
		val realName = if (serverList.size == 1) serverList[0] else "${search.size}个服"

		val now = Calendar.getInstance().timeInMillis
		val start = Calendar.getInstance().apply { time = strTimeToDate("2022-08-08 23:00:00") }

		val diff = floor((now - start.timeInMillis) / 1000 / 60.0 / 60 / 24).toInt()
		val turn = diff / 9 + 1
		val canEntry = (diff % 9) < 5
		val nextEventTime = (start.clone() as Calendar).apply {
			if (canEntry)
				add(Calendar.DAY_OF_MONTH, ((turn - 1) * 9 + 5))
			else
				add(Calendar.DAY_OF_MONTH, turn * 9)
		}

		changeBotGroupNameCard(commandContext.sender.getGroupOrNull())

		try {
			getPic(realName, search, commandContext.sender.getGroupOrNull()?.id ?: 0, limitStr.uppercase(Locale.getDefault()))
				.use { surface ->
					surface.makeImageSnapshot().encodeToData()?.use { data ->
						data.bytes.inputStream().toExternalResource().use {
							if (commandContext.sender.subject != null) {
								val image = it.uploadAsImage(commandContext.sender.subject!!)
								if (commandContext.sender.isNotConsole()) {
									commandContext.sender.sendMessage(commandContext.originalMessage.quote() + image)
								} else
									commandContext.sender.sendMessage("控制台：" + image.toMessageChain().contentToString())
							}
						}
					}
				}
		} catch (e: Exception) {
			WannaHomeKt.logger.error { "发送房屋数据失败：${e.stackTraceToString()}" }
			commandContext.sender.sendMessage(commandContext.originalMessage.quote() + "发送房屋数据失败：$e")
		}
		synchronized(groupListLock) {
			if (commandContext.sender.getGroupOrNull() != null)
				Data.WannaHomeGroupList.add("${commandContext.sender.bot?.id}-${commandContext.sender.getGroupOrNull()?.id}")
		}

	}

	private fun serverOrDcNickNameToServerName(name: String): MutableList<String> {
		return if ((serverMap.keys + Config.subNameMap.keys).contains(name)) {
			val list = mutableListOf<String>()
			(serverMap + Config.subNameMap.map { Pair(it.key, listOf(it.value)) })[name]!!
				.forEach {
					list.addAll(serverOrDcNickNameToServerName(it))
				}
			list
		} else
			mutableListOf(name)
	}

	suspend fun sendServerNickName(commandContext: CommandContext) {
		val nameList: MutableMap<String, MutableList<String>> = mutableMapOf()
		Config.subNameMap.forEach { (t, u) ->
			if (nameList.containsKey(u)) {
				nameList[u]?.add(t)
			} else {
				nameList[u] = mutableListOf(t)
			}
		}
		val output = StringBuilder()
		output.appendLine("服务器缩写[${nameList.count()}个]：")
		nameList.forEach { (t, u) -> output.appendLine("$t：[${u.joinTo(StringBuilder())}]") }
		commandContext.sender.sendMessage(output.toString().trimEnd('\n'))
	}

	suspend fun getHouseData(serverIdList: List<Int>): Channel<Triple<IVoteInfoOperate, Boolean, Map<String, PlotInfo>>> {
		val channel = Channel<Triple<IVoteInfoOperate, Boolean, Map<String, PlotInfo>>>(Channel.UNLIMITED)

		WannaHomeKt.launch {
			serverIdList.forEach { serverId ->
				HouseDataList.forEach {
					launch {
						try {
							channel.send(Triple(it, true, it.run(serverId)))
						} catch (e: Exception) {
							WannaHomeKt.logger.error { "${it.sourceName}获取网络数据出错：${e.stackTraceToString()}" }
							channel.send(Triple(it, false, mapOf()))
						}
					}
				}
			}
			//list.forEach { it.join() }
		}
		return channel
	}

	private suspend fun getServerData(groupId: Long?, searchList: List<Int>, serverName: String, limitStr: String = "", predicate: (Int, HouseInfo.ServerData.SaleItem) -> Boolean): String {
		try {
			val now = Calendar.getInstance().timeInMillis / 1000
			val start = Calendar.getInstance().apply { time = strTimeToDate("2022-08-08 23:00:00") }

			val diff = floor((now - start.timeInMillis / 1000) / 60.0 / 60 / 24).toInt()
			val turn = diff / 9 + 1
			val canEntry = (diff % 9) < 5
			val lastTurnStart = (start.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, (turn - 2) * 9) }.timeInMillis / 1000
			val thisTurnStart = (start.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, (turn - 1) * 9) }.timeInMillis / 1000
			val thisTurnShow = (start.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, (turn - 1) * 9 + 5) }.timeInMillis / 1000
			val nextEventTime = (start.clone() as Calendar).apply {
				if (canEntry)
					add(Calendar.DAY_OF_MONTH, ((turn - 1) * 9 + 5))
				else
					add(Calendar.DAY_OF_MONTH, turn * 9)
			}
			val nextEventTimeStr = String.format("%d号%02d点", nextEventTime.get(Calendar.DAY_OF_MONTH), nextEventTime.get(Calendar.HOUR_OF_DAY))

			val plotInfoMap = mutableMapOf<String, PlotInfo>()
			val output = java.lang.StringBuilder()
			output.appendLine(String.format("[第%s轮第%s天-%s中]<%s>%s：", turn, diff % 9 + 1, if (canEntry) "参与" else "公示", serverName, limitStr.uppercase(Locale.getDefault())))

			val channel = getHouseData(searchList)
			repeat(HouseDataList.size * searchList.size) {
				channel.receive().third.forEach { (key, it) ->
					if (plotInfoMap.containsKey(key)) {
						if (plotInfoMap[key]!!.SaleState == 0 ||
							(it.Update > plotInfoMap[key]!!.Update &&
									((it.Update >= thisTurnStart && plotInfoMap[key]!!.Update < thisTurnStart) || it.SaleState != 0))
						) {
							plotInfoMap[key]!!.VoteCount = it.VoteCount
							plotInfoMap[key]!!.WinnerIndex = it.WinnerIndex
							plotInfoMap[key]!!.SaleState = it.SaleState
							plotInfoMap[key]!!.Update = it.Update
						}
					} else
						plotInfoMap[key] = it
				}
			}
			channel.close()

			try {
				plotInfoMap.values.forEach {
					if ((it.SaleState == 3 || (it.SaleState == 2 && it.WinnerIndex == 0)) && it.Update < thisTurnStart) {
						it.Update = thisTurnStart
						it.SaleState = 0
					}
				}
				var personList: List<PlotInfo> = plotInfoMap.values.filter { it.Update >= lastTurnStart && it.WardId < fcIdStart }
				var fcList: List<PlotInfo> = plotInfoMap.values.filter { it.Update >= lastTurnStart && it.WardId >= fcIdStart }

				personList = personList.filter { it.SaleState == 0 || it.Update >= thisTurnStart }
				fcList = fcList.filter { it.SaleState == 0 || it.Update >= thisTurnStart }

				if (limitStr.isNotEmpty()) {
					//单独查询个人房or部队房
					if (limitStr.contains("部队")) personList = mutableListOf()
					if (limitStr.contains("个人")) fcList = mutableListOf()
					if (limitStr.contains("准备")) {
						personList = personList.filter { it.SaleState == 3 || (it.SaleState == 2 && it.WinnerIndex == 0) }
						fcList = fcList.filter { it.SaleState == 3 || (it.SaleState == 2 && it.WinnerIndex == 0) }
					}

					if (limitStr.any { i -> sizeMap.contains("$i") }) {
						personList = personList.filter { limitStr.contains(sizeMap[it.Size]) }
						fcList = fcList.filter { limitStr.contains(sizeMap[it.Size]) }
					}
					if (limitStr.any { i -> territoryMap.containsValue("$i") }) {
						personList = personList.filter { limitStr.contains(territoryMap[it.TerritoryId] ?: "") }
						fcList = fcList.filter { limitStr.contains(territoryMap[it.TerritoryId] ?: "") }
					}
				}
				personList = personList.run { asSequence().sortedBy { it.WardId * 60 + it.HouseId }.sortedBy { it.TerritoryId }.sortedByDescending { it.ServerId }.sortedByDescending { it.Size }.sortedByDescending { it.VoteCount }.toList() }
				fcList = fcList.run { asSequence().sortedBy { it.WardId * 60 + it.HouseId }.sortedBy { it.TerritoryId }.sortedByDescending { it.ServerId }.sortedByDescending { it.Size }.sortedByDescending { it.VoteCount }.toList() }

				var personShow = min(Limit_Person.first, personList.size)
				var fcShow = min(Limit_FC.first, fcList.size)
				if (personShow < Limit_Person.first) fcShow = Limit_Person.first + Limit_FC.first - personShow
				if (fcShow < Limit_FC.first) personShow = Limit_Person.first + Limit_FC.first - fcShow

				if (personList.isEmpty() && fcList.isEmpty()) {
					output.deleteCharAt(output.length - 1)
					output.appendLine("无空地 / 无上传数据")
				} else {
					output.deleteCharAt(output.length - 1)
					output.appendLine("${personList.size + fcList.size}处")
				}
				/**
				 * 参与人数过期时间，单位为秒
				 */
				var outdatedLimit: Long
				val outdatedTime: String
				if (canEntry) {
					outdatedLimit = maxOf(10 * 60.0, minOf(outdatedWarn.toDouble(), (now - thisTurnStart) * 0.7, (thisTurnShow - now) * 0.7)).toLong()
					outdatedTime = if (outdatedLimit / 60 >= 60) {
						outdatedLimit = (outdatedLimit / 60.0 / 60).roundToLong() * 60 * 60
						"超过${outdatedLimit / 60 / 60}小时"
					} else {
						outdatedLimit = (outdatedLimit / 60.0 / 5).roundToLong() * 5 * 60
						"超过${outdatedLimit / 60}分钟"
					}
				} else {
					outdatedLimit = now - thisTurnShow
					outdatedTime = "公布后"
				}

				if (!limitStr.contains("个人") && personList.isNotEmpty()) output.appendLine("个人：${personList.size}")
				output.append(printSaleList(personList, personShow, now, outdatedLimit, searchList.size > 1))
				if (!limitStr.contains("部队") && fcList.isNotEmpty()) output.appendLine("部队：${fcList.size}")
				output.append(printSaleList(fcList, fcShow, now, outdatedLimit, searchList.size > 1))

				//output.appendLine(String.format("今日为第%d轮%d天，%s轮%s：%s", turn, diff % 9 + 1, if (canEntry) "本" else "下", if (!canEntry) "参与" else "公示", nextEventTimeStr))
				if (output.contains(outdatedWarnTag))
					output.appendLine(outdatedWarnTag + outdatedTime + outdatedWarnTips)
				if (personList.size + fcList.size > Limit_Person.first + Limit_FC.first) {
					if (houseTipsGroup.contains(groupId))
						output.appendLine("更多空地请查阅群公告中网站")
					else
						output.appendLine("更多空地使用“/空地”查看数据源")
				}
			} catch (e: Exception) {
				output.appendLine("转换出错：")
				//output.appendLine("Content：${content}")
				output.appendLine(e.stackTraceToString())
				WannaHomeKt.logger.error { "数据转换出错：${e}\n${e.stackTraceToString()}" }
			}
			return output.toString().trimEnd('\n')
			//return "获取${serverName}服务器空地为空：from：${fromStr}\n content：${content}"
		} catch (e: Exception) {
			WannaHomeKt.logger.error { "获取<${serverName}>数据出错：${e}\n${e.stackTraceToString()}" }
			return "获取<${serverName}>数据出错：${e}"
		}
	}

	private fun printSaleList(saleList: List<PlotInfo>, countLimit: Int, nowTimeStamp: Long, outdatedLimit: Long, showServerName: Boolean = false): String {
		if (saleList.isEmpty())
			return ""
		val output = StringBuilder()
		for ((i, sale) in saleList.withIndex()) {
			if (i < countLimit) {
				if (sale.SaleState == 1 && nowTimeStamp - sale.Update >= outdatedLimit)
					output.append(outdatedWarnTag)
				output.append(String.format("[%s %s%s%02d-%02d]", sizeMap[sale.Size], if (showServerName) "${serverNameMap[sale.ServerId]?.substring(0, 2)} " else "", territoryMap[sale.TerritoryId], sale.WardId + 1, sale.HouseId + 1))
				if (sale.SaleState in 1..2)
					output.append(String.format(" 参与:%,d人", sale.VoteCount))
				if (sale.SaleState == 2)
					output.append(String.format("(中奖%,d号)", sale.WinnerIndex))
				if (sale.SaleState == 3)
					output.append(" 准备中")
				output.appendLine()
			}
		}
		if (saleList.size > countLimit) {
			output.deleteCharAt(output.length - 1)
			output.appendLine(" ...")
		}
		return output.toString()
	}
}

