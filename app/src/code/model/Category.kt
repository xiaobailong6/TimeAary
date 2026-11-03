package local.daybook.model

/** 分类只描述归属，不拥有事件本身；删除分类时事件迁移到“未分类”。 */
data class Category(
    val id: String,
    val name: String,
    val position: Int,
) {
    companion object {
        const val NAME_LIMIT = 20
        const val LIMIT = 50
    }
}
