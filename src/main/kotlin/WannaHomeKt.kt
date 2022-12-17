package cn.status102

import cn.status102.Config.subNameMap
import cn.status102.command.*
import cn.status102.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import net.mamoe.mirai.console.command.*
import net.mamoe.mirai.console.command.CommandManager.INSTANCE.register
import net.mamoe.mirai.console.command.CommandManager.INSTANCE.unregister
import net.mamoe.mirai.console.command.CommandSender.Companion.toCommandSender
import net.mamoe.mirai.console.data.value
import net.mamoe.mirai.console.plugin.jvm.JvmPluginDescription
import net.mamoe.mirai.console.plugin.jvm.KotlinPlugin
import net.mamoe.mirai.contact.*
import net.mamoe.mirai.event.GlobalEventChannel
import net.mamoe.mirai.event.events.*
import net.mamoe.mirai.message.data.*
import net.mamoe.mirai.message.data.MessageSource.Key.quote
import net.mamoe.mirai.utils.ExternalResource.Companion.toExternalResource
import net.mamoe.mirai.utils.ExternalResource.Companion.uploadAsImage
import net.mamoe.mirai.utils.error
import net.mamoe.mirai.utils.info
import okhttp3.Cache
import okhttp3.OkHttpClient
import org.jetbrains.skia.*
import java.io.File
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.set
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToLong

val groupListLock = Any()

val HouseDataList = listOf(HouseInfo(), VoteInfoCha(), VoteInfoHouseHelper())

/**
 * 超过8小时数据未更新
 */
const val outdatedWarn: Long = 24 * 60 * 60//
const val outdatedWarnTag = "※"
const val outdatedWarnTips = "未更新数据"
const val oldDataTag = "旧"
const val spaceTag = "　"

val houseTipsGroup = setOf<Long>(
	1074761017,//海猫房群
	299803462,//琥珀房群
)

//region 缓存路径
val cacheDir = File("${WannaHomeKt.dataFolderPath}${File.separatorChar}okhttpCache")
val imageDir = File("${WannaHomeKt.dataFolderPath}${File.separatorChar}map")
val client = OkHttpClient.Builder()
	.cache(Cache(cacheDir, 100 * 1024 * 1024))
	.connectTimeout(10, TimeUnit.SECONDS)
	.build()

//endregion
var font: Font? = null

