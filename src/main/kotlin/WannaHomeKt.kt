package cn.status102

import cn.status102.Config.subNameMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import net.mamoe.mirai.console.command.*
import net.mamoe.mirai.console.command.CommandManager.INSTANCE.register
import net.mamoe.mirai.console.command.CommandManager.INSTANCE.unregister
import net.mamoe.mirai.console.data.AutoSavePluginConfig
import net.mamoe.mirai.console.data.value
import net.mamoe.mirai.console.plugin.jvm.JvmPluginDescription
import net.mamoe.mirai.console.plugin.jvm.KotlinPlugin
import net.mamoe.mirai.contact.MessageTooLargeException
import net.mamoe.mirai.message.data.MessageSource.Key.quote
import net.mamoe.mirai.utils.ExternalResource.Companion.toExternalResource
import net.mamoe.mirai.utils.ExternalResource.Companion.uploadAsImage
import net.mamoe.mirai.utils.error
import net.mamoe.mirai.utils.info
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.Request
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

const val Limit_Person = 7
const val Limit_FC = 5
const val fcIdStart = 16//个人区1~16，对应ID 0~15

/**
 * 超过8小时数据未更新
 */
const val outdatedWarn: Long = 8 * 60 * 60//
const val outdatedWarnChar = "※"
const val outdatedWarnTips = "未更新数据"

val showTipsGroup = setOf<Long>(
	1074761017,//海猫房群
	299803462,//琥珀房群
)

//region 缓存路径
val cacheDir = File("${WannaHomeKt.dataFolderPath}${File.separatorChar}okhttpCache")
val imageDir = File("${WannaHomeKt.dataFolderPath}${File.separatorChar}map")
val client = OkHttpClient.Builder().cache(Cache(cacheDir, 100 * 1024 * 1024)).connectTimeout(30, TimeUnit.SECONDS).build()

