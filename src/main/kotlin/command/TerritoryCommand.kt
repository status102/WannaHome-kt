package cn.status102.command

import cn.status102.WannaHomeKt
import cn.status102.territoryMap
import net.mamoe.mirai.console.command.CommandSender
import net.mamoe.mirai.console.command.CompositeCommand

/**
 * 输出房区列表
 */
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