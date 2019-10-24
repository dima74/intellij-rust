/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.console

import com.intellij.execution.console.LanguageConsoleImpl
import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.execution.ui.ObservableConsoleView
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ActionCallback
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.psi.PsiCodeFragment
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.SingleRootFileViewProvider
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.LightVirtualFile
import org.rust.ide.highlight.RsHighlighter
import org.rust.lang.RsLanguage
import org.rust.lang.core.psi.RsFileBase
import org.rust.lang.core.psi.RsStmt
import org.rust.lang.core.psi.ext.RsMod
import javax.swing.JComponent

//class RsReplCodeFragment(project: Project, text: CharSequence, context: RsElement)
//    : RsCodeFragment(project, text, RsCodeFragmentElementType.STMT, context) {
//    val stmt: RsStmt? get() = PsiTreeUtil.getChildOfType(this, RsStmt::class.java)
//}

class RsReplCodeFragment(project: Project, virtualFile: VirtualFile)
    : RsFileBase(SingleRootFileViewProvider(PsiManager.getInstance(project), virtualFile, true)), PsiCodeFragment {

    init {
        (viewProvider as SingleRootFileViewProvider).forceCachedPsi(this)
    }

    override val containingMod: RsMod
        get() = TODO("not implemented")
    override val crateRoot: RsMod?
        get() = null

    override fun forceResolveScope(scope: GlobalSearchScope?) {
        TODO("not implemented")
    }

    override fun getForcedResolveScope(): GlobalSearchScope {
        TODO("not implemented")
    }

    val stmt: RsStmt? get() = PsiTreeUtil.getChildOfType(this, RsStmt::class.java)
}

class RsConsoleViewHelper(project: Project, virtualFile: VirtualFile)
    : LanguageConsoleImpl.Helper(project, virtualFile) {
    override fun getFile(): PsiFile {
        return RsReplCodeFragment(project, virtualFile)
//        GroovyShellCodeFragment(project, virtualFile as LightVirtualFile)
    }
}

class RsConsoleView : LanguageConsoleImpl, ObservableConsoleView {
    private lateinit var myExecuteActionHandler: RsConsoleExecuteActionHandler
    private val myRsHighlighter: RsHighlighter
    private val myScheme: EditorColorsScheme

    private val myInitialized = ActionCallback()
    private var isShowVars: Boolean = false

    //    constructor(project: Project, title: String) : super(project, title, RsLanguage) {
    constructor(project: Project, title: String)
        : super(RsConsoleViewHelper(project, LightVirtualFile("repl.rs", RsLanguage, ""))) {
//        val lightVirtualFile = LightVirtualFile("", RsStatementCodeFragment(), "")

//        todo
//        isShowVars = RsConsoleOptions.getInstance(project).isShowVariableByDefault()
        val virtualFile = virtualFile
        virtualFile.putUserData(RUST_CONSOLE_KEY, true)
        // Mark editor as console one, to prevent autopopup completion
//        todo
//        consoleEditor.putUserData(RustConsoleAutopopupBlockingHandler.REPL_KEY, Any())
//        historyViewer.putUserData(ConsoleViewUtil.EDITOR_IS_CONSOLE_HISTORY_VIEW, true)
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

        return centerComponent
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
        private var RUST_CONSOLE_KEY = Key<Boolean>("RS_CONSOLE_KEY")
    }
}
