/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.console

import com.intellij.execution.console.LanguageConsoleImpl
import com.intellij.execution.impl.ConsoleViewUtil
import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.execution.ui.ObservableConsoleView
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ActionCallback
import com.intellij.openapi.util.Key
import com.intellij.openapi.wm.IdeFocusManager
import org.rust.ide.highlight.RsHighlighter
import org.rust.lang.RsLanguage
import javax.swing.JComponent

class RsConsoleView : LanguageConsoleImpl, ObservableConsoleView, RsCodeExecutor {
    private lateinit var myExecuteActionHandler: RsConsoleExecuteActionHandler
    private val myRsHighlighter: RsHighlighter
    private val myScheme: EditorColorsScheme

    private val myInitialized = ActionCallback()
    private var isShowVars: Boolean = false

    constructor(project: Project, title: String) : super(project, title, RsLanguage) {
//        todo
//        isShowVars = RsConsoleOptions.getInstance(project).isShowVariableByDefault()
        val virtualFile = virtualFile
        virtualFile.putUserData(CONSOLE_KEY, true)
        // Mark editor as console one, to prevent autopopup completion
//        todo
//        consoleEditor.putUserData(RustConsoleAutopopupBlockingHandler.REPL_KEY, Any())
        historyViewer.putUserData(ConsoleViewUtil.EDITOR_IS_CONSOLE_HISTORY_VIEW, true)
//        todo
//        super.setPrompt(null)
        super.setPrompt(">> ")
        setUpdateFoldingsEnabled(false)
        myRsHighlighter = RsHighlighter()
        myScheme = consoleEditor.colorsScheme
    }

    fun setExecutionHandler(consoleExecuteActionHandler: RsConsoleExecuteActionHandler) {
        myExecuteActionHandler = consoleExecuteActionHandler
    }

    override fun requestFocus() {
        myInitialized.doWhenDone { IdeFocusManager.getGlobalInstance().requestFocus(consoleEditor.contentComponent, true) }
    }

    override fun createCenterComponent(): JComponent {
        // workaround for extra lines appearing in the console
        val centerComponent = super.createCenterComponent()
        historyViewer.settings.additionalLinesCount = 0
        historyViewer.settings.isUseSoftWraps = false
        consoleEditor.gutterComponentEx.background = consoleEditor.backgroundColor
        consoleEditor.gutterComponentEx.revalidate()
        consoleEditor.colorsScheme.setColor(EditorColors.GUTTER_BACKGROUND, consoleEditor.backgroundColor)

        // settings.set
        return centerComponent
    }

    private fun executeInConsole(code: String) {
        print("Received: '$code'", ProcessOutputTypes.STDERR)
    }

    override fun print(text: String, outputType: ConsoleViewContentType) {
        // todo highlighting
        super.print(text, outputType)
    }

    fun print(text: String, attributes: Key<*>) {
        print(text, outputTypeForAttributes(attributes))
    }

    private fun outputTypeForAttributes(attributes: Key<*>): ConsoleViewContentType {
        return when {
            attributes === ProcessOutputTypes.STDERR -> ConsoleViewContentType.ERROR_OUTPUT
            attributes === ProcessOutputTypes.SYSTEM -> ConsoleViewContentType.SYSTEM_OUTPUT
            else -> ConsoleViewContentType.getConsoleViewType(attributes)
        }
    }

    fun initialized() {
        myInitialized.setDone()
    }

    companion object {
        private var CONSOLE_KEY = Key<Boolean>("RS_CONSOLE_KEY")
    }
}
