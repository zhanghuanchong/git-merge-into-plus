package com.hans.gitmergeintoplus.dialog

import com.hans.gitmergeintoplus.settings.FavoritesManager
import com.intellij.icons.AllIcons
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.JBColor
import com.intellij.ui.SearchTextField
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import git4idea.GitLocalBranch
import git4idea.commands.Git
import git4idea.commands.GitCommand
import git4idea.commands.GitLineHandler
import git4idea.repo.GitRepository
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.Font
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import java.awt.Rectangle
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.BorderFactory
import javax.swing.DefaultListCellRenderer
import javax.swing.DefaultListModel
import javax.swing.JCheckBox
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.JPopupMenu
import javax.swing.ListCellRenderer
import javax.swing.ListSelectionModel
import javax.swing.SwingConstants
import javax.swing.SwingUtilities
import javax.swing.event.ListSelectionEvent
import java.util.concurrent.ConcurrentHashMap

private const val STAR_ZONE_WIDTH = 24

class MergeIntoDialog(
    private val project: Project,
    private val repositories: List<GitRepository>,
    private val defaultRepository: GitRepository,
) : DialogWrapper(project, true) {

    data class BranchCommitSummary(
        val hash: String,
        val author: String,
        val timeAgo: String,
        val subject: String,
    )

    private val favorites: FavoritesManager = FavoritesManager.getInstance(project)

    private val infoLabel = JBLabel()
    private val searchField = SearchTextField()
    private val branchCountLabel = JBLabel().apply {
        font = font.deriveFont(font.size2D - 1f)
        foreground = UIUtil.getContextHelpForeground()
    }
    private val model = DefaultListModel<BranchItem>()
    private val branchList = JBList(model)
    private val commitSummaryCache = ConcurrentHashMap<Pair<String, String>, BranchCommitSummary>()
    private val commitPreviewLabel = JBLabel().apply {
        icon = AllIcons.Vcs.CommitNode
        font = font.deriveFont(font.size2D - 1f)
    }
    private val noFFCheckBox = JCheckBox("Create a merge commit (--no-ff)", favorites.isNoFF())
    private val commitMessageField = JBTextField().apply {
        emptyText.text = "Optional commit message (e.g. Merge feat/login into dev (#1024))"
        isEnabled = favorites.isNoFF()
    }
    private val pushCheckBox = JCheckBox("Push target branch after merging", favorites.isPushAfterMerge())

    private var selectedRepository: GitRepository = defaultRepository
    private var currentBranchName: String? = null

    init {
        setTitle("Merge Into...")
        setOKButtonText("Merge")
        init()
        updateForRepository()
    }

    data class BranchItem(val name: String, val header: Boolean, val favorite: Boolean) {
        companion object {
            fun header(text: String) = BranchItem(text, true, false)
            fun branch(name: String, favorite: Boolean) = BranchItem(name, false, favorite)
        }
    }

    override fun createCenterPanel(): JComponent? {
        val panel = JPanel(GridBagLayout())
        val gbc = GridBagConstraints().apply {
            gridx = 0
            weightx = 1.0
            fill = GridBagConstraints.HORIZONTAL
            insets = JBUI.insets(4, 8)
        }

        if (repositories.size > 1) {
            gbc.gridy = 0
            panel.add(createRepositorySelector(), gbc)
        }

        gbc.gridy = 1
        infoLabel.border = BorderFactory.createEmptyBorder(2, 0, 2, 0)
        panel.add(infoLabel, gbc)

        gbc.gridy = 2
        searchField.addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(e: javax.swing.event.DocumentEvent) {
                rebuildModel()
            }
        })
        searchField.toolTipText = "Search branches by name"
        val searchPanel = JPanel(BorderLayout(JBUI.scale(8), 0)).apply {
            add(searchField, BorderLayout.CENTER)
            add(branchCountLabel, BorderLayout.EAST)
        }
        panel.add(searchPanel, gbc)

        gbc.gridy = 3
        gbc.weighty = 1.0
        gbc.fill = GridBagConstraints.BOTH
        branchList.selectionMode = ListSelectionModel.SINGLE_SELECTION
        branchList.setCellRenderer(BranchCellRenderer())
        branchList.addListSelectionListener(this::onSelectionChanged)
        branchList.addMouseListener(BranchMouseListener())
        val scrollPane = JBScrollPane(branchList)
        scrollPane.preferredSize = Dimension(420, 310)
        panel.add(scrollPane, gbc)

        gbc.gridy = 4
        gbc.weighty = 0.0
        gbc.fill = GridBagConstraints.HORIZONTAL
        panel.add(createCommitPreviewPanel(), gbc)

        gbc.gridy = 5
        gbc.weighty = 0.0
        gbc.fill = GridBagConstraints.HORIZONTAL
        val options = JPanel(BorderLayout())
        val left = JPanel(GridBagLayout())
        val oc = GridBagConstraints().apply {
            gridx = 0
            gridy = 0
            weightx = 1.0
            fill = GridBagConstraints.HORIZONTAL
            insets = JBUI.insets(0, 0, 2, 0)
        }
        noFFCheckBox.addActionListener {
            commitMessageField.isEnabled = noFFCheckBox.isSelected
        }
        left.add(noFFCheckBox, oc)

        oc.gridy = 1
        oc.insets = JBUI.insets(2, 22, 4, 0)
        val commitMsgPanel = JPanel(BorderLayout(JBUI.scale(6), 0)).apply {
            add(JBLabel("Commit message:").apply {
                foreground = UIUtil.getContextHelpForeground()
                font = font.deriveFont(font.size2D - 1f)
            }, BorderLayout.WEST)
            add(commitMessageField, BorderLayout.CENTER)
        }
        left.add(commitMsgPanel, oc)

        oc.gridy = 2
        oc.insets = JBUI.insets(2, 0, 2, 0)
        left.add(pushCheckBox, oc)
        options.add(left, BorderLayout.CENTER)
        panel.add(options, gbc)

        return panel
    }

    private fun createRepositorySelector(): JComponent {
        val panel = JPanel(BorderLayout())
        val label = JBLabel("Repository:")
        label.border = BorderFactory.createEmptyBorder(0, 0, 0, 6)
        panel.add(label, BorderLayout.WEST)

        val combo = JComboBox<GitRepository>()
        repositories.forEach { combo.addItem(it) }
        combo.renderer = object : DefaultListCellRenderer() {
            override fun getListCellRendererComponent(
                list: JList<*>?,
                value: Any?,
                index: Int,
                isSelected: Boolean,
                cellHasFocus: Boolean,
            ): Component {
                val c = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
                if (value is GitRepository) {
                    text = value.root.path
                }
                return c
            }
        }
        combo.selectedItem = selectedRepository
        combo.addActionListener {
            val item = combo.selectedItem
            if (item is GitRepository) {
                selectedRepository = item
                updateForRepository()
            }
        }
        panel.add(combo, BorderLayout.CENTER)
        return panel
    }

    private fun updateForRepository() {
        val branch: GitLocalBranch? = selectedRepository.currentBranch
        currentBranchName = branch?.name
        if (currentBranchName == null) {
            infoLabel.text = "Repository is not on any branch (detached HEAD). Merge is unavailable."
        } else {
            infoLabel.text =
                "<html>Current branch: <b>${escape(currentBranchName!!)}</b> &mdash; merge it into:</html>"
        }
        branchList.clearSelection()
        rebuildModel()
    }

    private fun rebuildModel() {
        val filter = searchField.text.trim().lowercase()
        val repoPath = selectedRepository.root.path

        val favoriteNames = ArrayList<String>()
        val otherNames = ArrayList<String>()
        selectedRepository.branches.localBranches
            .map { it.name }
            .filter { it != currentBranchName }
            .filter { filter.isEmpty() || it.lowercase().contains(filter) }
            .sorted()
            .forEach { name ->
                if (favorites.isFavorite(repoPath, name)) favoriteNames.add(name) else otherNames.add(name)
            }

        val previousSelection = getSelectedBranch()
        model.clear()

        val totalBranches = selectedRepository.branches.localBranches.count { it.name != currentBranchName }
        val totalFavorites = selectedRepository.branches.localBranches.count {
            it.name != currentBranchName && favorites.isFavorite(repoPath, it.name)
        }
        val matchedBranches = favoriteNames.size + otherNames.size
        val matchedFavorites = favoriteNames.size

        if (filter.isEmpty()) {
            val favSuffix = if (totalFavorites > 0) " · $totalFavorites ★" else ""
            val branchWord = if (totalBranches == 1) "branch" else "branches"
            branchCountLabel.text = "$totalBranches $branchWord$favSuffix"
        } else {
            val favSuffix = if (matchedFavorites > 0) " ($matchedFavorites ★)" else ""
            val branchWord = if (totalBranches == 1) "branch" else "branches"
            branchCountLabel.text = "$matchedBranches / $totalBranches $branchWord$favSuffix"
        }

        if (favoriteNames.isNotEmpty()) {
            model.addElement(BranchItem.header(favoriteNames.size.toString() + " favorite" +
                if (favoriteNames.size > 1) "s" else ""))
            favoriteNames.forEach { model.addElement(BranchItem.branch(it, true)) }
            model.addElement(BranchItem.header("All branches"))
            otherNames.forEach { model.addElement(BranchItem.branch(it, false)) }
        } else {
            model.addElement(BranchItem.header(if (filter.isEmpty()) "All branches" else "Matches"))
            otherNames.forEach { model.addElement(BranchItem.branch(it, false)) }
        }

        if (model.size() == 0) {
            branchList.emptyText.text = "No branches found"
        }

        selectBranch(previousSelection)
        branchList.repaint()
        updateOkButton()
        updateCommitPreview()
    }

    private fun selectBranch(branchName: String?) {
        var candidate = branchName
        if (candidate == null) {
            candidate = favorites.getLastTarget(selectedRepository.root.path)
        }
        for (i in 0 until model.size()) {
            val item = model.getElementAt(i)
            if (!item.header && (candidate == null || item.name == candidate)) {
                branchList.selectedIndex = i
                branchList.ensureIndexIsVisible(i)
                return
            }
        }
        if (candidate != null) {
            for (i in 0 until model.size()) {
                val item = model.getElementAt(i)
                if (!item.header) {
                    branchList.selectedIndex = i
                    branchList.ensureIndexIsVisible(i)
                    return
                }
            }
        }
    }

    private fun onSelectionChanged(e: ListSelectionEvent) {
        if (e.valueIsAdjusting) {
            return
        }
        updateOkButton()
        updateCommitPreview()
    }

    private fun createCommitPreviewPanel(): JComponent {
        val panel = JPanel(BorderLayout()).apply {
            border = BorderFactory.createCompoundBorder(
                JBUI.Borders.customLine(JBColor.border(), 1),
                JBUI.Borders.empty(6, 8)
            )
            background = UIUtil.getPanelBackground()
        }
        resetCommitPreview()
        panel.add(commitPreviewLabel, BorderLayout.CENTER)
        return panel
    }

    private fun resetCommitPreview() {
        commitPreviewLabel.text = "<html><font color='gray'>Select a branch to preview its latest commit</font></html>"
        commitPreviewLabel.toolTipText = null
    }

    private fun updateCommitPreview() {
        val selected = getSelectedBranch()
        if (selected == null) {
            resetCommitPreview()
            return
        }

        val repo = selectedRepository
        val key = Pair(repo.root.path, selected)
        val cached = commitSummaryCache[key]
        if (cached != null) {
            showCommitSummary(selected, cached)
            return
        }

        commitPreviewLabel.text = "<html><font color='gray'>Loading latest commit for <b>${escape(selected)}</b>...</font></html>"
        commitPreviewLabel.toolTipText = null

        ApplicationManager.getApplication().executeOnPooledThread {
            val summary = fetchLatestCommit(repo, selected)
            if (summary != null) {
                commitSummaryCache[key] = summary
            }
            SwingUtilities.invokeLater {
                if (getSelectedBranch() == selected && selectedRepository == repo) {
                    showCommitSummary(selected, summary)
                }
            }
        }
    }

    private fun showCommitSummary(branch: String, summary: BranchCommitSummary?) {
        if (summary == null) {
            commitPreviewLabel.text = "<html><font color='gray'>No commit details available for <b>${escape(branch)}</b></font></html>"
            commitPreviewLabel.toolTipText = null
            return
        }
        val displaySubject = if (summary.subject.length > 55) {
            summary.subject.take(52) + "..."
        } else {
            summary.subject
        }
        val escapedSubject = escape(displaySubject)
        val escapedAuthor = escape(summary.author)
        val escapedTime = escape(summary.timeAgo)
        val escapedHash = escape(summary.hash)
        commitPreviewLabel.text = "<html><b>Latest commit:</b> <code>$escapedHash</code> $escapedSubject <font color='gray'>&mdash; $escapedAuthor, $escapedTime</font></html>"
        commitPreviewLabel.toolTipText = "${summary.hash} ${summary.subject} (${summary.author}, ${summary.timeAgo})"
    }

    private fun fetchLatestCommit(repository: GitRepository, branch: String): BranchCommitSummary? {
        return try {
            val handler = GitLineHandler(project, repository.root, GitCommand.LOG)
            handler.addParameters("-1", "--format=%h\t%an\t%cr\t%s", branch)
            val result = Git.getInstance().runCommand(handler)
            if (result.success()) {
                val line = result.output.firstOrNull()?.trim().orEmpty()
                if (line.isNotEmpty()) {
                    val parts = line.split("\t", limit = 4)
                    if (parts.size >= 4) {
                        BranchCommitSummary(
                            hash = parts[0],
                            author = parts[1],
                            timeAgo = parts[2],
                            subject = parts[3]
                        )
                    } else null
                } else null
            } else null
        } catch (_: Exception) {
            null
        }
    }

    private fun updateOkButton() {
        val item = branchList.selectedValue
        val valid = item != null && !item.header
        getOKAction().isEnabled = valid && currentBranchName != null
    }

    private fun toggleFavoriteFor(item: BranchItem) {
        if (item.header) {
            return
        }
        favorites.toggleFavorite(selectedRepository.root.path, item.name)
        rebuildModel()
    }

    private fun showPopupMenu(e: MouseEvent) {
        val index = branchList.locationToIndex(e.point)
        if (index < 0) {
            return
        }
        val item = model.getElementAt(index)
        if (item.header) {
            return
        }
        branchList.selectedIndex = index
        val menu = JPopupMenu()
        if (item.favorite) {
            menu.add("Remove from favorites").addActionListener { toggleFavoriteFor(item) }
        } else {
            menu.add("Add to favorites").addActionListener { toggleFavoriteFor(item) }
        }
        menu.show(branchList, e.x, e.y)
    }

    private inner class BranchMouseListener : MouseAdapter() {
        override fun mouseClicked(e: MouseEvent) {
            val index = branchList.locationToIndex(e.point)
            if (index < 0) {
                return
            }
            val item = model.getElementAt(index)
            if (item.header) {
                return
            }
            val cellBounds = branchList.getCellBounds(index, index)
            val inStarZone = e.x > cellBounds.x + cellBounds.width - STAR_ZONE_WIDTH

            if (e.clickCount == 2 && !inStarZone && SwingUtilities.isLeftMouseButton(e)) {
                if (getOKAction().isEnabled) {
                    doOKAction()
                }
                return
            }
            if (inStarZone && SwingUtilities.isLeftMouseButton(e)) {
                toggleFavoriteFor(item)
            }
        }

        override fun mousePressed(e: MouseEvent) {
            if (e.isPopupTrigger) {
                showPopupMenu(e)
            }
        }

        override fun mouseReleased(e: MouseEvent) {
            if (e.isPopupTrigger) {
                showPopupMenu(e)
            }
        }
    }

    private class BranchCellRenderer : ListCellRenderer<BranchItem> {
        override fun getListCellRendererComponent(
            list: JList<out BranchItem>,
            value: BranchItem,
            index: Int,
            isSelected: Boolean,
            cellHasFocus: Boolean,
        ): Component {
            val panel = JPanel(BorderLayout())
            panel.background = if (isSelected) list.selectionBackground else list.background

            if (value.header) {
                val label = JLabel(value.name)
                label.font = label.font.deriveFont(Font.BOLD)
                label.foreground = UIUtil.getContextHelpForeground()
                label.border = BorderFactory.createEmptyBorder(4, 8, 4, 8)
                panel.add(label, BorderLayout.CENTER)
                return panel
            }

            val nameLabel = JLabel(value.name)
            nameLabel.foreground = if (isSelected) list.selectionForeground else list.foreground
            nameLabel.border = BorderFactory.createEmptyBorder(2, 8, 2, 2)
            panel.add(nameLabel, BorderLayout.CENTER)

            val star = JLabel(if (value.favorite) "\u2605" else "\u2606", SwingConstants.CENTER)
            star.preferredSize = Dimension(STAR_ZONE_WIDTH, STAR_ZONE_WIDTH)
            star.isOpaque = false
            star.foreground = if (isSelected) list.selectionForeground else list.foreground
            panel.add(star, BorderLayout.EAST)
            return panel
        }
    }

    private fun escape(text: String): String =
        text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    fun getRepository(): GitRepository? = selectedRepository

    fun getCurrentBranchName(): String? = currentBranchName

    fun getSelectedBranch(): String? {
        val item = branchList.selectedValue
        return if (item == null || item.header) null else item.name
    }

    fun isNoFF(): Boolean = noFFCheckBox.isSelected

    fun isPushAfterMerge(): Boolean = pushCheckBox.isSelected

    fun getCustomCommitMessage(): String? {
        if (!isNoFF()) return null
        return commitMessageField.text.trim().takeIf { it.isNotEmpty() }
    }

    fun getBranchCountText(): String = branchCountLabel.text

    fun setCommitMessage(text: String) {
        commitMessageField.text = text
    }

    override fun doOKAction() {
        favorites.setNoFF(isNoFF())
        favorites.setPushAfterMerge(isPushAfterMerge())
        super.doOKAction()
    }

    override fun getPreferredFocusedComponent(): JComponent? = searchField
}