object WannaHomeKt : KotlinPlugin(
	JvmPluginDescription(id = "cn.status102.WannaHome-kt", name = "WannaHome-kt", version = "0.2.10")
	{ author("status102") }
) {
	override fun onEnable() {
		Config.reload()
		Data.reload()
		//CommandManager.registerCommand(WannaCommand)
		ServerListCommand.register()
		TerritoryCommand.register()
		WannaCommand.register()
		SubNameCommand.register()
		TestCommand.register()
		MapCommand.register()

		WannaHomeKt::class.java.getResourceAsStream("/Microsoft_YaHei_UI_Light.ttf")?.use {
			val dataFile = File(WannaHomeKt.dataFolderPath.toFile(), "Microsoft_YaHei_UI_Light.ttf")
			dataFile.writeBytes(it.readAllBytes())
			logger.info { "加载附带字体：" + File(WannaHomeKt.dataFolderPath.toFile(), "Microsoft_YaHei_UI_Light.ttf").toString() }
			font = Font(Typeface.makeFromFile(File(WannaHomeKt.dataFolderPath.toFile(), "Microsoft_YaHei_UI_Light.ttf").toString()))
		}

		logger.info { "Plugin loaded" }
		val eventChannel = GlobalEventChannel.parentScope(this)

		eventChannel.subscribeAlways<NewFriendRequestEvent> {
			if (Config.acceptFriendRequest) {
				accept()
				delay(random().nextLong(5_000, 30_000))
				bot.getFriend(Config.owner)?.sendMessage(String.format("%s 收到<%s[%d]>发来的好友申请，已同意好友申请。申请留言：%s", strTime(), fromNick, fromId, message))
			} else
				bot.getFriend(Config.owner)?.sendMessage(String.format("%s 收到<%s[%d]>发来的好友申请，申请留言：%s", strTime(), fromNick, fromId, message))
		}
		eventChannel.subscribeAlways<BotInvitedJoinGroupRequestEvent> {
			if (Config.acceptGroupRequest) {
				accept()
				delay(random().nextLong(5_000, 30_000))
				bot.getFriend(Config.owner)?.sendMessage(String.format("%s <%s[%d]>邀请加入<%s[%d]>，已同意加群邀请", strTime(), invitorNick, invitorId, groupName, groupId))
			} else
				bot.getFriend(Config.owner)?.sendMessage(String.format("%s <%s[%d]>邀请加入<%s[%d]>", strTime(), invitorNick, invitorId, groupName, groupId))
		}
		//处理BOT退群消息，包括被踢、解散、主动退出
		eventChannel.subscribeAlways<BotLeaveEvent> {
			if (this is BotLeaveEvent.Kick) {
				bot.getFriend(Config.owner)?.sendMessage(String.format("%s Bot被<%s[%d]>踢出了<%s[%d]>", strTime(), operator.remarkOrNameCardOrNick, operator.id, group.name, group.id))
			} else {
				bot.getFriend(Config.owner)?.sendMessage(String.format("%s Bot退出了<%s[%d]>群", strTime(), group.name, group.id))
			}
		}
		//处理BOT被禁言
		eventChannel.subscribeAlways<BotMuteEvent> {
			val day = durationSeconds / 24 / 3600
			bot.getFriend(Config.owner)?.sendMessage(String.format("%s <%s[%d]>在<%s[%d]>群禁言了bot，时长：%s%d:%02d:%02d", strTime(), operator.remarkOrNameCardOrNick, operator.id, group.name, group.id, if (day == 0) "" else "$day day ", (durationSeconds / 3600) % 24, (durationSeconds / 60) % 60, durationSeconds % 60))
		}
		eventChannel.subscribeAlways<BotUnmuteEvent> {
			bot.getFriend(Config.owner)?.sendMessage(String.format("%s <%s[%d]>在<%s[%d]>群取消禁言了bot", strTime(), operator.nameCardOrNick, operator.id, group.name, group.id))
		}

		eventChannel.subscribeAlways<FriendMessageEvent> {
			if (Config.owner < 1) {
				sender.sendMessage("未配置号主")
			} else if (sender.id != Config.owner) {
				sender.sendMessage("您不是号主")
			} else {
				message.contentsList().forEach {
					if (it is PlainText) {
						if (it.content.startsWith("/quit ")) {
							val result = Regex("(?<=/quit )\\d+").find(it.content)?.value
							if (result == null) {
								sender.sendMessage("未输入有效群号")
							} else if (bot.getGroup(result.toLong()) == null) {
								sender.sendMessage("未找到要退出的群：[$result]")
							} else {
								val groupName = bot.getGroup(result.toLong())!!.name
								if (!bot.getGroup(result.toLong())!!.quit())
									sender.sendMessage("退出群聊<$groupName[$result]>失败")
								//else
								//sender.sendMessage("退出群聊<$groupName[$result]>成功")
								//退出群聊成功的合并到bot离开群聊的消息推送
							}
						}
						if (it.content.startsWith("/delete ")) {
							val result = Regex("(?<=/delete )\\d+").find(it.content)?.value
							if (result == null) {
								sender.sendMessage("未输入有效好友QQ号")
							} else if (bot.getFriend(result.toLong()) == null) {
								sender.sendMessage("未找到要删除的好友：[$result]")
							} else {
								val friendName = bot.getFriend(result.toLong())!!.remarkOrNick
								bot.getFriend(result.toLong())!!.delete()
								sender.sendMessage("退出群聊<$friendName[$result]>成功")
							}
						}
					}
				}
			}
		}
		eventChannel.subscribeAlways<MessageEvent> {
			message.contentsList().forEach {
				if (it is PlainText) {
					if (it.content == "/空地") {
						WannaCommand.handle(Cont(this.message, toCommandSender()))
					} else if (it.content == "/空地 简称") {
						WannaCommand.sendServerNickName(Cont(this.message, toCommandSender()))
					} else if (it.content.startsWith("/空地 ")) {
						getEmptyPlace(Cont(this.message, toCommandSender()), it.content.substring(4))
					}
				}
			}
		}
		initNote()
	}

	override fun onDisable() {
		Config.save()
		Data.save()
		MapCommand.unregister()
		ServerListCommand.unregister()
		TerritoryCommand.unregister()
		WannaCommand.unregister()
		SubNameCommand.unregister()
		TestCommand.unregister()
		super.onDisable()
	}
}

