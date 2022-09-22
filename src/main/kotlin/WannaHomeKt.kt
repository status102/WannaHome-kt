package cn.status102

import cn.status102.Config.subNameMap
import cn.status102.data.HouseInfo
import cn.status102.data.PlotInfo
import cn.status102.data.VoteInfoCha
import cn.status102.data.VoteInfoHouseHelper
import io.ktor.utils.io.charsets.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import net.mamoe.mirai.Bot
import net.mamoe.mirai.console.command.*
import net.mamoe.mirai.console.command.CommandManager.INSTANCE.register
import net.mamoe.mirai.console.command.CommandManager.INSTANCE.unregister
import net.mamoe.mirai.console.command.CommandSender.Companion.toCommandSender
import net.mamoe.mirai.console.data.AutoSavePluginConfig
import net.mamoe.mirai.console.data.AutoSavePluginData
import net.mamoe.mirai.console.data.value
import net.mamoe.mirai.console.plugin.jvm.JvmPluginDescription
import net.mamoe.mirai.console.plugin.jvm.KotlinPlugin
import net.mamoe.mirai.contact.Group
import net.mamoe.mirai.contact.MessageTooLargeException
import net.mamoe.mirai.contact.nameCardOrNick
import net.mamoe.mirai.contact.remarkOrNameCardOrNick
import net.mamoe.mirai.event.GlobalEventChannel
import net.mamoe.mirai.event.events.*
import net.mamoe.mirai.message.data.MessageChain
import net.mamoe.mirai.message.data.MessageSource.Key.quote
import net.mamoe.mirai.message.data.PlainText
import net.mamoe.mirai.message.data.toMessageChain
import net.mamoe.mirai.utils.ExternalResource.Companion.toExternalResource
import net.mamoe.mirai.utils.ExternalResource.Companion.uploadAsImage
import net.mamoe.mirai.utils.error
import net.mamoe.mirai.utils.info
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jetbrains.skia.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.set
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToLong
import kotlin.text.Charsets


public const val Limit_Person = 7
public const val Limit_FC = 5
public const val fcIdStart = 16//个人区1~16，对应ID 0~15

public val HouseDataList = listOf(HouseInfo(), VoteInfoCha(), VoteInfoHouseHelper())

/**
 * 超过8小时数据未更新
 */
public const val outdatedWarn: Long = 8 * 60 * 60//
public const val outdatedWarnChar = "※"
public const val outdatedWarnTips = "未更新数据"

public val showTipsGroup = setOf<Long>(
	1074761017,//海猫房群
	299803462,//琥珀房群
)

//region 缓存路径
public val cacheDir = File("${WannaHomeKt.dataFolderPath}${File.separatorChar}okhttpCache")
public val imageDir = File("${WannaHomeKt.dataFolderPath}${File.separatorChar}map")
public val client = OkHttpClient.Builder().cache(Cache(cacheDir, 100 * 1024 * 1024)).connectTimeout(30, TimeUnit.SECONDS).build()

//endregion

