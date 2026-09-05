package com.hans.gitmergeintoplus.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.Project

@State(name = "GitMergeIntoPlusSettings", storages = [Storage("gitMergeIntoPlus.xml")])
class FavoritesManager : PersistentStateComponent<FavoritesManager.State> {

    class State {
        var favorites: MutableMap<String, MutableList<String>> = HashMap()
        var lastTargets: MutableMap<String, String> = HashMap()
        var noFF: Boolean = true
        var pushAfterMerge: Boolean = true
        var pullBeforeMerge: Boolean = false
    }

    private var myState = State()

    override fun getState(): State? = myState

    override fun loadState(state: State) {
        myState = state
    }

    fun isFavorite(repoPath: String, branchName: String): Boolean =
        myState.favorites[repoPath]?.contains(branchName) == true

    fun toggleFavorite(repoPath: String, branchName: String) {
        val list = myState.favorites.getOrPut(repoPath) { ArrayList() }
        if (!list.remove(branchName)) {
            list.add(branchName)
        }
        if (list.isEmpty()) {
            myState.favorites.remove(repoPath)
        }
    }

    fun getLastTarget(repoPath: String): String? = myState.lastTargets[repoPath]

    fun setLastTarget(repoPath: String, branchName: String) {
        myState.lastTargets[repoPath] = branchName
    }

    fun isNoFF(): Boolean = myState.noFF

    fun setNoFF(value: Boolean) {
        myState.noFF = value
    }

    fun isPushAfterMerge(): Boolean = myState.pushAfterMerge

    fun setPushAfterMerge(value: Boolean) {
        myState.pushAfterMerge = value
    }

    fun isPullBeforeMerge(): Boolean = myState.pullBeforeMerge

    fun setPullBeforeMerge(value: Boolean) {
        myState.pullBeforeMerge = value
    }

    companion object {
        fun getInstance(project: Project): FavoritesManager =
            project.getService(FavoritesManager::class.java)
    }
}
