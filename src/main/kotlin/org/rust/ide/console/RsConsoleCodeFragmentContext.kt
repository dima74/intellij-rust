/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.console

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFileFactory
import com.intellij.ui.GuiUtils
import org.rust.cargo.project.model.cargoProjects
import org.rust.lang.RsLanguage
import org.rust.lang.core.psi.RsBlock
import org.rust.lang.core.psi.RsFile
import org.rust.lang.core.psi.RsPsiFactory
import org.rust.lang.core.psi.console.RsReplCodeFragment
import org.rust.lang.core.psi.ext.descendantOfTypeStrict
import org.rust.lang.core.psi.rustFile
import org.rust.openapiext.document
import org.rust.openapiext.toPsiFile

class RsConsoleCodeFragmentContext(project: Project) {

    private val topLevelElements: MutableMap<String, String> = mutableMapOf()
    private val allCommandsText: StringBuilder = StringBuilder()
    val variablesFile: RsReplCodeFragment = PsiFileFactory.getInstance(project)
        .createFileFromText(RsConsoleView.VIRTUAL_FILE_NAME, RsLanguage, "") as RsReplCodeFragment

    fun addToContext(lastCommandContext: RsConsoleOneCommandContext) {
        topLevelElements.putAll(lastCommandContext.topLevelElements)
        allCommandsText.append(lastCommandContext.statementsText)
    }

    fun updateContext(project: Project, codeFragment: RsReplCodeFragment) {
        val allCommandsText = getAllCommandsText()

        GuiUtils.invokeLaterIfNeeded({
            ApplicationManager.getApplication().runWriteAction {
                variablesFile.virtualFile.document?.setText(allCommandsText)
                codeFragment.context = createContext(project, codeFragment.crateRoot as RsFile?, allCommandsText)
            }
        }, ModalityState.defaultModalityState())
    }

    private fun getAllCommandsText(): String {
        val topLevelElementsText = topLevelElements.values.joinToString("\n")
        return topLevelElementsText + allCommandsText
    }

    companion object {
        fun createContext(project: Project, originalCrateRoot: RsFile?, allCommandsText: String = ""): RsBlock {
            val rsFile = RsPsiFactory(project).createFile("fn main() { $allCommandsText }")

            val crateRoot = originalCrateRoot ?: findAnyCrateRoot(project)
            crateRoot?.let { rsFile.originalFile = crateRoot }

            return rsFile.descendantOfTypeStrict()!!
        }

        private fun findAnyCrateRoot(project: Project): RsFile? {
            val cargoProject = project.cargoProjects.allProjects.first()
            val crateRoot = cargoProject.workspace?.packages?.firstOrNull()?.targets?.firstOrNull()?.crateRoot
            return crateRoot?.toPsiFile(project)?.rustFile
        }
    }
}

class RsConsoleOneCommandContext(codeFragment: RsReplCodeFragment) {
    val topLevelElements: MutableMap<String, String> = mutableMapOf()
    val statementsText: String

    init {
        for (namedElement in codeFragment.namedElements) {
            val name = namedElement.name ?: continue
            topLevelElements[name] = namedElement.text
        }

        statementsText = codeFragment.stmts.joinToString("\n") { it.text }
    }
}