//endregion
//region 房屋种类信息
val Map_Url = mapOf(
	"沙" to "https://huiji-public.huijistatic.com/ff14/uploads/3/31/高脚孤丘房屋等级及尺寸示意图.jpg",
	"沙扩" to "https://huiji-public.huijistatic.com/ff14/uploads/a/a0/高脚孤丘扩建区房屋等级及尺寸示意图.jpg",
	"森" to "https://huiji-public.huijistatic.com/ff14/uploads/0/03/薰衣草苗圃房屋等级及尺寸示意图.jpg",
	"森扩" to "https://huiji-public.huijistatic.com/ff14/uploads/0/07/薰衣草苗圃扩建区房屋等级及尺寸示意图.jpg",
	"海" to "https://huiji-public.huijistatic.com/ff14/uploads/1/14/海雾村房屋等级及尺寸示意图.jpg",
	"海扩" to "https://huiji-public.huijistatic.com/ff14/uploads/7/7b/海雾村扩建区房屋等级及尺寸示意图.jpg",
	"白" to "https://huiji-public.huijistatic.com/ff14/uploads/a/a8/白银乡房屋等级及尺寸示意图update.jpg",
	"白扩" to "https://huiji-public.huijistatic.com/ff14/uploads/0/0b/白银乡扩建区房屋等级及尺寸示意图.jpg",
	"雪" to "https://huiji-public.huijistatic.com/ff14/uploads/0/0b/穹顶皓天房屋等级及尺寸示意图.jpg.png"
)
val serverMap = mapOf(
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
val territoryMap = mapOf(339 to "海", 340 to "森", 341 to "沙", 641 to "白", 979 to "雪")
val dcMap: Map<String, String> = mapOf("鸟" to "LuXingNiao", "猪" to "MoGuLi", "猫" to "MaoXiaoPang", "狗" to "DouDouChai")
val sizeMap_WannaHome: Map<Int, String> = mapOf(1 to "S", 2 to "M", 3 to "L")
val sizeMap: List<String> = listOf("S", "M", "L")
//endregion

object WannaHomeKt : KotlinPlugin(JvmPluginDescription(id = "cn.status102.WannaHome-kt", name = "WannaHome-kt", version = "0.1.0") { author("status102") }
) {
	override fun onEnable() {
		Config.reload()
		//CommandManager.registerCommand(WannaCommand)
		ServerListCommand.register()
		TerritoryCommand.register()
		WannaCommand.register()
		SubNameCommand.register()
		TestCommand.register()
		MapCommand.register()
		logger.info { "Plugin loaded" }
		/*
		val eventChannel = GlobalEventChannel.parentScope(this)
		Bot.instances.forEach {
			it.getFriend(3122262428)?.sendMessage("")
		}
		eventChannel.subscribeAlways<NewFriendRequestEvent> {
			//自动同意好友申请
			if(bot.id ==)
		}
		eventChannel.subscribeAlways<BotInvitedJoinGroupRequestEvent> {
			//自动同意加群申请
		}*/
	}

	override fun onDisable() {
		MapCommand.unregister()
		ServerListCommand.unregister()
		TerritoryCommand.unregister()
		WannaCommand.unregister()
		SubNameCommand.unregister()
		TestCommand.unregister()
		super.onDisable()
	}
}

object TestCommand : SimpleCommand(
	WannaHomeKt, "WannaHome", description = "示例指令"
) {
	@Handler
	suspend fun CommandSender.handle() {
		val str = StringBuilder()
		str.appendLine("使用方法：")
		str.appendLine("/空地：获取使用方法")
		str.appendLine("/空地 <服务器>：获取服务器空地信息，不显示空地价格，超过15个后S房将被折叠")
		str.appendLine("/空地 <服务器> <SML>：获取服务器的SML类型空地信息，超过30个的数据将被折叠")
		str.appendLine("/[服务器简称|简称|缩写] list：查看已有的服务器简称，可在Config文件配置")
		str.appendLine("/[服务器简称|简称|缩写] del <简称>：删除服务器简称")
		str.appendLine("/[服务器简称|简称|缩写] add <简称> <全名>：为服务器添加简称，例：/简称 add 海猫 海猫茶屋")
		str.appendLine("/房区 list：查看已有的房区，可在Config文件配置")
		str.appendLine("/房区 del <id|名称>：删除房区")
		str.appendLine("/房区 add <id|名称> <名称|id>：增加新房区，例：/房区 add 339 海")
		str.appendLine()
		str.appendLine("获取到单一地块有多个CD数据时，取最长者")
		str.appendLine("感谢 Cettiidae/猹 提供数据支持：home.iinformation.info")
		str.appendLine("感谢 冰音 提供数据支持：https://wanahome.ffxiv.bingyin.org/")
		str.appendLine("By status102/依琳娜")
		sendMessage(str.toString().trimEnd('\n'))
	}
}

object SubNameCommand : CompositeCommand(
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

object ServerListCommand : CompositeCommand(
	WannaHomeKt, "服务器列表"
) {
	@SubCommand("list")
	suspend fun CommandSender.handle() { // 函数名随意, 但参数需要按顺序放置.
		val str = StringBuilder()
		for (pair in serverMap) {
			str.appendLine("<${pair.key}, ${pair.value}>")
		}
		sendMessage(str.toString())
	}
}

object TerritoryCommand : CompositeCommand(
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

object WannaCommand : SimpleCommand(
	WannaHomeKt, "空地", "空房", description = "获取服务器空余地块"
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

		val str = StringBuilder()
		str.appendLine("使用方法：")
		str.appendLine("/空地：获取基本使用方法")
		str.appendLine("/空地 <服务器>：获取服务器空地信息")
		str.appendLine("/空地 <服务器> <S|M|L|海|森|白|沙|雪|个人|部队|准备>：筛选服务器空地信息")
		str.appendLine("/空地 简称：获取服务器简称列表")
		str.appendLine()
		str.appendLine(String.format("今日为第%d轮%d天", turn, diff % 9 + 1))
		str.appendLine(String.format("%s开始参与时间：%s", if (canEntry) "" else "下轮", unixTimeToStr((if (canEntry) thisTurnStart else nextTurnStart).time.time, true)))
		str.appendLine(String.format("%s公示时间：%s", if (canEntry) "" else "下轮", unixTimeToStr((if (canEntry) thisTurnStart else nextTurnStart).time.time / 1000 + 5 * 24 * 60 * 60)))
		str.appendLine()
		str.appendLine("感谢提供数据支持：")
		str.appendLine("Cettiidae/猹")
		str.appendLine("冰音：https://wanahome.ffxiv.bingyin.org/")
		str.appendLine("Jim：https://house.ffxiv.cyou/")
		str.appendLine()
		str.appendLine("By status102/依琳娜")

		commandContext.sender.sendMessage(commandContext.originalMessage.quote() + str.toString().trimEnd('\n'))
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
		if (str.isEmpty() || (!serverMap.containsKey(str) && !subNameMap.containsKey(str))) {
			commandContext.sender.sendMessage(commandContext.originalMessage.quote() + "${str}服务器不存在")
			return
		}
		var serverName = str
		if (subNameMap.containsKey(serverName)) serverName = subNameMap[serverName].toString()
		val realName = serverName
		if (serverMap.containsKey(serverName)) serverName = serverMap[serverName].toString()

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
		val nextEventTimeStr = String.format("%d号%02d点", nextEventTime.get(Calendar.DAY_OF_MONTH), nextEventTime.get(Calendar.HOUR_OF_DAY))
		commandContext.sender.getGroupOrNull()?.botAsMember?.apply {
			nameCard = nameCard.replace(Regex("【.+?(】|$)"), "").let {
				if (it.isEmpty() || it.isBlank())
					nick
				else
					it
			} + "【${if (!canEntry) "下轮开始" else "本轮公示"}${nextEventTimeStr}】"
		}
		val output = getServerData(commandContext.sender.getGroupOrNull()?.id, serverName, realName, size.uppercase(Locale.getDefault())) { i, _ -> i >= 30 }.trimEnd('\n')
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
	}

	private suspend fun sendServerNickName(commandContext: CommandContext) {
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

	private suspend fun getHouseData(serverId: String): Channel<Map<String, PlotInfo>> {
		val channel = Channel<Map<String, PlotInfo>>(3)
		runBlocking(Dispatchers.Default) {
			launch { channel.send(VoteInfoCha().run(serverId)) }
			launch { channel.send(HouseInfo().run(serverId)) }
			launch { channel.send(VoteInfoHouseHelper().run(serverId)) }
		}
		return channel
	}

	private suspend fun getServerData(groupId: Long?, serverId: String, serverName: String, limitStr: String = "", predicate: (Int, HouseInfo.ServerData.SaleItem) -> Boolean): String {
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
			val nextEventTimeStr = String.format("%d号%02d:%02d", nextEventTime.get(Calendar.DAY_OF_MONTH), nextEventTime.get(Calendar.HOUR_OF_DAY), nextEventTime.get(Calendar.MINUTE))

			val output = java.lang.StringBuilder()
			output.appendLine(String.format("[%s]<%s>%s：", if (canEntry) "可参与" else "公示中", serverName, limitStr.uppercase(Locale.getDefault())))
			var content = ""
			val plotInfoMap = mutableMapOf<String, PlotInfo>()

			val channel = getHouseData(serverId)
			repeat(3) {
				channel.receive().forEach { (key, it) ->
					if (plotInfoMap.containsKey(key)) {
						if (plotInfoMap[key]!!.SaleState == 0 || (it.Update > plotInfoMap[key]!!.Update && ((it.Update >= thisTurnStart && plotInfoMap[key]!!.Update < thisTurnStart) || it.SaleState != 0))) {
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
				var outdatedLimit = 0L
				val outdatedTime: String
				if (canEntry) {
					outdatedLimit = maxOf(10 * 60.0, minOf(outdatedWarn.toDouble(), (now - thisTurnStart) * 0.6, (thisTurnShow - now) * 0.6)).toLong()
					outdatedTime = if (outdatedLimit / 60 >= 60) {
						outdatedLimit = outdatedLimit / 60 / 60 * 60 * 60
						"超过${outdatedLimit / 60 / 60}小时"
					} else {
						outdatedLimit = outdatedLimit / 60 / 5 * 5 * 60
						"超过${outdatedLimit / 60}分钟"
					}
				} else {
					outdatedLimit = now - thisTurnShow
					outdatedTime = "公布后"
				}

				if (!limitStr.contains("个人") && personList.isNotEmpty()) output.appendLine("个人：${personList.size}")
				output.append(printSaleList(personList, personShow, now, outdatedLimit))
				if (!limitStr.contains("部队") && fcList.isNotEmpty()) output.appendLine("部队：${fcList.size}")
				output.append(printSaleList(fcList, fcShow, now, outdatedLimit))

				output.appendLine(String.format("今日为第%d轮%d天，%s轮%s：%s", turn, diff % 9 + 1, if (canEntry) "本" else "下", if (!canEntry) "参与" else "公示", nextEventTimeStr))
				if (output.contains(outdatedWarnChar))
					output.appendLine(outdatedWarnChar + outdatedTime + outdatedWarnTips)
				if (personList.size + fcList.size > Limit_Person + Limit_FC) {
					if (showTipsGroup.contains(groupId))
						output.appendLine("更多空地请查阅群公告中的网站")
					else
						output.appendLine("更多空地请使用“/空地”指令查看数据源网站")
				}
			} catch (e: Exception) {
				output.appendLine("转换出错：")
				output.appendLine("Content：${content}")
				output.appendLine(e.stackTraceToString())
			}
			return output.toString().trimEnd('\n') + content
			//return "获取${serverName}服务器空地为空：from：${fromStr}\ncontent：${content}"
		} catch (e: Exception) {
			WannaHomeKt.logger.error { "获取${serverName}服务器数据出错：${e}\n${e.stackTraceToString()}" }
			return "获取${serverName}服务器数据出错：${e}"
		}
	}

	private fun printSaleList(saleList: List<PlotInfo>, countLimit: Int, timeStamp: Long, outdatedLimit: Long): String {
		if (saleList.isEmpty())
			return ""
		val output = StringBuilder()
		for ((i, sale) in saleList.withIndex()) {
			if (i < countLimit) {
				if (sale.SaleState == 1 && timeStamp - sale.Update >= outdatedLimit)
					output.append(outdatedWarnChar)
				output.append(String.format("[%s %s%02d-%02d]", sizeMap[sale.Size], territoryMap[sale.TerritoryId], sale.WardId + 1, sale.HouseId + 1))
				if (sale.SaleState in 1..2)
					output.append(String.format(" 参与:%,d人", sale.VoteCount))
				if (sale.SaleState == 2)
					output.append("(中奖${sale.WinnerIndex}号)")
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

object MapCommand : SimpleCommand(
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
		if (!File("").exists())
			if (!downTerritoryMap(place))
				commandContext.sender.sendMessage("图片下载失败")
		val imageFile = File("${imageDir}${File.separatorChar}$place")
		imageFile.toExternalResource().use {
			if (commandContext.sender.subject != null) {
				val image = it.uploadAsImage(commandContext.sender.subject!!)
				commandContext.sender.sendMessage(commandContext.originalMessage.quote() + image)
			}
		}
	}

	private fun downTerritoryMap(place: String): Boolean {
		if (!imageDir.exists())
			if (!imageDir.mkdir()) {
				println("创建房区地图文件夹失败")
				return false
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

fun unixTimeToStr(num: Long, isMillis: Boolean = false): String {
	return SimpleDateFormat("YYYY-MM-dd HH:mm:ss").format(if (isMillis) num else (num * 1000))
}

fun strTimeToDate(str: String): Date {
	return SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(str)
}

fun strTimeToUnix(str: String): Long {
	return strTimeToDate(str).time / 1000
}

fun strTimeToUnixMillis(str: String): Long {
	return strTimeToDate(str).time
}

object Config : AutoSavePluginConfig("Config") {
	var subNameMap by value(
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