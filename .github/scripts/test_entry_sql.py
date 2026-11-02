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
    for name in ("PAGE", "SUMMARY", "DISTRIBUTION"):
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
    return {name: by_name[name] for name in ("complete", "update", "delete")}


class EntrySqlTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.sql = load_constants()
        cls.mutations = load_mutations()

    def setUp(self):
        self.db = sqlite3.connect(":memory:")
        self.addCleanup(self.db.close)
        self.db.row_factory = sqlite3.Row
        # Match EntryRow's Room column types and non-null Kotlin fields.
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
                revision INTEGER NOT NULL
            )
        """)

    def add(self, number, title="entry", note="", day=TODAY, kind="DATE",
            completed=False):
        identifier = f"00000000-0000-0000-0000-{number:012d}"
        self.db.execute(
            "INSERT INTO entries VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
            (identifier, title, note, day, kind, 0, 1, int(completed), 0),
        )
        return identifier

    def page(self, kind="DATE", search="", filter="ALL", sort="DATE_ASC",
             today=TODAY):
        return self.db.execute(self.sql["PAGE"], {
            "kind": kind, "search": search, "filter": filter,
            "sort": sort, "today": today,
        }).fetchall()

    def ids(self, **parameters):
        return [row["id"] for row in self.page(**parameters)]

    def summary(self, today=TODAY):
        return dict(self.db.execute(self.sql["SUMMARY"], {"today": today}).fetchone())

    def distribution(self, start, end):
        return [tuple(row) for row in self.db.execute(
            self.sql["DISTRIBUTION"], {"start": start, "endInclusive": end}
        )]

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
        self.assertEqual(self.summary(), {
            "dateCount": 0, "todoCount": 0, "completedCount": 0,
            "overdueCount": 0, "upcomingDateCount": 0,
        })
        self.assertEqual(self.ids(), [])
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
            "overdueCount": 1, "upcomingDateCount": 2,
        })
        self.assertEqual(self.summary(today=TODAY + 1), {
            "dateCount": 3, "todoCount": 5, "completedCount": 2,
            "overdueCount": 2, "upcomingDateCount": 1,
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
            "overdueCount": 0, "upcomingDateCount": 1,
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
        self.assertEqual(self.summary(), {
            "dateCount": 0, "todoCount": 0, "completedCount": 0,
            "overdueCount": 0, "upcomingDateCount": 0,
        })
        self.assertEqual(self.distribution(TODAY - 1, TODAY + 1), [])


if __name__ == "__main__":
    unittest.main()
