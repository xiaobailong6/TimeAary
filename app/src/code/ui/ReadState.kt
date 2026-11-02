package local.daybook.ui

/** 页面保留加载和可重试错误，不把失败伪装为零数据。 */
sealed interface ReadState<out T> {
    data object Loading : ReadState<Nothing>
    data class Ready<T>(val value: T) : ReadState<T>
    data class Failed(val message: String) : ReadState<Nothing>
}
