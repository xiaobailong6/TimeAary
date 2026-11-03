package local.daybook.data

/** 常量 SQL 供 Room 编译校验与本地 SQLite 回归复用；参数始终绑定，不拼接用户输入。 */
object EntrySql {
    // 活动列表：归档条目只在归档流出现；置顶优先，其次保持完成状态分组与所选排序。
    const val PAGE = """
        SELECT * FROM entries
        WHERE kind = :kind
          AND archivedAt IS NULL
          AND (:categoryId = '' OR categoryId = :categoryId)
          AND (:search = '' OR instr(lower(title), lower(:search)) > 0 OR instr(lower(note), lower(:search)) > 0)
          AND (:filter = 'ALL'
            OR (:filter = 'TODAY' AND epochDay = :today)
            OR (:filter = 'FUTURE' AND epochDay > :today)
            OR (:filter = 'PAST' AND epochDay < :today)
            OR (:filter = 'OPEN' AND completed = 0)
            OR (:filter = 'DONE' AND completed = 1)
            OR (:filter = 'OVERDUE' AND completed = 0 AND epochDay < :today))
        ORDER BY
          pinned DESC,
          CASE WHEN kind = 'TODO' THEN completed ELSE 0 END,
          CASE WHEN :sort = 'DATE_ASC' THEN epochDay END ASC,
          CASE WHEN :sort = 'DATE_DESC' THEN epochDay END DESC,
          CASE WHEN :sort = 'TITLE' THEN title END COLLATE NOCASE ASC,
          id ASC
    """
    // 归档列表是独立分页流：只读 archivedAt 非空的行，按最近归档时间优先。
    const val ARCHIVE_PAGE = """
        SELECT * FROM entries
        WHERE kind = :kind
          AND archivedAt IS NOT NULL
          AND (:search = '' OR instr(lower(title), lower(:search)) > 0 OR instr(lower(note), lower(:search)) > 0)
        ORDER BY archivedAt DESC, id ASC
    """
    const val SUMMARY = """
        SELECT
          COUNT(CASE WHEN archivedAt IS NULL AND kind = 'DATE' THEN 1 END) AS dateCount,
          COUNT(CASE WHEN archivedAt IS NULL AND kind = 'TODO' THEN 1 END) AS todoCount,
          COUNT(CASE WHEN archivedAt IS NULL AND kind = 'TODO' AND completed = 1 THEN 1 END) AS completedCount,
          COUNT(CASE WHEN archivedAt IS NULL AND kind = 'TODO' AND completed = 0 AND epochDay < :today THEN 1 END) AS overdueCount,
          COUNT(CASE WHEN archivedAt IS NULL AND kind = 'DATE' AND epochDay >= :today THEN 1 END) AS upcomingDateCount,
          COUNT(CASE WHEN archivedAt IS NOT NULL THEN 1 END) AS archivedCount
        FROM entries
    """
    // 归档条目保留历史但不进入活动分布，统计口径与列表筛选保持一致。
    const val DISTRIBUTION = """
        SELECT epochDay,
          COUNT(CASE WHEN kind = 'DATE' THEN 1 END) AS dateCount,
          COUNT(CASE WHEN kind = 'TODO' THEN 1 END) AS todoCount,
          COUNT(CASE WHEN kind = 'TODO' AND completed = 1 THEN 1 END) AS completedCount
        FROM entries WHERE archivedAt IS NULL AND epochDay BETWEEN :start AND :endInclusive
        GROUP BY epochDay ORDER BY epochDay
    """
    const val CATEGORIES = """
        SELECT * FROM categories ORDER BY position ASC, name COLLATE NOCASE ASC
    """
}
