"""Run the production Room SQL against an isolated, in-memory SQLite database."""

import re
import sqlite3
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SQL_SOURCE = ROOT / "app/src/code/data/EntrySql.kt"
DATABASE_SOURCE = ROOT / "app/src/code/data/EntryDatabase.kt"
TODAY = 20_000


def load_constants():
    """Keep query behavior in Kotlin: do not maintain Python copies of the SQL."""
    source = SQL_SOURCE.read_text(encoding="utf-8")
    constants = {}
    for name in ("PAGE", "ARCHIVE_PAGE", "SUMMARY", "DISTRIBUTION", "CATEGORIES"):
        matches = re.findall(
            r'\bconst\s+val\s+' + name + r'\s*=\s*"""(.*?)"""',
            source,
            flags=re.DOTALL,
        )
        if len(matches) != 1:
            raise AssertionError(f"Expected exactly one Kotlin SQL constant {name}")
        constants[name] = matches[0]
    return constants


def load_mutations():
    """Use the DAO statements too, so mutation tests exercise real revisions."""
    source = DATABASE_SOURCE.read_text(encoding="utf-8")
    queries = dict(re.findall(
        r'@Query\("([^"\n]*)"\)\s*suspend\s+fun\s+(\w+)\s*\(', source
    ))
    by_name = {name: query for query, name in queries.items()}
    names = ("complete", "update", "delete", "deleteMany", "assignCategoryMany",
             "setPinned", "archiveEntry", "restoreEntry", "clearCategory", "deleteCategory")
    return {name: by_name[name] for name in names}


class EntrySqlTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.sql = load_constants()
        cls.mutations = load_mutations()

    def setUp(self):
        self.db = sqlite3.connect(":memory:")
        self.addCleanup(self.db.close)
        self.db.row_factory = sqlite3.Row
        # Match the Room columns declared in EntryDatabase.kt (schema version 3).
        self.db.execute("""
            CREATE TABLE entries (
                id TEXT NOT NULL PRIMARY KEY,
                title TEXT NOT NULL,
                note TEXT NOT NULL,
                epochDay INTEGER NOT NULL,
                kind TEXT NOT NULL,
                countUp INTEGER NOT NULL,
                includeFirstDay INTEGER NOT NULL,
                completed INTEGER NOT NULL,
                revision INTEGER NOT NULL,
                categoryId TEXT,
                pinned INTEGER NOT NULL DEFAULT 0,
                archivedAt INTEGER,
                archiveReason TEXT,
                icon TEXT,
                color TEXT
            )
        """)
        self.db.execute("""
            CREATE TABLE categories (
                id TEXT NOT NULL PRIMARY KEY,
                name TEXT NOT NULL,
                position INTEGER NOT NULL
            )
        """)
        self.db.execute("CREATE UNIQUE INDEX index_categories_name ON categories (name)")

    def add(self, number, title="entry", note="", day=TODAY, kind="DATE",
            completed=False, category=None, pinned=0, archived=None,
            icon=None, color=None):
        identifier = f"00000000-0000-0000-0000-{number:012d}"
        self.db.execute(
            "INSERT INTO entries VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            (identifier, title, note, day, kind, 0, 1, int(completed), 0,
             category, pinned, archived, "MANUAL" if archived is not None else None,
             icon, color),
        )
        return identifier

    def add_category(self, name, position):
        identifier = f"c-{name}"
        self.db.execute("INSERT INTO categories VALUES (?, ?, ?)", (identifier, name, position))
        return identifier

    def page(self, kind="DATE", search="", filter="ALL", sort="DATE_ASC",
             today=TODAY, category=""):
        return self.db.execute(self.sql["PAGE"], {
            "kind": kind, "search": search, "filter": filter,
            "sort": sort, "today": today, "categoryId": category,
        }).fetchall()

    def archive_page(self, kind="DATE", search=""):
        return self.db.execute(self.sql["ARCHIVE_PAGE"], {"kind": kind, "search": search}).fetchall()

    def ids(self, **parameters):
        return [row["id"] for row in self.page(**parameters)]

    def archive_ids(self, **parameters):
        return [row["id"] for row in self.archive_page(**parameters)]

    def summary(self, today=TODAY):
        return dict(self.db.execute(self.sql["SUMMARY"], {"today": today}).fetchone())

    def distribution(self, start, end):
        return [tuple(row) for row in self.db.execute(
            self.sql["DISTRIBUTION"], {"start": start, "endInclusive": end}
        )]

    def archive(self, identifier, archived_at=TODAY * 10, reason="MANUAL"):
        return self.db.execute(self.mutations["archiveEntry"], {
            "id": identifier, "archivedAt": archived_at, "reason": reason,
        }).rowcount

    def restore(self, identifier):
        return self.db.execute(self.mutations["restoreEntry"], {"id": identifier}).rowcount

    def batch_delete(self, identifiers):
        # Room expands :ids into one bind per element; mirror that here.
        bounded = ", ".join("?" * len(identifiers))
        query = self.mutations["deleteMany"].replace("(:ids)", f"({bounded})")
        return self.db.execute(query, list(identifiers)).rowcount

    def batch_assign(self, identifiers, category):
        bounded = ", ".join("?" * len(identifiers))
        query = self.mutations["assignCategoryMany"].replace(":categoryId", "?") \
            .replace("(:ids)", f"({bounded})")
        return self.db.execute(query, [category] + list(identifiers)).rowcount

    def empty_summary(self):
        return {"dateCount": 0, "todoCount": 0, "completedCount": 0,
                "overdueCount": 0, "upcomingDateCount": 0, "archivedCount": 0}

    def test_chinese_title_substring_and_kind_isolation(self):
        match = self.add(1, title="爸爸的生日纪念")
        self.add(2, title="爸爸的生日纪念", kind="TODO")
        self.add(3, title="其他纪念日")
        self.assertEqual(self.ids(search="生日"), [match])

    def test_chinese_note_substring(self):
        match = self.add(1, title="出发", note="记得提前购买火车票哦")
        self.add(2, title="回家", note="记得带钥匙")
        self.assertEqual(self.ids(search="购买火车票"), [match])

    def test_ascii_search_is_case_insensitive_in_title_and_note(self):
        title = self.add(1, title="Plan ALPHA Trip")
        note = self.add(2, note="review Alpha tomorrow")
        self.add(3, title="unrelated")
        for search in ("alpha", "ALPHA", "aLpHa"):
            with self.subTest(search=search):
                self.assertEqual(self.ids(search=search), [title, note])

    def test_percent_and_underscore_are_literal(self):
        percent = self.add(1, title="Save 50% today")
        underscore = self.add(2, note="file_name")
        self.add(3, title="Save 500 today", note="fileXname")
        for search, expected in (("%", [percent]), ("_", [underscore]),
                                 ("50%", [percent]), ("file_", [underscore]),
                                 ("%_", [])):
            with self.subTest(search=search):
                self.assertEqual(self.ids(search=search), expected)

    def test_injection_like_search_is_bound_not_executed(self):
        attack = "'; DROP TABLE entries; --"
        match = self.add(1, note="literal " + attack)
        self.add(2, title="ordinary")
        self.assertEqual(self.ids(search=attack), [match])
        self.assertEqual(self.ids(search="' OR 1=1 --"), [])
        self.assertEqual(self.summary()["dateCount"], 2)
        self.assertEqual(len(self.ids()), 2)

    def test_date_filters_have_strict_boundaries_and_bound_today(self):
        past = self.add(1, day=TODAY - 1)
        today = self.add(2)
        future = self.add(3, day=TODAY + 1)
        self.add(4, day=TODAY - 1, kind="TODO")
        for filter, expected in (("PAST", [past]), ("TODAY", [today]),
                                 ("FUTURE", [future]), ("ALL", [past, today, future])):
            with self.subTest(filter=filter):
                self.assertEqual(self.ids(filter=filter), expected)
        self.assertEqual(self.ids(filter="TODAY", today=TODAY + 1), [future])
        self.assertEqual(self.ids(filter="PAST", today=TODAY + 1), [past, today])

    def test_todo_open_done_and_overdue(self):
        past = self.add(1, kind="TODO", day=TODAY - 1)
        today = self.add(2, kind="TODO")
        future = self.add(3, kind="TODO", day=TODAY + 1)
        done = self.add(4, kind="TODO", day=TODAY - 2, completed=True)
        self.add(5, day=TODAY - 3)
        for filter, expected in (("OPEN", [past, today, future]),
                                 ("DONE", [done]), ("OVERDUE", [past])):
            with self.subTest(filter=filter):
                self.assertEqual(self.ids(kind="TODO", filter=filter), expected)
        self.assertEqual(self.ids(kind="TODO", filter="OVERDUE", today=TODAY + 1),
                         [past, today])

    def test_sort_modes_use_uuid_tiebreak_and_completed_last(self):
        # Insert ties out of UUID order to catch accidental rowid ordering.
        b = self.add(2, title="alpha", kind="TODO")
        a = self.add(1, title="Alpha", kind="TODO")
        early = self.add(3, title="Zulu", kind="TODO", day=TODAY - 1)
        late = self.add(4, title="Beta", kind="TODO", day=TODAY + 1)
        done = self.add(5, title="A", kind="TODO", day=TODAY - 10, completed=True)
        expected = {
            "DATE_ASC": [early, a, b, late, done],
            "DATE_DESC": [late, a, b, early, done],
            "TITLE": [a, b, late, early, done],
        }
        for sort, identifiers in expected.items():
            with self.subTest(sort=sort):
                self.assertEqual(self.ids(kind="TODO", sort=sort), identifiers)
                self.assertEqual(self.ids(kind="TODO", sort=sort), identifiers)

    def test_search_reaches_beyond_thirty_loaded_rows(self):
        identifiers = [self.add(i, title=f"ordinary {i}", day=TODAY + i)
                       for i in range(1, 66)]
        distant = self.add(66, title="远处的目标", day=TODAY + 66)
        self.assertEqual(self.ids(), identifiers + [distant])
        self.assertEqual(self.ids(search="目标"), [distant])
        self.assertEqual(self.summary()["dateCount"], 66)

    def test_empty_summary_has_zero_counts(self):
        self.assertEqual(self.summary(), self.empty_summary())
        self.assertEqual(self.ids(), [])
        self.assertEqual(self.archive_ids(), [])
        self.assertEqual(self.distribution(TODAY - 1, TODAY + 1), [])

    def test_summary_exact_counts_and_today_boundary(self):
        for i, day in enumerate((TODAY - 1, TODAY, TODAY + 1), 1):
            self.add(i, day=day)
        self.add(4, kind="TODO", day=TODAY - 1)
        self.add(5, kind="TODO")
        self.add(6, kind="TODO", day=TODAY + 1)
        self.add(7, kind="TODO", day=TODAY - 2, completed=True)
        self.add(8, kind="TODO", completed=True)
        self.assertEqual(self.summary(), {
            "dateCount": 3, "todoCount": 5, "completedCount": 2,
            "overdueCount": 1, "upcomingDateCount": 2, "archivedCount": 0,
        })
        self.assertEqual(self.summary(today=TODAY + 1), {
            "dateCount": 3, "todoCount": 5, "completedCount": 2,
            "overdueCount": 2, "upcomingDateCount": 1, "archivedCount": 0,
        })

    def test_distribution_exact_inclusive_range_and_order(self):
        self.add(1, day=TODAY + 2)
        self.add(2, day=TODAY - 2)
        self.add(3, day=TODAY + 1)
        self.add(4, day=TODAY - 1)
        self.add(5, day=TODAY - 1, kind="TODO")
        self.add(6, day=TODAY - 1, kind="TODO", completed=True)
        self.add(7, day=TODAY + 1, kind="TODO", completed=True)
        self.assertEqual(self.distribution(TODAY - 1, TODAY + 1), [
            (TODAY - 1, 1, 2, 1), (TODAY + 1, 1, 1, 1),
        ])
        self.assertEqual(self.distribution(TODAY - 1, TODAY - 1),
                         [(TODAY - 1, 1, 2, 1)])
        self.assertEqual(self.distribution(TODAY, TODAY), [])
        self.assertEqual(self.distribution(TODAY + 1, TODAY - 1), [])

    def test_completion_updates_page_summary_and_distribution(self):
        identifier = self.add(1, kind="TODO", day=TODAY - 1)
        for completed in (1, 0):
            with self.subTest(completed=completed):
                self.db.execute(self.mutations["complete"], {
                    "id": identifier, "completed": completed,
                })
                self.assertEqual(self.ids(kind="TODO", filter="OVERDUE"),
                                 [] if completed else [identifier])
                self.assertEqual(self.ids(kind="TODO", filter="DONE"),
                                 [identifier] if completed else [])
                self.assertEqual(self.summary(), {
                    "dateCount": 0, "todoCount": 1, "completedCount": completed,
                    "overdueCount": 1 - completed, "upcomingDateCount": 0,
                    "archivedCount": 0,
                })
                self.assertEqual(self.distribution(TODAY - 1, TODAY),
                                 [(TODAY - 1, 0, 1, completed)])
        self.assertEqual(self.page(kind="TODO")[0]["revision"], 2)

    def test_edit_date_updates_filters_search_summary_and_distribution(self):
        date = self.add(1, title="old", day=TODAY - 1)
        todo = self.add(2, title="old", kind="TODO", day=TODAY - 1)
        for identifier in (date, todo):
            self.db.execute(self.mutations["update"], {
                "id": identifier, "title": "新标题", "note": "new note",
                "epochDay": TODAY + 1, "countUp": 1, "includeFirstDay": 0,
                "categoryId": None, "icon": None, "color": None,
            })
        for kind, identifier in (("DATE", date), ("TODO", todo)):
            with self.subTest(kind=kind):
                self.assertEqual(self.ids(kind=kind, filter="PAST"), [])
                self.assertEqual(self.ids(kind=kind, filter="FUTURE", search="新"),
                                 [identifier])
                self.assertEqual(self.ids(kind=kind, search="old"), [])
                row = self.page(kind=kind)[0]
                self.assertEqual((row["revision"], row["countUp"], row["includeFirstDay"]),
                                 (1, 1, 0))
        self.assertEqual(self.ids(kind="TODO", filter="OVERDUE"), [])
        self.assertEqual(self.summary(), {
            "dateCount": 1, "todoCount": 1, "completedCount": 0,
            "overdueCount": 0, "upcomingDateCount": 1, "archivedCount": 0,
        })
        self.assertEqual(self.distribution(TODAY - 1, TODAY + 1),
                         [(TODAY + 1, 1, 1, 0)])

    def test_delete_updates_page_summary_and_distribution(self):
        date = self.add(1)
        done = self.add(2, kind="TODO", completed=True)
        overdue = self.add(3, kind="TODO", day=TODAY - 1)
        for identifier in (date, done, overdue):
            self.db.execute(self.mutations["delete"], {"id": identifier})
        self.assertEqual(self.ids(), [])
        self.assertEqual(self.ids(kind="TODO"), [])
        self.assertEqual(self.summary(), self.empty_summary())
        self.assertEqual(self.distribution(TODAY - 1, TODAY + 1), [])

    def test_icon_and_color_are_saved_changed_and_cleared(self):
        identifier = self.add(1, icon="STAR", color="BLUE")
        row = self.page()[0]
        self.assertEqual((row["icon"], row["color"]), ("STAR", "BLUE"))
        self.db.execute(self.mutations["update"], {
            "id": identifier, "title": "改", "note": "", "epochDay": TODAY,
            "countUp": 0, "includeFirstDay": 0, "categoryId": None,
            "icon": None, "color": "RED",
        })
        row = self.page()[0]
        self.assertEqual((row["title"], row["icon"], row["color"]), ("改", None, "RED"))

    def test_batch_delete_removes_only_selected_rows(self):
        first = self.add(1)
        second = self.add(2)
        third = self.add(3, kind="TODO")
        self.assertEqual(self.batch_delete([first, third]), 2)
        self.assertEqual(self.ids(), [second])
        self.assertEqual(self.ids(kind="TODO"), [])
        self.assertEqual(self.summary()["dateCount"], 1)
        self.assertEqual(self.summary()["todoCount"], 0)

    def test_batch_assign_category_moves_only_selected_rows(self):
        work = self.add_category("工作", 0)
        first = self.add(1)
        second = self.add(2)
        untouched = self.add(3, category=work)
        self.assertEqual(self.batch_assign([first, second], work), 2)
        self.assertEqual(self.ids(category=work), [first, second, untouched])
        self.assertEqual(self.batch_assign([first], None), 1)
        self.assertEqual(self.ids(category=work), [second, untouched])
        self.assertEqual([row["categoryId"] for row in self.page()], [None, work, work])
        # 批量移动分类只改归属与 revision，不改变日期与完成状态。
        self.assertEqual(self.summary()["dateCount"], 3)

    def test_archive_moves_record_between_lists_and_pauses_counts(self):
        identifier = self.add(1, kind="TODO", day=TODAY - 1)
        self.assertEqual(self.ids(kind="TODO"), [identifier])
        self.assertEqual(self.archive(identifier), 1)
        self.assertEqual(self.ids(kind="TODO"), [])
        self.assertEqual(self.archive_ids(kind="TODO"), [identifier])
        self.assertEqual(self.summary(), {
            "dateCount": 0, "todoCount": 0, "completedCount": 0,
            "overdueCount": 0, "upcomingDateCount": 0, "archivedCount": 1,
        })
        self.assertEqual(self.distribution(TODAY - 1, TODAY), [])
        self.assertEqual(self.restore(identifier), 1)
        self.assertEqual(self.archive_ids(kind="TODO"), [])
        self.assertEqual(self.ids(kind="TODO"), [identifier])
        self.assertEqual(self.summary()["archivedCount"], 0)
        self.assertEqual(self.page(kind="TODO")[0]["revision"], 2)

    def test_archive_keeps_first_timestamp_and_ignores_repeat(self):
        identifier = self.add(1)
        self.assertEqual(self.archive(identifier, archived_at=1_000), 1)
        self.assertEqual(self.archive(identifier, archived_at=9_000), 0)
        row = self.archive_page()[0]
        self.assertEqual((row["archivedAt"], row["archiveReason"]), (1_000, "MANUAL"))
        self.assertEqual(row["revision"], 1)

    def test_archive_page_is_separate_searchable_and_newest_first(self):
        old = self.add(1, title="旧归档")
        new = self.add(2, title="新归档")
        active = self.add(3, title="仍在列表")
        self.archive(old, archived_at=1_000)
        self.archive(new, archived_at=5_000)
        self.assertEqual(self.archive_ids(), [new, old])
        self.assertEqual(self.archive_ids(search="旧"), [old])
        self.assertEqual(self.ids(), [active])
        self.assertEqual(self.archive_ids(kind="TODO"), [])

    def test_pinned_records_stay_first_within_the_same_list(self):
        pinned_late = self.add(1, title="Zulu", pinned=1, day=TODAY + 5)
        early = self.add(2, title="Alpha", day=TODAY - 5)
        late = self.add(3, title="Beta", day=TODAY + 1)
        self.assertEqual(self.ids(), [pinned_late, early, late])
        self.assertEqual(self.ids(sort="TITLE"), [pinned_late, early, late])
        self.db.execute(self.mutations["setPinned"], {"id": early, "pinned": 0})
        self.db.execute(self.mutations["setPinned"], {"id": late, "pinned": 1})
        self.assertEqual(self.db.execute(
            "SELECT revision FROM entries WHERE id=:id", {"id": early}).fetchone()[0], 1)
        # 置顶优先于完成分组：已完成但置顶的待办仍在最前，取消置顶后回到组内位置。
        done_pinned = self.add(4, kind="TODO", completed=True, pinned=1, day=TODAY - 3)
        open_todo = self.add(5, kind="TODO", day=TODAY + 2)
        self.assertEqual(self.ids(kind="TODO"), [done_pinned, open_todo])
        self.db.execute(self.mutations["setPinned"], {"id": done_pinned, "pinned": 0})
        self.assertEqual(self.ids(kind="TODO"), [open_todo, done_pinned])
        self.assertEqual(self.db.execute(
            "SELECT revision FROM entries WHERE id=:id", {"id": done_pinned}).fetchone()[0], 1)

    def test_category_filter_isolates_and_excludes_uncategorized(self):
        work = self.add_category("工作", 0)
        travel = self.add_category("旅行", 1)
        in_work = self.add(1, title="周报", category=work)
        in_travel = self.add(2, title="机票", category=travel)
        uncategorized = self.add(3, title="随手记")
        self.assertEqual(self.ids(category=work), [in_work])
        self.assertEqual(self.ids(category=travel), [in_travel])
        self.assertEqual(self.ids(category=""), [in_work, in_travel, uncategorized])
        self.assertEqual(self.ids(category="c-不存在"), [])
        self.assertEqual(self.summary()["dateCount"], 3)

    def test_category_filter_combines_with_search_and_archive_state(self):
        work = self.add_category("工作", 0)
        self.add_category("旅行", 1)
        match = self.add(1, title="季度总结", category=work)
        self.add(2, title="年度总结", category="c-旅行")
        archived = self.add(3, title="季度规划", category=work)
        self.archive(archived)
        self.assertEqual(self.ids(category=work, search="季度"), [match])
        self.assertEqual(self.ids(category=work), [match])
        self.assertEqual(self.archive_ids(search="季度"), [archived])

    def test_delete_category_migrates_records_without_deleting_them(self):
        work = self.add_category("工作", 0)
        other = self.add_category("旅行", 1)
        first = self.add(1, category=work)
        second = self.add(2, category=work, kind="TODO")
        untouched = self.add(3, category=other)
        self.db.execute(self.mutations["clearCategory"], {"id": work})
        self.db.execute(self.mutations["deleteCategory"], {"id": work})
        self.assertEqual(self.ids(), [first, untouched])
        self.assertEqual(self.ids(kind="TODO"), [second])
        self.assertEqual([row["categoryId"] for row in self.page()], [None, other])
        self.assertEqual(self.ids(category=work), [])
        self.assertEqual(self.ids(category=other), [untouched])
        self.assertEqual(self.summary()["dateCount"], 2)
        self.assertEqual(self.summary()["todoCount"], 1)

    def test_categories_list_uses_position_then_case_insensitive_name(self):
        self.add_category("旅行", 5)
        self.add_category("工作", 1)
        self.add_category("alpha", 3)
        self.add_category("Beta", 3)
        names = [row["name"] for row in self.db.execute(self.sql["CATEGORIES"])]
        self.assertEqual(names, ["工作", "alpha", "Beta", "旅行"])


if __name__ == "__main__":
    unittest.main()