suspend fun getEmptyPlace(commandContext: CommandContext, ss: String) {
	val word = ss.split(' ')
	val regex = Regex((serverMap.keys + subNameMap.keys + serverIdMap.keys).joinTo(StringBuilder(), "|").toString())
	val serverList = mutableListOf<String>()
	val limitStr = StringBuilder()
	word.forEach { str ->
		if ((serverMap.keys + subNameMap.keys + serverIdMap.keys).contains(str)) {
			serverList.add(str)
		} else if (regex.containsMatchIn(str)) {
			val result = regex.findAll(str)
			result.forEach {
				serverList.add(it.value)
			}
			limitStr.append(str.replace(regex, ""))
		} else {
			val filter = serverIdMap.keys.filter { it.contains(str) }
			if (ss.length >= 2 && filter.size == 1) {
				if (commandContext.sender.isNotConsole())
					commandContext.sender.sendMessage(commandContext.originalMessage.quote() + "未知服务器，推测为<${filter[0]}>，正在获取数据中，请耐心等待")
				else
					println("未知服务器，推测为<${filter[0]}>，正在获取数据中，请耐心等待")
				serverList.add(filter[0])
			} else
				limitStr.append(str)
		}
	}
	try {
		WannaCommand.handle(commandContext, serverList, limitStr.toString())
	} catch (e: Exception) {
		WannaHomeKt.logger.error { "解析执行错误：${e}\n${e.stackTraceToString()}" }
		if (commandContext.sender.isNotConsole())
			commandContext.sender.sendMessage(commandContext.originalMessage.quote() + "解析执行错误：${e}")

	}
}

class Cont(override val originalMessage: MessageChain, override val sender: CommandSender) : CommandContext

/*
fun pornhub(porn: String = "Porn", hub: String = "Hub"): Surface {
	val font = Font(FontUtils.matchArial(FontStyle.BOLD), 90F)
	val prefix = TextLine.make(porn, font)
	val suffix = TextLine.make(hub, font)
	val black = Paint().setARGB(0xFF, 0x00, 0x00, 0x00)
	val white = Paint().setARGB(0xFF, 0xFF, 0xFF, 0xFF)
	val yellow = Paint().setARGB(0xFF, 0xFF, 0x90, 0x00)

	val surface = Surface.makeRasterN32Premul((prefix.width + suffix.width + 50).toInt(), (suffix.height + 40).toInt())
	surface.canvas {
		clear(black.color)
		drawTextLine(prefix, 10F, 20 - font.metrics.ascent, white)
		drawRRect(RRect.makeXYWH(prefix.width + 15, 15F, suffix.width + 20, suffix.height + 10, 10F), yellow)
		drawTextLine(suffix, prefix.width + 25, 20 - font.metrics.ascent, black)
	}

	return surface
}
*/
/**
 * 按[M 海01-05]参与:1,118人（中奖5号）格式标注
 *
 * @param outdatedLimit 过期时间点（秒）
 */
fun trans(nowTimeStamp: Long, showServerName: Boolean, outdatedLimit: Long): (PlotInfo) -> String =
	{ sale ->
		val output = StringBuilder()
		val time = TimeCalculator.getInstance()
		//M字符宽度较长
		val sizeMap: List<String> = listOf("S ", "M", "L ")
		//对有数据
		if (sale.SaleState == 1 && (nowTimeStamp - sale.Update) >= outdatedLimit)
			output.append(outdatedWarnTag)
		if (sale.SaleState == 0 && sale.Update < time.thisTurnStart)
			output.append(oldDataTag)
		output.append(String.format("[%s %s%s%02d-%02d]", sizeMap[sale.Size], if (showServerName) "${serverNameMap[sale.ServerId]?.substring(0, 2)} " else "", territoryMap[sale.TerritoryId], sale.WardId + 1, sale.HouseId + 1))
		if (sale.SaleState in 1..2)
			output.append(String.format(" 参与:%,d人", sale.VoteCount))
		if (sale.SaleState == 2)
			output.append(String.format("(中奖%,d号)", sale.WinnerIndex))
		if (sale.SaleState == 3)
			output.append(" 准备中")
		output.toString()
	}

