package cn.status102.command

import cn.status102.WannaHomeKt
import cn.status102.data.PlotInfo
import cn.status102.getEmptyPlace
import net.mamoe.mirai.console.command.CommandContext
import net.mamoe.mirai.console.command.SimpleCommand
import org.jetbrains.skia.Bitmap
import java.util.*

/**
 * 控制台测试用指令，虽然也可以用“/空房”做测试就是了
 */
object TestCommand : SimpleCommand(
	WannaHomeKt, "wh", "WannaHome", description = "示例指令"
) {
	@Handler
	public suspend fun handle(commandContext: CommandContext) {

		val list = listOf(
			PlotInfo(1177, 339, 1, 4, 2, Calendar.getInstance().timeInMillis / 1000),
			PlotInfo(1177, 339, 20, 4, 2, Calendar.getInstance().timeInMillis / 1000 - 12 * 3600, 1)
		)
		//getPic("测试数据", list)
		val bitmap = Bitmap().apply {
			allocN32Pixels(120, 180)
		}


		/*Typeface.makeFromName("SimSun", FontStyle.NORMAL).familyNames.forEach {
			WannaHomeKt.logger.info { "字符集：${it.name}<${it.language}>" }
		}*/

		/*
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
		}*/
		/*
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
		*/

	}

	@Handler
	suspend fun handle(commandContext: CommandContext, vararg str: String) {
		getEmptyPlace(commandContext, str.joinToString(" "))
	}
}