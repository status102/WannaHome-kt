package cn.status102

import java.util.*
import kotlin.math.floor

val lock = Any()
var instance = object : TimeCalculator() {}

abstract class TimeCalculator {
	companion object {
		fun getInstance(): TimeCalculator {
			if (Calendar.getInstance().timeInMillis > instance.nextUpdateTime) {
				synchronized(lock) {
					if (Calendar.getInstance().timeInMillis > instance.nextUpdateTime) {
						synchronized(lock) {
							instance = object : TimeCalculator() {}
						}
					}
				}
			}
			return instance
		}
	}

	val start: Calendar = Calendar.getInstance().apply { time = strTimeToDate("2022-08-08 23:00:00") }

	val diff = floor((Calendar.getInstance().timeInMillis - start.timeInMillis) / 1000 / 60.0 / 60 / 24).toInt()
	val turn = diff / 9 + 1
	val canEntry = (diff % 9) < 5

	val lastTurnStart = (start.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, (turn - 2) * 9) }.timeInMillis / 1000

	val thisTurnStart = (start.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, (turn - 1) * 9) }.timeInMillis / 1000
	val thisTurnShow = (start.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, (turn - 1) * 9 + 5) }.timeInMillis / 1000

	val nextTurnStart = (start.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, turn * 9) }.timeInMillis / 1000
	val nextTurnShow = (start.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, turn * 9 + 5) }.timeInMillis / 1000

	val nextEventTime = (start.clone() as Calendar).apply {
		if (canEntry)
			add(Calendar.DAY_OF_MONTH, ((turn - 1) * 9 + 5))
		else
			add(Calendar.DAY_OF_MONTH, turn * 9)
	}.timeInMillis / 1000
	val nextEventDate = (start.clone() as Calendar).apply {
		if (canEntry)
			add(Calendar.DAY_OF_MONTH, ((turn - 1) * 9 + 5))
		else
			add(Calendar.DAY_OF_MONTH, turn * 9)
	}

	val nextUpdateTime = (start.clone() as Calendar).apply {
		add(Calendar.DAY_OF_MONTH, ((turn - 1) * 9 + diff))
	}.timeInMillis
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
