package cn.status102

import net.mamoe.mirai.console.data.AutoSavePluginConfig
import net.mamoe.mirai.console.data.AutoSavePluginData
import net.mamoe.mirai.console.data.value

object Config : AutoSavePluginConfig("Config") {
	var subNameMap: MutableMap<String, String> by value(
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

object Data : AutoSavePluginData("Data") {
	//TODO 增加号主设定
	var WannaHomeGroupList by value(mutableSetOf<String>())
}

/**
 * 房区地图链接，From 灰机wiki
 */
val Map_Url: Map<String, String> = mapOf(
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