/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.refactoring.move

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.ui.*
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.SeparatorFactory
import com.intellij.util.ui.tree.TreeUtil
import java.awt.BorderLayout
import javax.swing.Icon
import javax.swing.JPanel
import javax.swing.tree.DefaultTreeModel

abstract class RsMoveNodeInfo {
    abstract val description: String
    abstract val icon: Icon?
    open val children: List<RsMoveNodeInfo> get() = emptyList()

    override fun toString(): String = description
}

class RsMoveMemberSelectionPanel(
    val project: Project,
    title: String,
    nodesAll: List<RsMoveNodeInfo>,
    nodesSelected: List<RsMoveNodeInfo>
) : JPanel() {

    val tree: RsMoveMemberSelectionTree = RsMoveMemberSelectionTree(project, nodesAll, nodesSelected)

    init {
        layout = BorderLayout()
        val scrollPane = ScrollPaneFactory.createScrollPane(tree)
        add(SeparatorFactory.createSeparator(title, tree), BorderLayout.NORTH)
        add(scrollPane, BorderLayout.CENTER)
    }
}

class RsMoveMemberSelectionTree(
    project: Project,
    nodesAll: List<RsMoveNodeInfo>,
    nodesSelected: List<RsMoveNodeInfo>
) : ChangesTreeImpl<RsMoveNodeInfo>(
    project,
    true,
    false,
    RsMoveNodeInfo::class.java
) {

    init {
        setIncludedChanges(nodesSelected)
        setChangesToDisplay(nodesAll)
        TreeUtil.collapseAll(this, 0)
    }

    override fun buildTreeModel(nodeInfos: List<RsMoveNodeInfo>): DefaultTreeModel {
        return RsMoveMemberSelectionModelBuilder(project, grouping).buildTreeModel(nodeInfos)
    }
}

class RsMoveMemberSelectionModelBuilder(
    project: Project,
    grouping: ChangesGroupingPolicyFactory
) : TreeModelBuilder(project, grouping) {

    fun buildTreeModel(nodeInfos: List<RsMoveNodeInfo>): DefaultTreeModel {
        fun addNode(nodeInfo: RsMoveNodeInfo, root: ChangesBrowserNode<*>) {
            val node = RsMoveMemberSelectionNode(nodeInfo)
            myModel.insertNodeInto(node, root, root.childCount)

            val children = nodeInfo.children
            if (children.isNotEmpty()) {
                node.markAsHelperNode()
                for (child in children) {
                    addNode(child, node)
                }
            }
        }

        for (node in nodeInfos) {
            addNode(node, myRoot)
        }
        return build()
    }
}

private class RsMoveMemberSelectionNode(val info: RsMoveNodeInfo) : ChangesBrowserNode<RsMoveNodeInfo>(info) {
    override fun render(renderer: ChangesBrowserNodeRenderer, selected: Boolean, expanded: Boolean, hasFocus: Boolean) {
        super.render(renderer, selected, expanded, hasFocus)
        renderer.icon = info.icon
    }
}
