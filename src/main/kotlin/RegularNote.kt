package cn.status102

import kotlinx.coroutines.*
import net.mamoe.mirai.Bot
import net.mamoe.mirai.contact.Group
import net.mamoe.mirai.utils.info
import java.util.*
import kotlin.math.floor

@OptIn(DelicateCoroutinesApi::class)
fun initNote() {
	GlobalScope.launch(Dispatchers.IO) {
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
			WannaHomeKt.logger.info { "注册例行公告，执行时间：${unixMillisTimeToStr(nextEventTime.timeInMillis)}" }
			delay(nextEventTime.timeInMillis - now + 5)
			try {
				sendNoteMessage()
			} catch (e: Exception) {
				WannaHomeKt.logger.info { "执行例行提醒出错：\n${e.stackTraceToString()}" }
			}
		}
	}

	GlobalScope.launch(Dispatchers.IO) {
		while (true) {
			val now = Calendar.getInstance().timeInMillis
			val start = Calendar.getInstance().apply { time = strTimeToDate("2022-08-08 20:00:00") }

			val diff = floor((Calendar.getInstance().timeInMillis - start.timeInMillis) / 1000 / 60.0 / 60 / 24).toInt()
			val turn = diff / 9 + 1
			val canEntry = (diff % 9) < 5
			val nextEventTime = (start.clone() as Calendar).apply {
				if (canEntry)
					add(Calendar.DAY_OF_MONTH, ((turn - 1) * 9 + 5))
				else
					add(Calendar.DAY_OF_MONTH, turn * 9 + 5)
			}
			WannaHomeKt.logger.info { "注册开奖前提醒，执行时间：${unixMillisTimeToStr(nextEventTime.timeInMillis)}" }
			delay(nextEventTime.timeInMillis - now + 5)
			try {
				sendJoinTimeEndNotice()
			} catch (e: Exception) {
				WannaHomeKt.logger.info { "执行开奖前提醒出错：\n${e.stackTraceToString()}" }
			}
		}
	}
}


suspend fun sendNoteMessage() {
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


	output.appendLine("第${turn}轮${if (canEntry) "摇号" else "公示"}已开始")
	if (!canEntry)
		output.appendLine(String.format("下轮参与时间：%s点~%s点", unixTimeToStr(nextTurnStart.timeInMillis, true).substring(5, 13), unixTimeToStr((nextTurnStart.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, 5) }.timeInMillis, true).substring(5, 13)))
	else
		output.appendLine(String.format("参与时间：%s点~%s点", unixTimeToStr(thisTurnStart.timeInMillis, true).substring(5, 13), unixTimeToStr(thisTurnShow.timeInMillis, true).substring(5, 13)))
	output.appendLine("温馨提醒：数据来源的多个平台标准不统一，每个周期开始时会筛除掉上周期数据")
	output.appendLine("请耐心等待各平台数据更新，也欢迎更多玩家贡献数据")

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

	runBlocking {
		Bot.instances.forEach { bot ->
			if (bot.isOnline && noteList.containsKey(bot.id)) {
				launch(Dispatchers.IO) {
					noteList[bot.id]?.forEach {
						delay(random().nextLong(10_000, 60_000))
						bot.getGroup(it)?.run {
							changeBotGroupNameCard(this)
							sendMessage(output.toString().trimEnd('\n'))
						}
					}
				}
			}
		}
	}
}

/**
 * 参与期即将结束前，发送提醒消息
 */
suspend fun sendJoinTimeEndNotice() {
	if (Data.WannaHomeGroupList.isEmpty()) return
	val time = TimeCalculator.getInstance()

	val output = StringBuilder()

	output.appendLine("第${time.turn}轮${if (time.canEntry) "摇号" else "公示"}今晚即将结束，请注意结束时间以免错过")

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
	}

	runBlocking {
		Bot.instances.forEach { bot ->
			if (bot.isOnline && noteList.containsKey(bot.id)) {
				launch(Dispatchers.IO) {
					noteList[bot.id]?.forEach {
						delay(random().nextLong(10_000, 60_000))
						bot.getGroup(it)?.run {
							changeBotGroupNameCard(this)
							sendMessage(output.toString().trimEnd('\n'))
						}
					}
				}
			}
		}
	}
}


/**
 * 修改BOT群名片中的提示文本【(下轮抽奖|本轮公示)】
 */
fun changeBotGroupNameCard(group: Group?) {
	if (group == null) return
	val time = TimeCalculator.getInstance()
	val nextEventTimeStr = String.format("%d号%02d点", time.nextEventDate.get(Calendar.DAY_OF_MONTH), time.nextEventDate.get(Calendar.HOUR_OF_DAY))
	group.botAsMember.apply {
		nameCard = nameCard.replace(Regex("【.+?(】|$)"), "").let { nameCard ->
			if (nameCard.isEmpty() || nameCard.isBlank())
				nick
			else
				nameCard
		} + "【${if (!time.canEntry) "下轮抽奖" else "本轮公示"}${nextEventTimeStr}】"
	}
}
