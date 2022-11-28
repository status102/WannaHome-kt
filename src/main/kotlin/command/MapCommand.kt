package cn.status102.command

import cn.status102.Map_Url
import cn.status102.WannaHomeKt
import cn.status102.client
import cn.status102.imageDir
import net.mamoe.mirai.console.command.CommandContext
import net.mamoe.mirai.console.command.SimpleCommand
import net.mamoe.mirai.message.data.MessageSource.Key.quote
import net.mamoe.mirai.utils.ExternalResource.Companion.toExternalResource
import net.mamoe.mirai.utils.ExternalResource.Companion.uploadAsImage
import okhttp3.Request
import java.io.File

/**
 * 输出房区地图
 */
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
		if (!File("").exists()) {
			if (!downTerritoryMap(place))
				commandContext.sender.sendMessage("图片下载失败")
		}
		val imageFile = File("$imageDir${File.separatorChar}$place")
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
		val imageFile = File("$imageDir${File.separatorChar}$place")
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