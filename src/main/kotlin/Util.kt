package cn.status102

import java.text.SimpleDateFormat
import java.util.*
import kotlin.random.Random

/**
 * 返回 YYYY-MM-dd HH:mm:ss 格式的当前时间
 */
fun strTime() = unixTimeToStr(Calendar.getInstance().timeInMillis, true)

fun random() = Random(Calendar.getInstance().timeInMillis)


fun unixTimeToStr(num: Long, isMillis: Boolean = false): String {
	return SimpleDateFormat("YYYY-MM-dd HH:mm:ss").format(if (isMillis) num else (num * 1000))
}

fun unixMillisTimeToStr(num: Long): String {
	return SimpleDateFormat("YYYY-MM-dd HH:mm:ss.SSS").format(num)
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