package local.daybook.model

import org.junit.Assert.*
import org.junit.Test

class AppSettingsTest {
    @Test fun defaultsShowEveryEntryAndKeepSettingsVisible() {
        val settings = AppSettings()
        assertEquals(listOf(NavEntry.TASKS, NavEntry.TODOS, NavEntry.ANALYSIS, NavEntry.SETTINGS),
            settings.navEntries)
        assertTrue(settings.isVisible(NavEntry.SETTINGS))
    }

    @Test fun hidingEntriesKeepsOneEntryAndSettings() {
        var settings = AppSettings()
        settings = settings.toggleEntry(NavEntry.TASKS)
        settings = settings.toggleEntry(NavEntry.TODOS)
        settings = settings.toggleEntry(NavEntry.ANALYSIS)
        // 最后一项非设置入口也不允许隐藏，设置入口固定保留。
        assertEquals(listOf(NavEntry.ANALYSIS, NavEntry.SETTINGS), settings.navEntries)
        assertEquals(settings, settings.toggleEntry(NavEntry.SETTINGS))
        assertEquals(settings, settings.toggleEntry(NavEntry.ANALYSIS))
    }

    @Test fun showingHiddenEntryAppendsItBeforeSettings() {
        val settings = AppSettings().toggleEntry(NavEntry.TASKS).toggleEntry(NavEntry.TASKS)
        assertEquals(listOf(NavEntry.TODOS, NavEntry.ANALYSIS, NavEntry.TASKS, NavEntry.SETTINGS),
            settings.navEntries)
    }

    @Test fun movingReordersVisibleEntriesAndStopsAtEdges() {
        val moved = AppSettings().moveEntry(NavEntry.SETTINGS, -1)
        assertEquals(listOf(NavEntry.TASKS, NavEntry.TODOS, NavEntry.SETTINGS, NavEntry.ANALYSIS),
            moved.navEntries)
        assertEquals(moved, moved.moveEntry(NavEntry.TASKS, -1))
        assertEquals(listOf(NavEntry.TASKS, NavEntry.TODOS, NavEntry.ANALYSIS, NavEntry.SETTINGS),
            moved.moveEntry(NavEntry.SETTINGS, 1).navEntries)
    }

    @Test fun navListIsEncodedAndDecodedDeterministically() {
        assertEquals("TODOS,SETTINGS", AppSettings.encodeNav(listOf(NavEntry.TODOS, NavEntry.SETTINGS)))
        assertEquals(listOf(NavEntry.TODOS, NavEntry.SETTINGS),
            AppSettings.decodeNav("TODOS,SETTINGS"))
        // 未知与空值回退到完整默认入口，不产生空底栏。
        assertEquals(listOf(NavEntry.TASKS, NavEntry.TODOS, NavEntry.ANALYSIS, NavEntry.SETTINGS),
            AppSettings.decodeNav("UNKNOWN,,,"))
        assertEquals(listOf(NavEntry.TASKS, NavEntry.TODOS, NavEntry.ANALYSIS, NavEntry.SETTINGS),
            AppSettings.decodeNav(null))
    }

    @Test fun normalizedAlwaysEndsWithSettingsAndRemovesDuplicates() {
        val settings = AppSettings(navEntries = listOf(NavEntry.TODOS, NavEntry.TODOS, NavEntry.SETTINGS))
        assertEquals(listOf(NavEntry.TODOS, NavEntry.SETTINGS), settings.normalized().navEntries)
        assertEquals(listOf(NavEntry.TASKS, NavEntry.SETTINGS),
            AppSettings(navEntries = listOf(NavEntry.SETTINGS)).normalized().navEntries)
    }

    @Test fun fontScaleFactorsAreOrdered() {
        assertEquals(1f, FontScale.DEFAULT.factor, 0.0001f)
        assertTrue(FontScale.SMALL.factor < FontScale.DEFAULT.factor)
        assertTrue(FontScale.LARGE.factor > FontScale.MEDIUM.factor)
    }

    @Test fun visualEnumsRejectUnknownNames() {
        assertEquals(EntryIcon.STAR, parseEntryIcon("STAR"))
        assertNull(parseEntryIcon("UNKNOWN"))
        assertNull(parseEntryIcon(null))
        assertEquals(EntryColor.RED, parseEntryColor("RED"))
        assertNull(parseEntryColor(""))
        assertNull(parseEntryColor(null))
    }
}
