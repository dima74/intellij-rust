/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.refactoring.move

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.refactoring.BaseRefactoringProcessor
import com.intellij.refactoring.move.MoveMultipleElementsViewDescriptor
import com.intellij.usageView.UsageInfo
import com.intellij.usageView.UsageViewDescriptor
import com.intellij.util.containers.MultiMap
import org.rust.ide.refactoring.move.common.ElementToMove
import org.rust.ide.refactoring.move.common.RsMoveCommonProcessor
import org.rust.lang.core.psi.RsModItem
import org.rust.lang.core.psi.RsPsiFactory
import org.rust.lang.core.psi.ext.RsItemElement
import org.rust.lang.core.psi.ext.RsMod
import org.rust.lang.core.psi.ext.expandedItemsExceptImplsAndUses
import org.rust.lang.core.psi.ext.startOffset

// see overview of move refactoring in comment for `RsMoveCommonProcessor`
class RsMoveTopLevelItemsProcessor(
    private val project: Project,
    private val itemsToMove: Set<RsItemElement>,
    private val targetMod: RsMod,
    private val searchForReferences: Boolean,
    // todo remove, needed only for random move action
    private val throwOnConflicts: Boolean = false
) : BaseRefactoringProcessor(project) {

    private val commonProcessor: RsMoveCommonProcessor = run {
        val elementsToMove = itemsToMove.map { ElementToMove.fromItem(it) }
        RsMoveCommonProcessor(project, elementsToMove, targetMod)
    }

    override fun findUsages(): Array<out UsageInfo> {
        if (!searchForReferences) return UsageInfo.EMPTY_ARRAY
        return commonProcessor.findUsages()
    }

    private fun checkNoItemsWithSameName(conflicts: MultiMap<PsiElement, String>) {
        if (!searchForReferences) return
        val targetModItems = targetMod.expandedItemsExceptImplsAndUses.associateBy { it.name }
        for (item in itemsToMove) {
            val name = item.name ?: continue
            val targetModItem = targetModItems[name]
            // actually it is allowed to e.g. have function, struct and child mod with same name in one file
            // but not allowed to e.g. have struct and enum with same name
            // so this check is not very accurate
            if (targetModItem != null) {
                conflicts.putValue(targetModItem, "Target file already contains item with name $name")
            }
        }
    }

    override fun preprocessUsages(refUsages: Ref<Array<UsageInfo>>): Boolean {
        val usages = refUsages.get()
        val conflicts = MultiMap<PsiElement, String>()
        checkNoItemsWithSameName(conflicts)
        return commonProcessor.preprocessUsages(usages, conflicts) && showConflicts(conflicts, usages)
    }

    override fun showConflicts(conflicts: MultiMap<PsiElement, String>, usages: Array<out UsageInfo>?): Boolean {
        if (throwOnConflicts && !conflicts.isEmpty) {
            throw ConflictsInTestsException(conflicts.values())
        }
        return super.showConflicts(conflicts, usages)
    }

    override fun performRefactoring(usages: Array<out UsageInfo>) {
        commonProcessor.performRefactoring(usages, this::moveItems)
    }

    private fun moveItems(): List<ElementToMove> {
        val psiFactory = RsPsiFactory(project)
        return itemsToMove
            .sortedBy { it.startOffset }
            .map { item -> moveItem(item, psiFactory) }
    }

    private fun moveItem(item: RsItemElement, psiFactory: RsPsiFactory): ElementToMove {
        commonProcessor.updateMovedItemVisibility(item)

        if (targetMod.lastChildInner !is PsiWhiteSpace) {
            targetMod.addInner(psiFactory.createNewline())
        }
        val targetModLastWhiteSpace = targetMod.lastChildInner as? PsiWhiteSpace

        val space = (item.prevSibling as? PsiWhiteSpace) ?: (item.nextSibling as? PsiWhiteSpace)
        // have to call `copy` because of rare suspicious PsiInvalidElementAccessException
        val itemNew = targetMod.addBefore(item.copy(), targetModLastWhiteSpace) as RsItemElement
        targetMod.addBefore(space?.copy() ?: psiFactory.createNewline(), itemNew)

        space?.delete()
        item.delete()

        return ElementToMove.fromItem(itemNew)
    }

    override fun createUsageViewDescriptor(usages: Array<out UsageInfo>): UsageViewDescriptor =
        MoveMultipleElementsViewDescriptor(itemsToMove.toTypedArray(), targetMod.name ?: "")

    override fun getCommandName(): String = "Move items"
}

// like `PsiElement::add`, but works correctly for `RsModItem`
fun RsMod.addInner(element: PsiElement): PsiElement =
    addBefore(element, if (this is RsModItem) rbrace else null)

val RsMod.lastChildInner: PsiElement? get() = if (this is RsModItem) rbrace?.prevSibling else lastChild
