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
}