public object WannaHomeKt : KotlinPlugin(
	JvmPluginDescription(id = "cn.status102.WannaHome-kt", name = "WannaHome-kt", version = "0.1.1")
	{
		author("status102")
	}
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

		WannaHomeKt::class.java.getResourceAsStream("/Microsoft_YaHei_UI_Light.tff").use {
			if(it == null)
				println("null！！！！")
			else
			logger.info { "字体大小：" + it.readAllBytes().size }
		}
		logger.info { "Plugin loaded" }
		val eventChannel = GlobalEventChannel.parentScope(this)

		eventChannel.subscribeAlways<NewFriendRequestEvent> {
			this.bot.getFriend(3122262428)?.sendMessage("${fromNick}[${fromId}]发来的好友申请：${message}")
			//自动同意好友申请
			/*Bot.instances.forEach {
				it.getFriend(3122262428)?.sendMessage("${fromNick}[${fromId}]发来的好友申请：${message}")
			}*/
		}
		eventChannel.subscribeAlways<BotInvitedJoinGroupRequestEvent> {
			this.bot.getFriend(3122262428)?.sendMessage("${invitorNick}[${invitorId}]邀请加入${this.groupName}[${this.groupId}]")
			//自动同意加群申请
			/*Bot.instances.forEach {
				it.getFriend(3122262428)?.sendMessage("${invitorNick}[${invitorId}]邀请加入${this.groupName}[${this.groupId}]")
			}*/
		}
		eventChannel.subscribeAlways<BotLeaveEvent> {
			if (this is BotLeaveEvent.Kick) {
				this.bot.getFriend(3122262428)?.sendMessage("Bot被${operator.remarkOrNameCardOrNick}[${operator.id}]踢出了${group.name}[${group.id}]")
				//自动同意加群申请
				/*Bot.instances.forEach {
					it.getFriend(3122262428)?.sendMessage("Bot被${operator.remarkOrNameCardOrNick}[${operator.id}]踢出了${group.name}[${group.id}]")
				}*/
			}
		}
		eventChannel.subscribeAlways<BotMuteEvent> {
			this.bot.getFriend(3122262428)?.sendMessage("${operator.remarkOrNameCardOrNick}[${operator.id}]在${group.name}[${group.id}]禁言了bot，时长：" + String.format("%d day %d:%02d:%02d", durationSeconds / 24 / 3600, (durationSeconds / 3600) % 24, (durationSeconds / 60) % 60, durationSeconds % 60))

			//BOT被禁言
			/*Bot.instances.forEach {
				it.getFriend(3122262428)?.sendMessage("${operator.remarkOrNameCardOrNick}[${operator.id}]在${group.name}[${group.id}]禁言了bot，时长：" + String.format("%d day %d:%02d:%02d", durationSeconds / 24 / 3600, (durationSeconds / 3600) % 24, (durationSeconds / 60) % 60, durationSeconds % 60))
			}*/
		}
		eventChannel.subscribeAlways<BotUnmuteEvent> {
			this.bot.getFriend(3122262428)?.sendMessage("${operator.nameCardOrNick}[${operator.id}]在${group.name}[${group.id}]取消禁言了bot")
			//BOT被取消禁言
			/*Bot.instances.forEach {
				it.getFriend(3122262428)?.sendMessage("${operator.nameCardOrNick}[${operator.id}]在${group.name}[${group.id}]取消禁言了bot")
			}*/
		}
		eventChannel.subscribeAlways<GroupMessageEvent> {
			//BOT被取消禁言
			this.message.forEach {
				if (it is PlainText) {
					if (it.content == "/空地") {
						WannaCommand.handle(Cont(this.message, toCommandSender()))
					} else if (it.content == "/空地 简称") {
						WannaCommand.sendServerNickName(Cont(this.message, toCommandSender()))
					} else if (it.content.startsWith("/空地 ")) {
						val word = it.content.substring(4).split(" ")
						val regex = Regex((serverMap.keys + subNameMap.keys).joinTo(StringBuilder(), "|").toString())
						val serverList = mutableListOf<String>()
						val limitStr = StringBuilder()
						word.forEach { str ->
							if (regex.containsMatchIn(str)) {
								val result = regex.findAll(str)
								result.forEach {
									serverList.add(it.value)
								}
								limitStr.append(regex.replace(str, ""))
							} else
								limitStr.append(regex.replace(str, ""))
						}
						WannaCommand.handle(Cont(this.message, toCommandSender()), serverList, limitStr.toString())
					}
				}
			}
		}
		initSendNote()
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

public class Cont(override val originalMessage: MessageChain, override val sender: CommandSender) : CommandContext

/*
public fun pornhub(porn: String = "Porn", hub: String = "Hub"): Surface {
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
public object TestCommand : SimpleCommand(
	WannaHomeKt, "wh", "WannaHome", description = "示例指令"
) {
	@Handler
	public suspend fun handle(commandContext: CommandContext) {

		val t = "测试输出"
		val utf8: String = String(t.toByteArray(charset("UTF-8")))
		println(utf8)
		val unicode = String(utf8.toByteArray(), Charsets.UTF_8)
		println(unicode)
		val gbk = String(unicode.toByteArray(charset("GBK")))
		val bitmap = Bitmap().apply {
			allocN32Pixels(120, 180)

		}
		val font = Font(FontMgr.default.matchFamilyStyle("宋体", FontStyle.NORMAL))
		//val font = Font(Typeface.makeFromName("SimSun", FontStyle.NORMAL))
		font.size = 12f

		Typeface.makeFromName("SimSun", FontStyle.NORMAL).familyNames.forEach {
			WannaHomeKt.logger.info { "字符集：${it.name}<${it.language}>" }
		}
		val prefix = TextLine.make(unicode, font)
		val suffix = TextLine.make("(TestOutput)", font)
		val surface = Surface.makeRasterN32Premul((prefix.width + suffix.width + 50).toInt(), (suffix.height + 40).toInt())
		//val surface = Surface()
		surface.canvas.run {
			val black = Paint().setARGB(0xFF, 0x00, 0x00, 0x00)
			val white = Paint().setARGB(0xFF, 0xFF, 0xFF, 0xFF)
			val yellow = Paint().setARGB(0xFF, 0xFF, 0x90, 0x00)
			clear(black.color)
			drawTextLine(prefix, 10F, 20 - font.metrics.ascent, white)
			drawRRect(RRect.makeXYWH(prefix.width + 15, 15F, suffix.width + 20, suffix.height + 10, 5F), yellow)
			drawTextLine(suffix, prefix.width + 25, 20 - font.metrics.ascent, black)

		}
		surface.makeImageSnapshot().encodeToData()?.use {
			//File("C:\\Users\\status102\\Desktop\\latest.png").writeBytes(it.bytes)
		}
		surface.makeImageSnapshot().encodeToData()?.use {
			WannaHomeKt.logger.info { "大小：${it.size}" }
			it.bytes.inputStream().toExternalResource().use {
				if (commandContext.sender.subject != null) {
					val image = it.uploadAsImage(commandContext.sender.subject!!)
					if (commandContext.sender.isNotConsole())
						commandContext.sender.sendMessage(commandContext.originalMessage.quote() + image)
					else
						commandContext.sender.sendMessage("控制台：" + image.toMessageChain().contentToString())
				}
			}
		}

		val canvas = Canvas(bitmap)
		canvas.drawString("测试输出(test)", 10.0f, 30f, font, Paint().setARGB(255, 255, 0, 0))
		Image.makeFromBitmap(bitmap).encodeToData()?.use {
			WannaHomeKt.logger.info { "大小：${it.size}" }
			it.bytes.inputStream().toExternalResource().use {
				if (commandContext.sender.subject != null) {
					val image = it.uploadAsImage(commandContext.sender.subject!!)
					if (commandContext.sender.isNotConsole())
						commandContext.sender.sendMessage(commandContext.originalMessage.quote() + image)
					else
						commandContext.sender.sendMessage("控制台：" + image.toMessageChain().contentToString())
				}
			}
		}
		canvas.close()
		bitmap.close()

		WannaHomeKt.logger.info { "默认字符集：" + Charset.defaultCharset() }
		//println((serverMap.keys + subNameMap.keys).joinTo(StringBuilder(), "|"))
		//WannaCommand.handle(Cont(this.message, toCommandSender()), serverList, limitStr.toString())

		//val str = StringBuilder()
		//sendMessage(str.toString().trimEnd('\n'))
	}
}

public object SubNameCommand : CompositeCommand(
	WannaHomeKt, "服务器简称", "简称", "缩写"
) {
	@SubCommand("list")
	suspend fun CommandSender.handle() { // 函数名随意, 但参数需要按顺序放置.
		val str = StringBuilder()
		for (pair in subNameMap) {
			str.appendLine("<${pair.key}, ${pair.value}>")
		}
		sendMessage(str.toString())
	}

	@SubCommand("del")
	suspend fun CommandSender.handle(key: String) {
		if (subNameMap.containsKey(key)) {
			val subName = subNameMap[key]
			subNameMap.remove(key)
			sendMessage("成功删除<${subName}>的别名：$key")
		} else
			sendMessage("别名<$key>不存在")
	}

	@SubCommand("add")
	suspend fun CommandSender.handle(key: String, value: String) {
		if (subNameMap.containsKey(key))
			sendMessage("别名<${key}>存在")
		else {
			subNameMap[key] = value
			sendMessage("添加<${value}>的别名：${key} 成功")
		}
	}
}

public object ServerListCommand : CompositeCommand(
	WannaHomeKt, "服务器列表"
) {
	@SubCommand("list")
	suspend fun CommandSender.handle() { // 函数名随意, 但参数需要按顺序放置.
		val str = StringBuilder()
		for (pair in serverIdMap) {
			str.appendLine("<${pair.key}, ${pair.value}>")
		}
		sendMessage(str.toString())
	}
}

public object TerritoryCommand : CompositeCommand(
	WannaHomeKt, "房区",
) {
	@SubCommand("list")
	suspend fun CommandSender.handle() { // 函数名随意, 但参数需要按顺序放置.
		val str = StringBuilder()
		for (pair in territoryMap) {
			str.appendLine("<${pair.key}, ${pair.value}>")
		}
		sendMessage(str.toString())
	}
}

public object WannaCommand : SimpleCommand(
	WannaHomeKt, "空房", description = "获取服务器空余地块"
) {
	@Handler
	suspend fun handle(commandContext: CommandContext) {
		val now = Calendar.getInstance().time.time
		val start = Calendar.getInstance().apply { time = strTimeToDate("2022-08-08 23:00:00") }

		val diff = floor((now - start.time.time) / 1000 / 60.0 / 60 / 24).toInt()
		val turn = diff / 9 + 1
		val canEntry = (diff % 9) < 5
		val nextEventTime = (start.clone() as Calendar).apply {
			if (canEntry)
				add(Calendar.DAY_OF_MONTH, ((turn - 1) * 9 + 5))
			else
				add(Calendar.DAY_OF_MONTH, turn * 9)
		}
		val thisTurnStart = (start.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, (turn - 1) * 9) }
		val nextTurnStart = (start.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, turn * 9) }
		val nextEventTimeStr = String.format("%d号%02d:%02d", nextEventTime.get(Calendar.DAY_OF_MONTH), nextEventTime.get(Calendar.HOUR_OF_DAY), nextEventTime.get(Calendar.MINUTE))

		val output = StringBuilder()
		output.appendLine("使用方法：")
		output.appendLine("/空地：获取基本使用方法")
		output.appendLine("/空地 <服务器/大区名/SML|海|森|白|沙|雪|个人|部队|准备>：获取服务器空地信息")
		output.appendLine("/空地 简称：获取服务器简称列表")
		output.appendLine()
		output.appendLine(String.format("今日为第%d轮%d天", turn, diff % 9 + 1))
		if (!canEntry)
			output.appendLine(String.format("下轮参与时间：%s点~%s点", unixTimeToStr(nextTurnStart.timeInMillis, true).substring(5, 13), unixTimeToStr((nextTurnStart.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, 5) }.timeInMillis, true).substring(5, 13)))
		else
			output.appendLine(String.format("公示时间：%s点~%s点", unixTimeToStr(nextEventTime.timeInMillis, true).substring(5, 13), unixTimeToStr(nextTurnStart.timeInMillis, true).substring(5, 13)))
		output.appendLine()
		output.appendLine("感谢提供数据支持：")
		output.appendLine("Cettiidae/猹")
		output.appendLine("冰音：https://wanahome.ffxiv.bingyin.org/")
		output.appendLine("Jim：https://house.ffxiv.cyou/")
		output.appendLine(
			"平均延迟[失败次数/请求量]：" + String.format(
				"%.3fs[%,d/%,d]，%.3fs[%,d/%,d]，%.3fs[%,d/%,d]",
				VoteInfoCha.Companion.Logger.TimeMillis.toDouble() / VoteInfoCha.Companion.Logger.CallTimes / 1000, VoteInfoCha.Companion.Logger.FailTimes, VoteInfoCha.Companion.Logger.CallTimes,
				HouseInfo.Companion.Logger.TimeMillis.toDouble() / HouseInfo.Companion.Logger.CallTimes / 1000, HouseInfo.Companion.Logger.FailTimes, HouseInfo.Companion.Logger.CallTimes,
				VoteInfoHouseHelper.Companion.Logger.TimeMillis.toDouble() / VoteInfoHouseHelper.Companion.Logger.CallTimes / 1000, VoteInfoHouseHelper.Companion.Logger.FailTimes, VoteInfoHouseHelper.Companion.Logger.CallTimes,
			)
		)
		output.appendLine()
		output.appendLine("By status102/依琳娜")

		if (commandContext.sender.isNotConsole())
			commandContext.sender.sendMessage(commandContext.originalMessage.quote() + output.toString().trimEnd('\n'))
		else
			commandContext.sender.sendMessage(output.toString().trimEnd('\n'))
		//commandContext.sender.sendMessage(commandContext.originalMessage.quote() + output.toString().trimEnd('\n'))
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
		val nextEventTimeStr = String.format("%d号%02d点", nextEventTime.get(Calendar.DAY_OF_MONTH), nextEventTime.get(Calendar.HOUR_OF_DAY))
		commandContext.sender.getGroupOrNull()?.run {
			changeBotGroupNameCard(this, canEntry, nextEventTimeStr)
		}
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
	suspend fun handle(commandContext: CommandContext, serverList: List<String>, size: String) {
		if (serverList.size == 1) {
			handle(commandContext, serverList[0], size)
			return
		}
		if (serverList.isEmpty() || !serverList.all { serverIdMap.containsKey(it) || subNameMap.containsKey(it) || serverMap.containsKey(it) }) {
			commandContext.sender.sendMessage(commandContext.originalMessage.quote() + "<${serverList.joinTo(java.lang.StringBuilder())}>服务器/大区不存在")
			return
		}

		//var serverName = str
		//if (subNameMap.containsKey(serverName)) serverName = subNameMap[serverName].toString()
		//val realName = serverName
		//if (serverIdMap.containsKey(serverName)) serverName = serverIdMap[serverName].toString()
		//val realName = "${serverList.size}个服"
		val search = serverList.fold(mutableListOf<Int>()) { sum, element -> (sum + serverOrDcNickNameToServerName(element).map { serverIdMap[it] ?: 0 }.filter { it > 0 }).toMutableList() }.toSet().toList()
		val realName = "${search.size}个服"

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
		val nextEventTimeStr = String.format("%d号%02d点", nextEventTime.get(Calendar.DAY_OF_MONTH), nextEventTime.get(Calendar.HOUR_OF_DAY))
		commandContext.sender.getGroupOrNull()?.run {
			changeBotGroupNameCard(this, canEntry, nextEventTimeStr)
		}
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
	private suspend fun getHouseData(serverIdList: List<Int>, lastTurnStart: Long, thisTurnStart: Long): Channel<Map<String, PlotInfo>> {
		val channel = Channel<Map<String, PlotInfo>>(Channel.UNLIMITED)

		GlobalScope.launch {
			val list = mutableListOf<Job>()
			try {
				serverIdList.forEach { serverId ->
					HouseDataList.forEach {
						list.add(launch { channel.send(it.run(serverId, lastTurnStart, thisTurnStart)) })
					}
				}

				//list.forEach { it.join() }
			} catch (e: Exception) {
				WannaHomeKt.logger.error { "获取网络数据出错：\n${e.stackTraceToString()}" }
				channel.close()
			}
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
			val channel = getHouseData(searchList, lastTurnStart, thisTurnStart)
			val output = java.lang.StringBuilder()
			output.appendLine(String.format("[第%s轮%s日%s]<%s>%s：", turn, diff % 9 + 1, if (canEntry) "参与" else "公示", serverName, limitStr.uppercase(Locale.getDefault())))

			repeat(HouseDataList.size * searchList.size) {
				channel.receive().forEach { (key, it) ->
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
				personList = personList.run { sortedBy { it.WardId * 60 + it.HouseId }.sortedBy { it.TerritoryId }.sortedByDescending { it.Size }.sortedByDescending { it.VoteCount } }
				fcList = fcList.run { sortedBy { it.WardId * 60 + it.HouseId }.sortedBy { it.TerritoryId }.sortedByDescending { it.Size }.sortedByDescending { it.VoteCount } }

				var personShow = min(Limit_Person, personList.size)
				var fcShow = min(Limit_FC, fcList.size)
				if (personShow < Limit_Person) fcShow = Limit_Person + Limit_FC - personShow
				if (fcShow < Limit_FC) personShow = Limit_Person + Limit_FC - fcShow

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
				if (output.contains(outdatedWarnChar))
					output.appendLine(outdatedWarnChar + outdatedTime + outdatedWarnTips)
				if (personList.size + fcList.size > Limit_Person + Limit_FC) {
					if (showTipsGroup.contains(groupId))
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
			//return "获取${serverName}服务器空地为空：from：${fromStr}\ncontent：${content}"
		} catch (e: Exception) {
			WannaHomeKt.logger.error { "获取<${serverName}>数据出错：${e}\n${e.stackTraceToString()}" }
			return "获取<${serverName}>数据出错：${e}"
		}
	}

	private fun printSaleList(saleList: List<PlotInfo>, countLimit: Int, timeStamp: Long, outdatedLimit: Long, showServerName: Boolean = false): String {
		if (saleList.isEmpty())
			return ""
		val output = StringBuilder()
		for ((i, sale) in saleList.withIndex()) {
			if (i < countLimit) {
				if (sale.SaleState == 1 && timeStamp - sale.Update >= outdatedLimit)
					output.append(outdatedWarnChar)
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

public object MapCommand : SimpleCommand(
	WannaHomeKt, "房区地图", "地图", description = "获取房区地图"
) {
	@Handler
	suspend fun handle(commandContext: CommandContext, str: String) {
		if (Map_Url.containsKey(str))
			getPlaceImg(commandContext, str)
		else
			commandContext.sender.sendMessage("房区不存在，支持列表${Map_Url.keys}")
	}

	private suspend fun getPlaceImg(commandContext: CommandContext, place: String) {
		if (!File("").exists()) {
			if (!downTerritoryMap(place))
				commandContext.sender.sendMessage("图片下载失败")
		}
		val imageFile = File("${imageDir}${File.separatorChar}$place")
		imageFile.toExternalResource().use {
			if (commandContext.sender.subject != null) {
				val image = it.uploadAsImage(commandContext.sender.subject!!)
				commandContext.sender.sendMessage(commandContext.originalMessage.quote() + image)
			}
		}
	}

	private fun downTerritoryMap(place: String): Boolean {
		if (!imageDir.exists()) {
			if (!imageDir.mkdir()) {
				println("创建房区地图文件夹失败")
				return false
			}
		}
		val imageFile = File("${imageDir}${File.separatorChar}$place")
		if (!imageFile.exists() && imageFile.canWrite()) {
			println("创建房区地图文件失败")
			return false
		}
		val request = Request.Builder().url(Map_Url[place]!!).get().build()
		val response = client.newCall(request).execute()
		if (response.isSuccessful) {
			response.body?.byteStream()?.use {
				imageFile.outputStream().use { image ->
					it.copyTo(image)
				}
			}
		} else {
			println("下载房区地图文件失败，code=${response.code}")
		}
		response.close()
		return true
	}
}

public fun unixTimeToStr(num: Long, isMillis: Boolean = false): String {
	return SimpleDateFormat("YYYY-MM-dd HH:mm:ss").format(if (isMillis) num else (num * 1000))
}

public fun unixMillisTimeToStr(num: Long): String {
	return SimpleDateFormat("YYYY-MM-dd HH:mm:ss.SSS").format(num)
}

public fun strTimeToDate(str: String): Date {
	return SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(str)
}

@OptIn(DelicateCoroutinesApi::class)
public fun initSendNote() {
	GlobalScope.launch {
		while (true) {
			val now = Calendar.getInstance().timeInMillis
			val start = Calendar.getInstance().apply { time = strTimeToDate("2022-08-08 23:00:00") }

			val diff = floor((Calendar.getInstance().timeInMillis - start.timeInMillis) / 1000 / 60.0 / 60 / 24).toInt()
			val turn = diff / 9 + 1
			val canEntry = (diff % 9) < 5
			val nextEventTime = (start.clone() as Calendar).apply {
				if (canEntry)
					add(Calendar.DAY_OF_MONTH, ((turn - 1) * 9 + 5))
				else
					add(Calendar.DAY_OF_MONTH, turn * 9)
			}
			delay(nextEventTime.timeInMillis - now + 5)
			WannaHomeKt.logger.info { "执行例行公告，下次执行：${unixMillisTimeToStr(nextEventTime.timeInMillis)}" }
			try {
				sendNoteMessage()
			} catch (e: Exception) {
				WannaHomeKt.logger.info { "执行例行提醒出错：\n${e.stackTraceToString()}" }
			}
		}
	}
}

public suspend fun sendNoteMessage() {
	if (Data.WannaHomeGroupList.isEmpty()) return
	val now = Calendar.getInstance().timeInMillis / 1000
	val start = Calendar.getInstance().apply { time = strTimeToDate("2022-08-08 23:00:00") }

	val diff = floor((now - start.timeInMillis / 1000) / 60.0 / 60 / 24).toInt()
	val turn = diff / 9 + 1
	val canEntry = (diff % 9) < 5
	val output = StringBuilder()
	val lastTurnStart = (start.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, (turn - 2) * 9) }
	val thisTurnStart = (start.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, (turn - 1) * 9) }
	val thisTurnShow = (start.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, (turn - 1) * 9 + 5) }
	val nextTurnStart = (start.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, (turn) * 9) }
	val nextEventTime = (start.clone() as Calendar).apply {
		if (canEntry)
			add(Calendar.DAY_OF_MONTH, ((turn - 1) * 9 + 5))
		else
			add(Calendar.DAY_OF_MONTH, turn * 9)
	}
	val nextEventTimeStr = String.format("%d号%02d点", nextEventTime.get(Calendar.DAY_OF_MONTH), nextEventTime.get(Calendar.HOUR_OF_DAY))

	output.appendLine("第${turn}轮${if (canEntry) "摇号" else "公示"}已开始")
	if (!canEntry)
		output.appendLine(String.format("下轮参与时间：%s点~%s点", unixTimeToStr(nextTurnStart.timeInMillis, true).substring(5, 13), unixTimeToStr((nextTurnStart.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, 5) }.timeInMillis, true).substring(5, 13)))
	else
		output.appendLine(String.format("参与时间：%s点~%s点", unixTimeToStr(thisTurnStart.timeInMillis, true).substring(5, 13), unixTimeToStr(thisTurnShow.timeInMillis, true).substring(5, 13)))

	val noteList = mutableMapOf<Long, MutableSet<Long>>()
	synchronized(groupListLock) {
		Data.WannaHomeGroupList.map { it.split('-') }.forEach {
			if (it.size == 2) {
				if (noteList.containsKey(it[0].toLong()))
					noteList[it[0].toLong()]?.add(it[1].toLong())
				else
					noteList[it[0].toLong()] = mutableSetOf(it[1].toLong())
			}
		}
		Data.WannaHomeGroupList.clear()
	}

	Bot.instances.forEach { bot ->
		if (bot.isOnline && noteList.containsKey(bot.id)) {
			noteList[bot.id]?.forEach {
				bot.getGroup(it)?.run {
					changeBotGroupNameCard(this, canEntry, nextEventTimeStr)
					sendMessage(output.toString().trimEnd('\n'))
				}

			}
		}
	}
}

public fun changeBotGroupNameCard(group: Group, canEntry: Boolean, nextEventTimeStr: String) {
	group.botAsMember.apply {
		nameCard = nameCard.replace(Regex("【.+?(】|$)"), "").let { nameCard ->
			if (nameCard.isEmpty() || nameCard.isBlank())
				nick
			else
				nameCard
		} + "【${if (!canEntry) "下轮抽奖" else "本轮公示"}${nextEventTimeStr}】"
	}
}

public fun strTimeToUnix(str: String): Long {
	return strTimeToDate(str).time / 1000
}

public fun strTimeToUnixMillis(str: String): Long {
	return strTimeToDate(str).time
}

public val groupListLock = Any()

public object Data : AutoSavePluginData("Data") {
	public var WannaHomeGroupList by value(mutableSetOf<String>())
}

public object Config : AutoSavePluginConfig("Config") {
	public var subNameMap: MutableMap<String, String> by value(
		mutableMapOf(
			"海猫" to "海猫茶屋",
			"柔风" to "柔风海湾",
			"紫水" to "紫水栈桥",
			"静语" to "静语庄园",
			"鲸鱼" to "静语庄园",
			"琥珀" to "琥珀原",
			"魔都" to "摩杜纳",
			"摩杜" to "摩杜纳",
			"塔" to "水晶塔",
			"茶" to "红茶川",
			"湖" to "银泪湖"
		)
	)
}

//region 房屋种类信息
public val Map_Url: Map<String, String> = mapOf(
	"沙" to "https://huiji-public.huijistatic.com/ff14/uploads/3/31/高脚孤丘房屋等级及尺寸示意图.jpg",
	"沙扩" to "https://huiji-public.huijistatic.com/ff14/uploads/a/a0/高脚孤丘扩建区房屋等级及尺寸示意图.jpg",
	"森" to "https://huiji-public.huijistatic.com/ff14/uploads/0/03/薰衣草苗圃房屋等级及尺寸示意图.jpg",
	"森扩" to "https://huiji-public.huijistatic.com/ff14/uploads/0/07/薰衣草苗圃扩建区房屋等级及尺寸示意图.jpg",
	"海" to "https://huiji-public.huijistatic.com/ff14/uploads/1/14/海雾村房屋等级及尺寸示意图.jpg",
	"海扩" to "https://huiji-public.huijistatic.com/ff14/uploads/7/7b/海雾村扩建区房屋等级及尺寸示意图.jpg",
	"白" to "https://huiji-public.huijistatic.com/ff14/uploads/a/a8/白银乡房屋等级及尺寸示意图update.jpg",
	"白扩" to "https://huiji-public.huijistatic.com/ff14/uploads/0/0b/白银乡扩建区房屋等级及尺寸示意图.jpg",
	"雪" to "https://huiji-public.huijistatic.com/ff14/uploads/0/0b/穹顶皓天房屋等级及尺寸示意图.jpg.png",
	"雪扩" to "https://huiji-public.huijistatic.com/ff14/uploads/c/c4/穹顶皓天扩建区房屋等级及尺寸示意图.png"
)

/**
 * name to ID
 */
public val serverIdMap: Map<String, Int> = mapOf(
	"拉诺西亚" to 1042,
	"幻影群岛" to 1044,
	"萌芽池" to 1060,
	"神意之地" to 1081,
	"红玉海" to 1167,
	"宇宙和音" to 1173,
	"沃仙曦染" to 1174,
	"晨曦王座" to 1175,
	"白金幻象" to 1076,
	"旅人栈桥" to 1113,
	"拂晓之间" to 1121,
	"龙巢神殿" to 1166,
	"潮风亭" to 1170,
	"神拳痕" to 1171,
	"白银乡" to 1172,
	"梦羽宝境" to 1176,
	"紫水栈桥" to 1043,
	"摩杜纳" to 1045,
	"静语庄园" to 1106,
	"延夏" to 1169,
	"海猫茶屋" to 1177,
	"柔风海湾" to 1178,
	"琥珀原" to 1179,
	"银泪湖" to 1183,
	"水晶塔" to 1192,
	"太阳海岸" to 1180,
	"伊修加德" to 1186,
	"红茶川" to 1201
)
public val serverNameMap: Map<Int, String> by lazy {
	serverIdMap.mapValues { it.key }.mapKeys { serverIdMap[it.key]!! }
}
public val serverMap: Map<String, List<String>> = mapOf(
	"陆行鸟" to listOf("鸟"), "莫古力" to listOf("猪"), "猫小胖" to listOf("猫"), "豆豆柴" to listOf("狗"),
	"鸟" to listOf("拉诺西亚", "幻影群岛", "神意之地", "萌芽池", "红玉海", "宇宙和音", "沃仙曦染", "晨曦王座"),
	"猪" to listOf("潮风亭", "神拳痕", "白银乡", "白金幻象", "旅人栈桥", "拂晓之间", "龙巢神殿", "梦羽宝境"),
	"猫" to listOf("紫水栈桥", "延夏", "静语庄园", "摩杜纳", "海猫茶屋", "柔风海湾", "琥珀原"),
	"狗" to listOf("水晶塔", "银泪湖", "太阳海岸", "伊修加德", "红茶川")
)
public val territoryMap: Map<Int, String> = mapOf(339 to "海", 340 to "森", 341 to "沙", 641 to "白", 979 to "雪")
public val sizeMap: List<String> = listOf("S", "M", "L")
//endregion