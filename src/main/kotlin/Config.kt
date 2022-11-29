package cn.status102

import net.mamoe.mirai.console.data.AutoSavePluginConfig
import net.mamoe.mirai.console.data.AutoSavePluginData
import net.mamoe.mirai.console.data.value

val Limit_Person = 20..40
val Limit_FC = 20..40
const val fcIdStart = 16//个人区1~16，对应ID 0~15
const val Merge_Left = 10F
const val Merge_Right = 15F
const val Merge_Up = 10F
const val Merge_Down = 10F
const val Merge_Mid = 20F

object Config : AutoSavePluginConfig("Config") {

	/**
	 * 当BOT收到好友邀请、群邀请时，进行通知的QQ号
	 */
	var owner by value(0L)
	var acceptFriendRequest by value(false)
	var acceptGroupRequest by value(false)
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


/**
 * name to ID
 */
val serverIdMap: Map<String, Int> = mapOf(
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
val serverNameMap: Map<Int, String> by lazy {
	serverIdMap.mapValues { it.key }.mapKeys { serverIdMap[it.key]!! }
}
val serverMap: Map<String, List<String>> = mapOf(
	"陆行鸟" to listOf("鸟"), "莫古力" to listOf("猪"), "猫小胖" to listOf("猫"), "豆豆柴" to listOf("狗"),
	"鸟" to listOf("拉诺西亚", "幻影群岛", "神意之地", "萌芽池", "红玉海", "宇宙和音", "沃仙曦染", "晨曦王座"),
	"猪" to listOf("潮风亭", "神拳痕", "白银乡", "白金幻象", "旅人栈桥", "拂晓之间", "龙巢神殿", "梦羽宝境"),
	"猫" to listOf("紫水栈桥", "延夏", "静语庄园", "摩杜纳", "海猫茶屋", "柔风海湾", "琥珀原"),
	"狗" to listOf("水晶塔", "银泪湖", "太阳海岸", "伊修加德", "红茶川")
)
val territoryMap: Map<Int, String> = mapOf(339 to "海", 340 to "森", 341 to "沙", 641 to "白", 979 to "雪")
val sizeMap: List<String> = listOf("S", "M", "L")