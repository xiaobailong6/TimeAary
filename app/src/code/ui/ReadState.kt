package local.daybook.ui

/** 页面保留加载和可重试错误，不把失败伪装为零数据。 */
sealed interface ReadState<out T> {
    data object Loading : ReadState<Nothing>
    data class Ready<T>(val value: T) : ReadState<T>
    data class Failed(val message: String) : ReadState<Nothing>
}

/**
 * 取出成功值，加载 / 失败时返回 null。
 * 标星投影的 `Ready<*>` 取值会退化成 `Any?`，所以统一经这里做一次显式类型转换。
 */
fun <T> ReadState<T>.valueOrNull(): T? = (this as? ReadState.Ready<T>)?.value
