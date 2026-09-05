package com.hans.gitmergeintoplus.settings

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class FavoritesManagerTest : BasePlatformTestCase() {

    fun testServiceIsRegisteredAndPersistsFavorites() {
        val manager = project.getService(FavoritesManager::class.java)
        assertNotNull("project.getService(FavoritesManager) must not be null", manager)

        assertFalse(manager.isFavorite("/repo", "main"))
        manager.toggleFavorite("/repo", "main")
        assertTrue(manager.isFavorite("/repo", "main"))
        manager.toggleFavorite("/repo", "main")
        assertFalse(manager.isFavorite("/repo", "main"))
    }

    fun testMergeOptionsDefaultToTrue() {
        val manager = project.getService(FavoritesManager::class.java)
        assertTrue(manager.isNoFF())
        assertTrue(manager.isPushAfterMerge())
        assertFalse(manager.isPullBeforeMerge())
    }

    fun testMergeOptionsCanBeToggledAndPersisted() {
        val manager = project.getService(FavoritesManager::class.java)
        manager.setNoFF(false)
        manager.setPushAfterMerge(false)
        manager.setPullBeforeMerge(true)
        assertFalse(manager.isNoFF())
        assertFalse(manager.isPushAfterMerge())
        assertTrue(manager.isPullBeforeMerge())

        val state = manager.state
        assertNotNull(state)
        assertFalse(state!!.noFF)
        assertFalse(state.pushAfterMerge)
        assertTrue(state.pullBeforeMerge)

        val newManager = FavoritesManager()
        newManager.loadState(state)
        assertFalse(newManager.isNoFF())
        assertFalse(newManager.isPushAfterMerge())
        assertTrue(newManager.isPullBeforeMerge())
    }
}
