/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.console

import com.intellij.codeInsight.hint.HintManager
import com.intellij.execution.console.LanguageConsoleView
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.ui.ConsoleViewContentType

class RsConsoleExecuteActionHandlerImpl(private val myConsoleView: LanguageConsoleView,
                                        processHandler: ProcessHandler) :
    RsConsoleExecuteActionHandler(processHandler, false) {
    private val project = myConsoleView.project

    override var isEnabled: Boolean = false

    override fun processLine(line: String) {
        myConsoleView.print("Received: '$line'", ConsoleViewContentType.SYSTEM_OUTPUT)
    }

    override fun runExecuteAction(console: LanguageConsoleView) {
        if (!isEnabled) {
            HintManager.getInstance().showErrorHint(console.consoleEditor, consoleIsNotEnabledMessage)
            return
        }

        copyToHistoryAndExecute(console)
    }

    private fun copyToHistoryAndExecute(console: LanguageConsoleView) = super.runExecuteAction(console)

    companion object {
        const val consoleIsNotEnabledMessage = "Console is not enabled."
    }
}
