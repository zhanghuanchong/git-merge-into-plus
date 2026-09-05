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
import java.awt.BasicStroke
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import java.awt.Rectangle
import java.awt.RenderingHints
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseMotionAdapter
import java.awt.geom.Path2D
import javax.swing.Icon
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
    private val preselectedTarget: String? = null,
) : DialogWrapper(project, true) {

    data class BranchCommitSummary(
        val hash: String,
        val author: String,
        val timeAgo: String,
        val subject: String,
        val ahead: Int = 0,
        val behind: Int = 0,
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
    private val commitSummaryCache = ConcurrentHashMap<Triple<String, String, String>, BranchCommitSummary>()
    private val divergenceLabel = JBLabel().apply {
        font = font.deriveFont(font.size2D - 1f)
    }
    private val commitPreviewLabel = JBLabel().apply {
        icon = AllIcons.Vcs.CommitNode
        font = font.deriveFont(font.size2D - 1f)
    }
    private val noFFCheckBox = JCheckBox("Create a merge commit (--no-ff)", favorites.isNoFF()).apply {
        addActionListener {
            commitMessageField.isEnabled = isSelected
            favorites.setNoFF(isSelected)
        }
    }
    private val commitMessageField = JBTextField().apply {
        emptyText.text = "Optional commit message (e.g. Merge feat/login into dev (#1024))"
        isEnabled = favorites.isNoFF()
    }
    private val pullCheckBox = JCheckBox("Update target branch from remote before merging", favorites.isPullBeforeMerge()).apply {
        addActionListener {
            favorites.setPullBeforeMerge(isSelected)
        }
    }
    private val pushCheckBox = JCheckBox("Push target branch after merging", favorites.isPushAfterMerge()).apply {
        addActionListener {
            favorites.setPushAfterMerge(isSelected)
        }
    }

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
        branchList.selectionMode = ListSelectionModel.MULTIPLE_INTERVAL_SELECTION
        branchList.setCellRenderer(BranchCellRenderer())
        branchList.addListSelectionListener(this::onSelectionChanged)
        branchList.addMouseListener(BranchMouseListener())
        branchList.addMouseMotionListener(object : MouseMotionAdapter() {
            override fun mouseMoved(e: MouseEvent) {
                val index = branchList.locationToIndex(e.point)
                if (index >= 0 && index < model.size()) {
                    val item = model.getElementAt(index)
                    if (!item.header) {
                        val cellBounds = branchList.getCellBounds(index, index)
                        if (cellBounds != null && e.x > cellBounds.x + cellBounds.width - STAR_ZONE_WIDTH) {
                            branchList.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                            return
                        }
                    }
                }
                branchList.cursor = Cursor.getDefaultCursor()
            }
        })
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
        left.add(pullCheckBox, oc)

        oc.gridy = 3
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

        val previousSelections = getSelectedBranches()
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

        selectBranches(previousSelections)
        branchList.repaint()
        updateOkButton()
        updateCommitPreview()
    }

    private fun selectBranches(branches: List<String>) {
        if (branches.isEmpty()) {
            val candidate = preselectedTarget ?: favorites.getLastTarget(selectedRepository.root.path)
            if (candidate != null) {
                for (i in 0 until model.size()) {
                    val item = model.getElementAt(i)
                    if (!item.header && item.name == candidate) {
                        branchList.selectedIndex = i
                        branchList.ensureIndexIsVisible(i)
                        return
                    }
                }
            }
            for (i in 0 until model.size()) {
                val item = model.getElementAt(i)
                if (!item.header) {
                    branchList.selectedIndex = i
                    branchList.ensureIndexIsVisible(i)
                    return
                }
            }
            return
        }

        val indicesToSelect = mutableListOf<Int>()
        for (i in 0 until model.size()) {
            val item = model.getElementAt(i)
            if (!item.header && branches.contains(item.name)) {
                indicesToSelect.add(i)
            }
        }

        if (indicesToSelect.isNotEmpty()) {
            branchList.selectedIndices = indicesToSelect.toIntArray()
            branchList.ensureIndexIsVisible(indicesToSelect.first())
        }
    }

    private fun onSelectionChanged(e: ListSelectionEvent) {
        if (e.valueIsAdjusting) {
            return
        }
        val selectedIndices = branchList.selectedIndices
        val headerIndices = selectedIndices.filter { idx ->
            idx >= 0 && idx < model.size() && model.getElementAt(idx).header
        }
        if (headerIndices.isNotEmpty()) {
            for (idx in headerIndices) {
                branchList.removeSelectionInterval(idx, idx)
            }
            return
        }
        updateOkButton()
        updateCommitPreview()
    }

    private fun createCommitPreviewPanel(): JComponent {
        val panel = JPanel(GridBagLayout()).apply {
            border = BorderFactory.createCompoundBorder(
                JBUI.Borders.customLine(JBColor.border(), 1),
                JBUI.Borders.empty(6, 8)
            )
            background = UIUtil.getPanelBackground()
        }
        val c = GridBagConstraints().apply {
            gridx = 0
            weightx = 1.0
            fill = GridBagConstraints.HORIZONTAL
            anchor = GridBagConstraints.WEST
        }
        c.gridy = 0
        c.insets = JBUI.insetsBottom(4)
        panel.add(divergenceLabel, c)
        c.gridy = 1
        c.insets = JBUI.emptyInsets()
        panel.add(commitPreviewLabel, c)
        resetCommitPreview()
        return panel
    }

    private fun resetCommitPreview() {
        divergenceLabel.icon = AllIcons.General.Information
        divergenceLabel.text = "<html><font color='gray'>Select a target branch to preview status and commits</font></html>"
        divergenceLabel.toolTipText = null
        commitPreviewLabel.text = "<html><font color='gray'>Latest commit details will appear here</font></html>"
        commitPreviewLabel.toolTipText = null
    }

    private fun updateCommitPreview() {
        val selected = getSelectedBranches()
        if (selected.isEmpty()) {
            resetCommitPreview()
            return
        }

        if (selected.size > 1) {
            val branchListStr = selected.joinToString(", ")
            divergenceLabel.icon = AllIcons.General.Information
            divergenceLabel.text = "<html><b>${selected.size} target branches selected:</b> ${escape(branchListStr)}</html>"
            divergenceLabel.toolTipText = branchListStr

            val current = currentBranchName.orEmpty()
            commitPreviewLabel.icon = AllIcons.Vcs.Merge
            commitPreviewLabel.text = "<html>Will merge <b>${escape(current)}</b> into ${selected.size} branches sequentially and return</html>"
            commitPreviewLabel.toolTipText = "Sequential merge: ${selected.joinToString(" -> ")}"
            return
        }

        val target = selected.first()
        val repo = selectedRepository
        val current = currentBranchName ?: ""
        val key = Triple(repo.root.path, current, target)
        val cached = commitSummaryCache[key]
        if (cached != null) {
            showCommitSummary(target, cached)
            return
        }

        divergenceLabel.icon = AllIcons.Process.Step_1
        divergenceLabel.text = "<html><font color='gray'>Comparing with <b>${escape(target)}</b>...</font></html>"
        divergenceLabel.toolTipText = null
        commitPreviewLabel.icon = AllIcons.Vcs.CommitNode
        commitPreviewLabel.text = "<html><font color='gray'>Loading latest commit for <b>${escape(target)}</b>...</font></html>"
        commitPreviewLabel.toolTipText = null

        ApplicationManager.getApplication().executeOnPooledThread {
            val summary = fetchLatestCommit(repo, target, currentBranchName)
            if (summary != null) {
                commitSummaryCache[key] = summary
            }
            SwingUtilities.invokeLater {
                if (getSelectedBranches() == listOf(target) && selectedRepository == repo && currentBranchName == current) {
                    showCommitSummary(target, summary)
                }
            }
        }
    }

    private fun showCommitSummary(branch: String, summary: BranchCommitSummary?) {
        if (summary == null) {
            divergenceLabel.icon = AllIcons.General.Warning
            divergenceLabel.text = "<html><font color='gray'>Could not retrieve branch status for <b>${escape(branch)}</b></font></html>"
            divergenceLabel.toolTipText = null
            commitPreviewLabel.text = "<html><font color='gray'>No commit details available</font></html>"
            commitPreviewLabel.toolTipText = null
            return
        }

        val current = currentBranchName.orEmpty()
        val ahead = summary.ahead
        val behind = summary.behind

        when {
            ahead == 0 && behind == 0 -> {
                divergenceLabel.icon = AllIcons.General.InspectionsOK
                divergenceLabel.text = "<html><b>Up to date:</b> Target branch already includes all commits from <b>${escape(current)}</b></html>"
                divergenceLabel.toolTipText = "Target branch is at the same commit as current branch."
            }
            ahead > 0 && behind == 0 -> {
                divergenceLabel.icon = AllIcons.Vcs.Merge
                val commitStr = if (ahead == 1) "commit" else "commits"
                divergenceLabel.text = "<html><b>$ahead $commitStr to merge</b> <font color='gray'>(fast-forward possible)</font></html>"
                divergenceLabel.toolTipText = "Current branch is $ahead commit(s) ahead of target branch."
            }
            ahead > 0 && behind > 0 -> {
                divergenceLabel.icon = AllIcons.General.Warning
                val commitStr = if (ahead == 1) "commit" else "commits"
                divergenceLabel.text = "<html><b>$ahead $commitStr to merge</b> &middot; <font color='#e58e00'><b>$behind behind</b></font> <font color='gray'>(branches diverged)</font></html>"
                divergenceLabel.toolTipText = "Current branch is $ahead ahead and $behind behind target branch."
            }
            else -> {
                divergenceLabel.icon = AllIcons.General.Information
                divergenceLabel.text = "<html><b>Up to date:</b> 0 commits to merge <font color='gray'>(target has $behind newer commits)</font></html>"
                divergenceLabel.toolTipText = "All commits from current branch are already in target branch."
            }
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
        commitPreviewLabel.icon = AllIcons.Vcs.CommitNode
        commitPreviewLabel.text = "<html><b>Latest on target:</b> <code>$escapedHash</code> $escapedSubject <font color='gray'>&mdash; $escapedAuthor, $escapedTime</font></html>"
        commitPreviewLabel.toolTipText = "${summary.hash} ${summary.subject} (${summary.author}, ${summary.timeAgo})"
    }

    private fun fetchLatestCommit(repository: GitRepository, branch: String, currentBranch: String?): BranchCommitSummary? {
        return try {
            val handler = GitLineHandler(project, repository.root, GitCommand.LOG)
            handler.addParameters("-1", "--format=%h\t%an\t%cr\t%s", branch)
            val result = Git.getInstance().runCommand(handler)
            if (!result.success()) return null
            val line = result.output.firstOrNull()?.trim().orEmpty()
            if (line.isEmpty()) return null
            val parts = line.split("\t", limit = 4)
            if (parts.size < 4) return null

            var ahead = 0
            var behind = 0
            if (!currentBranch.isNullOrEmpty()) {
                val revHandler = GitLineHandler(project, repository.root, GitCommand.REV_LIST)
                revHandler.addParameters("--left-right", "--count", "$branch...$currentBranch")
                val revResult = Git.getInstance().runCommand(revHandler)
                if (revResult.success()) {
                    val revLine = revResult.output.firstOrNull()?.trim().orEmpty()
                    val counts = revLine.split("\\s+".toRegex())
                    if (counts.size >= 2) {
                        behind = counts[0].toIntOrNull() ?: 0
                        ahead = counts[1].toIntOrNull() ?: 0
                    }
                }
            }

            BranchCommitSummary(
                hash = parts[0],
                author = parts[1],
                timeAgo = parts[2],
                subject = parts[3],
                ahead = ahead,
                behind = behind,
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun updateOkButton() {
        val selected = getSelectedBranches()
        val valid = selected.isNotEmpty() && currentBranchName != null
        getOKAction().isEnabled = valid
        if (selected.isEmpty()) {
            setOKButtonText("Merge")
        } else if (selected.size == 1) {
            setOKButtonText("Merge into '${selected[0]}'")
        } else {
            setOKButtonText("Merge into ${selected.size} branches")
        }
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
        if (index < 0 || index >= model.size()) {
            return
        }
        val item = model.getElementAt(index)
        if (item.header) {
            return
        }
        if (!branchList.selectedIndices.contains(index)) {
            branchList.selectedIndex = index
        }
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
            if (index < 0 || index >= model.size()) {
                return
            }
            val item = model.getElementAt(index)
            if (item.header) {
                branchList.removeSelectionInterval(index, index)
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

            val star = JLabel(StarIcon(value.favorite, isSelected), SwingConstants.CENTER)
            star.preferredSize = Dimension(STAR_ZONE_WIDTH, STAR_ZONE_WIDTH)
            star.isOpaque = false
            panel.add(star, BorderLayout.EAST)
            return panel
        }
    }

    private class StarIcon(
        private val favorite: Boolean,
        private val isSelected: Boolean
    ) : Icon {
        private val size = JBUI.scale(13)

        override fun getIconWidth(): Int = size
        override fun getIconHeight(): Int = size

        override fun paintIcon(c: Component?, g: Graphics, x: Int, y: Int) {
            val g2 = g.create() as? Graphics2D ?: return
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                val cx = x + size / 2.0
                val cy = y + size / 2.0
                val outerR = size / 2.0 - 0.5
                val innerR = outerR * 0.42

                val path = Path2D.Double()
                val startAngle = -Math.PI / 2.0
                for (i in 0 until 10) {
                    val r = if (i % 2 == 0) outerR else innerR
                    val angle = startAngle + i * Math.PI / 5.0
                    val px = cx + r * Math.cos(angle)
                    val py = cy + r * Math.sin(angle)
                    if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
                }
                path.closePath()

                if (favorite) {
                    g2.color = JBColor(Color(0xF5A623), Color(0xF5BA2A))
                    g2.fill(path)
                    g2.color = JBColor(Color(0xD48806), Color(0xDE9E1B))
                    g2.stroke = BasicStroke(0.75f)
                    g2.draw(path)
                } else {
                    val strokeColor = if (isSelected) {
                        JBColor(Color(255, 255, 255, 110), Color(255, 255, 255, 110))
                    } else {
                        JBColor(Color(0xD2D2D2), Color(0x525252))
                    }
                    g2.color = strokeColor
                    g2.stroke = BasicStroke(0.85f)
                    g2.draw(path)
                }
            } finally {
                g2.dispose()
            }
        }
    }

    private fun escape(text: String): String =
        text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    fun getRepository(): GitRepository? = selectedRepository

    fun getCurrentBranchName(): String? = currentBranchName

    fun getSelectedBranches(): List<String> {
        return branchList.selectedValuesList
            .filter { !it.header }
            .map { it.name }
            .distinct()
    }

    fun getSelectedBranch(): String? = getSelectedBranches().firstOrNull()

    fun isNoFF(): Boolean = noFFCheckBox.isSelected

    fun isPushAfterMerge(): Boolean = pushCheckBox.isSelected

    fun isPullBeforeMerge(): Boolean = pullCheckBox.isSelected

    fun getDivergenceText(): String = divergenceLabel.text

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
        favorites.setPullBeforeMerge(isPullBeforeMerge())
        val selected = getSelectedBranches()
        if (selected.isNotEmpty()) {
            favorites.setLastTarget(selectedRepository.root.path, selected.first())
        }
        super.doOKAction()
    }

    override fun getPreferredFocusedComponent(): JComponent? = searchField
}
