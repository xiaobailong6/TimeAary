package local.daybook.data

/** 常量 SQL 供 Room 编译校验与本地 SQLite 回归复用；参数始终绑定，不拼接用户输入。 */
object EntrySql {
    const val PAGE = """
        SELECT * FROM entries
        WHERE kind = :kind
          AND (:search = '' OR instr(lower(title), lower(:search)) > 0 OR instr(lower(note), lower(:search)) > 0)
          AND (:filter = 'ALL'
            OR (:filter = 'TODAY' AND epochDay = :today)
            OR (:filter = 'FUTURE' AND epochDay > :today)
            OR (:filter = 'PAST' AND epochDay < :today)
            OR (:filter = 'OPEN' AND completed = 0)
            OR (:filter = 'DONE' AND completed = 1)
            OR (:filter = 'OVERDUE' AND completed = 0 AND epochDay < :today))
        ORDER BY
          CASE WHEN kind = 'TODO' THEN completed ELSE 0 END,
          CASE WHEN :sort = 'DATE_ASC' THEN epochDay END ASC,
          CASE WHEN :sort = 'DATE_DESC' THEN epochDay END DESC,
          CASE WHEN :sort = 'TITLE' THEN title END COLLATE NOCASE ASC,
          id ASC
    """
    const val SUMMARY = """
        SELECT
          COUNT(CASE WHEN kind = 'DATE' THEN 1 END) AS dateCount,
          COUNT(CASE WHEN kind = 'TODO' THEN 1 END) AS todoCount,
          COUNT(CASE WHEN kind = 'TODO' AND completed = 1 THEN 1 END) AS completedCount,
          COUNT(CASE WHEN kind = 'TODO' AND completed = 0 AND epochDay < :today THEN 1 END) AS overdueCount,
          COUNT(CASE WHEN kind = 'DATE' AND epochDay >= :today THEN 1 END) AS upcomingDateCount
        FROM entries
    """
    const val DISTRIBUTION = """
        SELECT epochDay,
          COUNT(CASE WHEN kind = 'DATE' THEN 1 END) AS dateCount,
          COUNT(CASE WHEN kind = 'TODO' THEN 1 END) AS todoCount,
          COUNT(CASE WHEN kind = 'TODO' AND completed = 1 THEN 1 END) AS completedCount
        FROM entries WHERE epochDay BETWEEN :start AND :endInclusive
        GROUP BY epochDay ORDER BY epochDay
    """
}
