/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

import com.google.common.annotations.VisibleForTesting
import com.intellij.codeInsight.hint.HintManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.rust.ide.actions.macroExpansion.FAILED_TO_EXPAND_MESSAGE
import org.rust.ide.actions.macroExpansion.MacroExpansionViewDetails
import org.rust.ide.actions.macroExpansion.expandMacroForViewWithProgress
import org.rust.ide.actions.macroExpansion.showMacroExpansionPopup
import org.rust.lang.core.psi.RsMacroCall
import org.rust.lang.core.psi.ext.ancestorOrSelf
import org.rust.lang.core.psi.ext.isAncestorOf

abstract class RsShowMacroExpansionIntentionBase(private val expandRecursively: Boolean) :
    RsElementBaseIntentionAction<RsMacroCall>() {

    override fun getFamilyName() = text

    override fun findApplicableContext(project: Project, editor: Editor, element: PsiElement): RsMacroCall? {
        val macroCall = element.ancestorOrSelf<RsMacroCall>() ?: return null
        if (!macroCall.path.isAncestorOf(element) && element != macroCall.excl) return null
        return macroCall
    }

    override fun invoke(project: Project, editor: Editor, ctx: RsMacroCall) {
        val expansionDetails = expandMacroForViewWithProgress(project, ctx, expandRecursively)

        if (expansionDetails != null) {
            showExpansion(project, editor, expansionDetails)
        } else {
            showError(editor)
        }
    }

    /** Progress window cannot be shown in the write action, so it have to be disabled. **/
    override fun startInWriteAction(): Boolean = false

    /**
     * This method is required for testing to avoid actually creating popup and editor.
     * Inspired by [com.intellij.codeInsight.hint.actions.ShowImplementationsAction].
     */
    @VisibleForTesting
    protected open fun showExpansion(project: Project, editor: Editor, expansionDetails: MacroExpansionViewDetails) {
        showMacroExpansionPopup(project, editor, expansionDetails)
    }

    private fun showError(editor: Editor) {
        HintManager.getInstance().showErrorHint(editor, FAILED_TO_EXPAND_MESSAGE)
    }
}

class RsShowRecursiveMacroExpansionIntention : RsShowMacroExpansionIntentionBase(expandRecursively = true) {
    override fun getText() = "Show recursive macro expansion"
}

class RsShowSingleStepMacroExpansionIntention : RsShowMacroExpansionIntentionBase(expandRecursively = false) {
    override fun getText() = "Show single step macro expansion"
}
