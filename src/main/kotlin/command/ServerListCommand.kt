package cn.status102.command

import cn.status102.WannaHomeKt
import cn.status102.serverIdMap
import net.mamoe.mirai.console.command.CommandSender
import net.mamoe.mirai.console.command.CompositeCommand


object ServerListCommand : CompositeCommand(
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