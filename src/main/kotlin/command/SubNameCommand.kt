package cn.status102.command

import cn.status102.Config
import cn.status102.WannaHomeKt
import net.mamoe.mirai.console.command.CommandSender
import net.mamoe.mirai.console.command.CompositeCommand

/**
 * 输出服务器简称
 */
object SubNameCommand : CompositeCommand(
	WannaHomeKt, "服务器简称", "简称", "缩写"
) {
	@SubCommand("list")
	suspend fun CommandSender.handle() { // 函数名随意, 但参数需要按顺序放置.
		val str = StringBuilder()
		for (pair in Config.subNameMap) {
			str.appendLine("<${pair.key}, ${pair.value}>")
		}
		sendMessage(str.toString())
	}

	@SubCommand("del")
	suspend fun CommandSender.handle(key: String) {
		if (Config.subNameMap.containsKey(key)) {
			val subName = Config.subNameMap[key]
			Config.subNameMap.remove(key)
			sendMessage("成功删除<${subName}>的别名：$key")
		} else
			sendMessage("别名<$key>不存在")
	}

	@SubCommand("add")
	suspend fun CommandSender.handle(key: String, value: String) {
		if (Config.subNameMap.containsKey(key))
			sendMessage("别名<${key}>存在")
		else {
			Config.subNameMap[key] = value
			sendMessage("添加<${value}>的别名：${key} 成功")
		}
	}
}