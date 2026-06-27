package com.vmdex.focusguard

import org.junit.Assert.assertEquals
import org.junit.Test

class ChooseAppsTest {
    // Перевіряємо, що без пошуку selected apps показуються на початку списку.
    @Test
    fun selectedAppsArePinnedFirstWhenSearchIsEmpty() {
        val visibleApps = visibleAppsForSelection(
            apps = apps,
            pinnedPackages = setOf(ChromePackage),
            searchText = ""
        )

        assertEquals(
            listOf("Chrome", "Calculator", "YouTube"),
            visibleApps.map { it.appName }
        )
    }

    // Перевіряємо, що пошук фільтрує всі apps і не лишає selected app зверху, якщо він не match-иться.
    @Test
    fun searchFiltersSelectedAppsToo() {
        val visibleApps = visibleAppsForSelection(
            apps = apps,
            pinnedPackages = setOf(ChromePackage),
            searchText = "you"
        )

        assertEquals(
            listOf("YouTube"),
            visibleApps.map { it.appName }
        )
    }

    // Перевіряємо, що зняття галочки не змінює порядок поточного списку.
    @Test
    fun uncheckingPinnedAppDoesNotMoveItDuringCurrentEdit() {
        val visibleApps = visibleAppsForSelection(
            apps = apps,
            pinnedPackages = setOf(ChromePackage),
            searchText = ""
        )

        assertEquals(
            listOf("Chrome", "Calculator", "YouTube"),
            visibleApps.map { it.appName }
        )
    }

    // Перевіряємо, що після очищення пошуку повертається порядок, який був до пошуку.
    @Test
    fun clearingSearchRestoresInitialPinnedOrder() {
        val beforeSearch = visibleAppsForSelection(
            apps = apps,
            pinnedPackages = setOf(ChromePackage),
            searchText = ""
        )
        val duringSearch = visibleAppsForSelection(
            apps = apps,
            pinnedPackages = setOf(ChromePackage),
            searchText = "you"
        )
        val afterSearch = visibleAppsForSelection(
            apps = apps,
            pinnedPackages = setOf(ChromePackage),
            searchText = ""
        )

        assertEquals(listOf("YouTube"), duringSearch.map { it.appName })
        assertEquals(beforeSearch, afterSearch)
    }

    private companion object {
        const val ChromePackage = "com.android.chrome"

        val apps = listOf(
            LaunchableApp(packageName = "com.google.android.youtube", appName = "YouTube"),
            LaunchableApp(packageName = ChromePackage, appName = "Chrome"),
            LaunchableApp(packageName = "com.android.calculator2", appName = "Calculator")
        )
    }
}
