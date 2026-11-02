package local.daybook.datetime

import java.time.Clock
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** 所有展示入口共享本规则，避免夏令时下按 24 小时计算出错。 */
class DayCounter(private val clock: Clock = Clock.systemDefaultZone()) {
    fun describe(date: LocalDate, countUp: Boolean, includeFirstDay: Boolean): String {
        val days = ChronoUnit.DAYS.between(LocalDate.now(clock), date)
        return when {
            days > 0 && countUp -> "尚未开始"
            days > 0 -> "还有 $days 天"
            days == 0L && countUp && includeFirstDay -> "第 1 天"
            days == 0L -> "就是今天"
            countUp && includeFirstDay -> "第 ${-days + 1} 天"
            else -> "已过 ${-days} 天"
        }
    }
}