suspend fun getPic(serverName: String, serverList: List<Int>, groupId: Long = 0, limitStr: String): Surface {
	val time = TimeCalculator.getInstance()
	val now = Calendar.getInstance().timeInMillis / 1000

	val plotInfoMap = mutableMapOf<String, PlotInfo>()

	val channel = WannaCommand.getHouseData(serverList)
	val successCount = MutableList(HouseDataList.size) { 0 }
	withTimeoutOrNull(40_000) {
		repeat(HouseDataList.size * serverList.size) {
			channel.receiveCatching().run {
				if (isSuccess) {
					getOrNull()?.run {
						//WannaHomeKt.logger.info { "${third.size}个：${first::class}" }
						if (second) successCount[HouseDataList.indexOfFirst { it::class == first::class }]++
						third.forEach { (key, it) ->
							//if(it.WardId == 0 && it.HouseId == 1 && it.Size == 1)
							//WannaHomeKt.logger.info{"临时测试：${it.TerritoryId} ${it.WardId + 1}-${it.HouseId + 1} ${it.SaleState}(${it.VoteCount}/${it.WinnerIndex})：${unixTimeToStr(it.Update)}：${this.first.javaClass}"}
							if (plotInfoMap.containsKey(key)) {
								if (plotInfoMap[key]!!.SaleState == 0 ||
									(it.Update > plotInfoMap[key]!!.Update &&
											((it.Update >= time.thisTurnStart && plotInfoMap[key]!!.Update < time.thisTurnStart) || it.SaleState != 0))
								) {
									plotInfoMap[key]!!.VoteCount = it.VoteCount
									plotInfoMap[key]!!.WinnerIndex = it.WinnerIndex
									plotInfoMap[key]!!.SaleState = it.SaleState
									plotInfoMap[key]!!.Update = it.Update
								}
							} else {
								plotInfoMap[key] = it
							}
						}
					}
				} else {
					WannaHomeKt.logger.error { exceptionOrNull()?.message + "\n" + exceptionOrNull()?.stackTraceToString() }
				}
			}
		}
	}
	channel.close()

	var plotList = plotInfoMap.values.toMutableList()
	val showServerName = serverList.size > 1

	val font = Font(font?.typefaceOrDefault)
	font.size = 24f

	val black = Paint().setARGB(0xFF, 0x00, 0x00, 0x00)
	val white = Paint().setARGB(0xFF, 0xFF, 0xFF, 0xFF)
	val yellow = Paint().setARGB(0xFF, 0xFF, 0x90, 0x00)
	val grey = Paint().setARGB(0x70, 0, 0, 0)


	plotList.forEach {
		//将上一轮的[准备中、无人中奖]归入该轮出售
		if ((it.SaleState == 3 || (it.SaleState == 2 && it.WinnerIndex == 0)) && it.Update < time.thisTurnStart) {
			it.Update = time.thisTurnStart
			it.SaleState = 0
		}
		//把无状态的房屋放在最后
		if (it.SaleState == 0) {
			it.VoteCount = -2
			it.WinnerIndex = -2
		} else if (it.SaleState == 3) {
			it.VoteCount = -1
			it.WinnerIndex = -1
		}
	}
	plotList = plotList.filter { it.Update >= time.lastTurnStart && (it.SaleState == 0 || it.Update >= time.thisTurnStart) }.toMutableList()

	//分离部队房和个人房
	var personList = plotList.filter { it.WardId < fcIdStart }
	var fcList = plotList.filter { it.WardId >= fcIdStart }

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
	//排序
	personList = personList.run { asSequence().sortedBy { it.WardId * 60 + it.HouseId }.sortedBy { it.TerritoryId }.sortedBy { it.ServerId }.sortedByDescending { it.VoteCount }.sortedByDescending { it.Size }.toList() }
	fcList = fcList.run { asSequence().sortedBy { it.WardId * 60 + it.HouseId }.sortedBy { it.TerritoryId }.sortedBy { it.ServerId }.sortedByDescending { it.VoteCount }.sortedByDescending { it.Size }.toList() }


	val personLarge = personList.indexOfLast { it.Size > 0 } + 1
	val fcLarge = fcList.indexOfLast { it.Size > 0 } + 1
	var personShow =
		if (personLarge in Limit_Person) personLarge
		else if (personLarge > Limit_Person.last) Limit_Person.last
		else Limit_Person.first
	var fcShow = if (fcLarge in Limit_FC) fcLarge else if (fcLarge > Limit_FC.last) Limit_FC.last else Limit_FC.first

	personShow = maxOf(personShow, fcShow)
	fcShow = maxOf(personShow, fcShow)
	/**
	 * 参与人数过期时间，单位为秒
	 */
	var outdatedLimit: Long
	val outdatedTime: String
	//计算抽奖人数数据过期期限
	if (time.canEntry) {
		outdatedLimit = maxOf(10 * 60.0, minOf(outdatedWarn.toDouble(), (now - time.thisTurnStart) * 0.7, (time.thisTurnShow - now) * 0.7)).toLong()
		outdatedTime = if (outdatedLimit / 60 >= 60) {
			outdatedLimit = (outdatedLimit / 60.0 / 60).roundToLong() * 60 * 60
			"超过${outdatedLimit / 60 / 60}小时"
		} else {
			outdatedLimit = (outdatedLimit / 60.0 / 5).roundToLong() * 5 * 60
			"超过${outdatedLimit / 60}分钟"
		}
	} else {
		outdatedLimit = now - time.thisTurnShow
		outdatedTime = "公布后"
	}

	val personTextList = personList.map(trans(now, showServerName, outdatedLimit)).take(personShow)
	val fcTextList = fcList.map(trans(now, showServerName, outdatedLimit)).take(fcShow)
	// 当前块是否存在前缀
	val hasPersonPrefix = personTextList.any { it.startsWith(outdatedWarnTag) || it.startsWith(oldDataTag) }
	val hasFcPrefix = fcTextList.any { it.startsWith(outdatedWarnTag) || it.startsWith(oldDataTag) }

	val title = (String.format("[第%s轮第%s天-%s中]<%s>%s：", time.turn, time.diff % 9 + 1, if (time.canEntry) "参与" else "公示", serverName, limitStr.uppercase(Locale.getDefault())))
	val outputTitle = mutableListOf<String>()
	if (personList.isEmpty() && fcTextList.isEmpty()) {
		outputTitle.add(title)
		outputTitle.add("无空地 / 无上传数据")
	} else {
		outputTitle.add("${title}${personList.size + fcList.size}处")
	}
	val textTitle = outputTitle.map { Pair(TextLine.make(it, font), black) }


	// 个人房数据，String转TextLine
	val textPerson = personTextList.map {
		if (it.startsWith(outdatedWarnTag) || it.startsWith(oldDataTag))
			Pair(TextLine.make(it, font), grey)
		else if (hasPersonPrefix)
			Pair(TextLine.make(spaceTag + it, font), black)
		else
			Pair(TextLine.make(it, font), black)
	}.toMutableList()
	//添加标头
	if (!limitStr.contains("个人") && personList.isNotEmpty()) {
		val personSizeStr = mutableListOf<String>().run {
			if (personList.any { it.Size == 0 }) add(String.format("S %,d", personList.filter { it.Size == 0 }.size))
			if (personList.any { it.Size == 1 }) add(String.format("M %,d", personList.filter { it.Size == 1 }.size))
			if (personList.any { it.Size == 2 }) add(String.format("L %,d", personList.filter { it.Size == 2 }.size))
			joinTo(StringBuilder(), "/", "(", ")")
		}
		if (hasPersonPrefix)
			textPerson.add(0, Pair(TextLine.make(String.format("${spaceTag}个人：%,d %s", personList.size, personSizeStr), font), black))
		else
			textPerson.add(0, Pair(TextLine.make(String.format("个人：%,d %s", personList.size, personSizeStr), font), black))
	}


	// 部队房数据，String转TextLine
	val textFc = fcTextList.map {
		if (it.startsWith(outdatedWarnTag) || it.startsWith(oldDataTag))
			Pair(TextLine.make(it, font), grey)
		else if (hasFcPrefix)
			Pair(TextLine.make(spaceTag + it, font), black)
		else
			Pair(TextLine.make(it, font), black)
	}.toMutableList()
	//添加标头
	if (!limitStr.contains("部队") && fcList.isNotEmpty()) {
		val fcSizeStr = mutableListOf<String>().run {
			if (fcList.any { it.Size == 0 }) add(String.format("S %,d", fcList.count { it.Size == 0 }))
			if (fcList.any { it.Size == 1 }) add(String.format("M %,d", fcList.count { it.Size == 1 }))
			if (fcList.any { it.Size == 2 }) add(String.format("L %,d", fcList.count { it.Size == 2 }))
			joinTo(StringBuilder(), "/", "(", ")")
		}
		if (hasFcPrefix)
			textFc.add(0, Pair(TextLine.make(String.format("${spaceTag}部队：%,d %s", fcList.size, fcSizeStr), font), black))
		else
			textFc.add(0, Pair(TextLine.make(String.format("部队：%,d %s", fcList.size, fcSizeStr), font), black))
	}


	val outputBottom = mutableListOf<String>()
	outputBottom.add("")
	//追加抽奖人数数据过期提醒
	if ((personTextList + fcTextList).any { it.contains(outdatedWarnTag) }) outputBottom.add(outdatedWarnTag + outdatedTime + outdatedWarnTips)
	if (personList.size > personTextList.size || fcList.size > fcTextList.size) {
		if (houseTipsGroup.contains(groupId)) {
			outputBottom.add("更多空地请查阅群公告中网站")
			outputBottom.add("数据上传方式使用“/空地”查询数据源网站，并获取对应上传方式")
		} else
			outputBottom.add("更多空地及数据上传方式使用“/空地”查询")
	} else
		outputBottom.add("数据上传方式使用“/空地”查看数据源网站")
	//多服务器查询时，打印每个服务器的数量
	if (showServerName) {
		val plotListPerServer = plotList.groupBy { it.ServerId }.mapKeys { serverNameMap[it.key]!! }.mapValues { pair ->
			listOf(pair.value.count { it.Size == 0 }, pair.value.count { it.Size == 1 }, pair.value.count { it.Size == 2 })
		}.toList()
			.sortedByDescending { it.second[0] }.sortedByDescending { it.second[1] }.sortedByDescending { it.second[2] }

		val houseNumAllServer = mutableListOf<String>()
		plotListPerServer
			.forEach { pair ->
				val perServer = mutableListOf<String>()
				if (pair.second[0] > 0) perServer.add("S ${pair.second[0]}")
				if (pair.second[1] > 0) perServer.add("M ${pair.second[1]}")
				if (pair.second[2] > 0) perServer.add("L ${pair.second[2]}")
				houseNumAllServer.add(perServer.joinTo(StringBuilder(), "/", "${pair.first.take(2)}(", ")").toString())
			}
		outputBottom.addAll(houseNumAllServer.chunked(4) { it.joinTo(StringBuilder(), ", ").toString() })
	}
	//如果有请求失败
	if (successCount.any { it != serverList.size }) {
		val str = StringBuilder()
		str.appendLine("数据源连接状态：")
		HouseDataList.forEachIndexed { index, voteInfoOperate ->
			if (index != 0) str.append(", ")
			str.append(String.format("%s[%,d/%,d]", voteInfoOperate.sourceName, successCount[index], serverList.size))
		}
		outputBottom.add(str.toString())
	}
	while (outputBottom.lastOrNull()?.isEmpty() == true)
		outputBottom.removeLast()
	val textBottom = outputBottom.map { if (it.startsWith(outdatedWarnTag)) Pair(TextLine.make(it, font), grey) else Pair(TextLine.make(it, font), black) }

	val base = if (textPerson.isEmpty()) 0F else textPerson.maxOfOrNull { it.first.width }!! + Merge_Mid

	val height = font.metrics.height * (outputTitle.size + maxOf(textPerson.size, textFc.size) + outputBottom.size) + Merge_Up + Merge_Down
	val width = Merge_Left + maxOf(textTitle.maxOfOrNull { it.first.width } ?: 0F, base + (textFc.maxOfOrNull { it.first.width } ?: 0F), textBottom.maxOfOrNull { it.first.width } ?: 0F) + Merge_Right

	val surface = Surface.makeRasterN32Premul(width.toInt(), height.toInt())
	surface.canvas.run {
		clear(white.color)

		for ((i, it) in textTitle.withIndex()) {
			drawTextLine(it.first, Merge_Left, i * font.metrics.height + 10 - font.metrics.ascent, it.second)
		}

		for ((i, it) in textPerson.withIndex()) {
			drawTextLine(it.first, Merge_Left, outputTitle.size * font.metrics.height + i * font.metrics.height + 10 - font.metrics.ascent, it.second)
		}
		for ((i, it) in textFc.withIndex()) {
			drawTextLine(it.first, Merge_Left + base, outputTitle.size * font.metrics.height + i * font.metrics.height + 10 - font.metrics.ascent, it.second)
		}

		for ((i, it) in textBottom.withIndex()) {
			drawTextLine(it.first, Merge_Left, (outputTitle.size + maxOf(textPerson.size, textFc.size) + i) * font.metrics.height + 10 - font.metrics.ascent, it.second)
		}
	}
	if (groupId == 0L) {
		surface.makeImageSnapshot().encodeToData()?.use {
			WannaHomeKt.logger.info { "未设置输出群号，导出至Data文件夹tmp.png，大小：${it.size}" }
			File(WannaHomeKt.dataFolderPath.toString(), "tmp.png").writeBytes(it.bytes)
		}
	}
	return surface
}


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
		output.add("By status102")

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
		if (str.isEmpty() || (!serverIdMap.containsKey(str) && !subNameMap.containsKey(str) && !serverMap.containsKey(str))) {
			commandContext.sender.sendMessage(commandContext.originalMessage.quote() + "<${str}>服务器/大区不存在")
			return
		}
		var serverName = str
		if (subNameMap.containsKey(serverName)) serverName = subNameMap[serverName].toString()
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
		if (serverList.isEmpty() || !serverList.all { serverIdMap.containsKey(it) || subNameMap.containsKey(it) || serverMap.containsKey(it) }) {
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
		return if ((serverMap.keys + subNameMap.keys).contains(name)) {
			val list = mutableListOf<String>()
			(serverMap + subNameMap.map { Pair(it.key, listOf(it.value)) })[name]!!
				.forEach {
					list.addAll(serverOrDcNickNameToServerName(it))
				}
			list
		} else
			mutableListOf(name)
	}

	suspend fun sendServerNickName(commandContext: CommandContext) {
		val nameList: MutableMap<String, MutableList<String>> = mutableMapOf()
		subNameMap.forEach { (t, u) ->
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

	@OptIn(DelicateCoroutinesApi::class)
	suspend fun getHouseData(serverIdList: List<Int>): Channel<Triple<IVoteInfoOperate, Boolean, Map<String, PlotInfo>>> {
		val channel = Channel<Triple<IVoteInfoOperate, Boolean, Map<String, PlotInfo>>>(Channel.UNLIMITED)

		GlobalScope.launch {
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


fun diffTimeToStr(diff: Long, showMillis: Boolean = true, showDays: Boolean = false): String {
	val diffSec = diff / 1000
	val left = if (showDays)
		String.format("%,d日 %d:%02d:%02d", diffSec / 24 / 3600, (diffSec / 3600) % 24, (diffSec / 60) % 60, diffSec % 60)
	else
		String.format("%,d:%02d:%02d", diffSec / 3600, (diffSec / 60) % 60, diffSec % 60)
	if (showMillis)
		return String.format("${left}.%03d", diff % 1000)
	return left
}
