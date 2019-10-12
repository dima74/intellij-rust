/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.console

import com.intellij.execution.ExecutionException
import com.intellij.execution.ExecutionManager
import com.intellij.execution.Executor
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.console.ConsoleExecuteAction
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.execution.process.ProcessTerminatedListener
import com.intellij.execution.runners.AbstractConsoleRunnerWithHistory.registerActionShortcuts
import com.intellij.execution.runners.ConsoleTitleGen
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.TransactionGuard
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.encoding.EncodingProjectManager
import com.intellij.ui.JBColor
import com.intellij.ui.SideBorder
import com.intellij.util.ui.JBEmptyBorder
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.nio.charset.StandardCharsets
import javax.swing.BorderFactory
import javax.swing.JPanel

class RsConsoleRunnerImpl : RsConsoleRunner {

    private val myProject: Project
    private val myTitle: String

    private lateinit var myProcessHandler: RsConsoleProcessHandler
    private lateinit var myConsoleExecuteActionHandler: RsConsoleExecuteActionHandler

    // Console title used during initialization, it can be changed with Rename action
    private var myConsoleInitTitle: String? = null
    private lateinit var myConsoleView: RsConsoleView

    constructor(project: Project) {
        myProject = project
        myTitle = "Rust REPL"
    }

    private fun createConsoleView(): RsConsoleView {
        return RsConsoleView(myProject, myTitle)
    }

    private fun fillRunActionsToolbar(toolbarActions: DefaultActionGroup): MutableList<AnAction> {
        val actions = ArrayList<AnAction>()
        actions.add(
            ConsoleExecuteAction(myConsoleView, myConsoleExecuteActionHandler, myConsoleExecuteActionHandler.emptyExecuteAction,
                myConsoleExecuteActionHandler))
        toolbarActions.addAll(actions)
        return actions
    }

    private fun fillOutputActionsToolbar(toolbarActions: DefaultActionGroup): List<AnAction> {
        val actions = ArrayList<AnAction>()

        // todo

        // Console History
//        actions.add(ConsoleHistoryController.getController(myConsoleView!!).browseHistory)
        toolbarActions.addAll(actions)
        return actions
    }

    protected fun showContentDescriptor(contentDescriptor: RunContentDescriptor) {
        val toolWindow = RsConsoleToolWindow.getToolWindow(myProject)
        if (toolWindow != null) {
            toolWindow.component.putClientProperty(STARTED_BY_RUNNER, "true")
            RsConsoleToolWindow.getInstance(myProject).init(toolWindow, contentDescriptor)
        } else {
            ExecutionManager
                .getInstance(myProject).contentManager.showRunContent(getExecutor(), contentDescriptor)
        }
    }

    private fun createContentDescriptorAndActions() {
        val runToolbarActions = DefaultActionGroup()
        val runActionsToolbar = ActionManager.getInstance().createActionToolbar("RustConsoleRunner", runToolbarActions, false)

        val outputToolbarActions = DefaultActionGroup()
        val outputActionsToolbar = ActionManager.getInstance().createActionToolbar("RustConsoleRunner", outputToolbarActions, false)

        val actionsPanel = JPanel(BorderLayout())
        // Left toolbar panel
        actionsPanel.add(runActionsToolbar.component, BorderLayout.WEST)
        // Add line between toolbar panels
        val outputActionsComponent = outputActionsToolbar.component
        val emptyBorderSize = outputActionsComponent.border.getBorderInsets(outputActionsComponent).left
        outputActionsComponent.border = BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 1, 0, 0, JBColor.border()),
            JBEmptyBorder(emptyBorderSize)
        )
        // Right toolbar panel
        actionsPanel.add(outputActionsComponent, BorderLayout.CENTER)

        val mainPanel = JPanel(BorderLayout())
        mainPanel.add(actionsPanel, BorderLayout.WEST)
        mainPanel.add(myConsoleView.component, BorderLayout.CENTER)

        runActionsToolbar.setTargetComponent(mainPanel)
        outputActionsToolbar.setTargetComponent(mainPanel)

        if (myConsoleInitTitle == null) {
            myConsoleInitTitle = object : ConsoleTitleGen(myProject, myTitle) {
                override fun getActiveConsoles(consoleTitle: String): List<String> {
                    val toolWindow = RsConsoleToolWindow.getInstance(myProject)
                    return if (toolWindow != null && toolWindow.isInitialized && toolWindow.toolWindow != null) {
                        toolWindow.toolWindow.contentManager.contents
                            .map { c -> c.displayName }
                            .filter { s -> s.startsWith(myTitle) }
                    } else {
                        super.getActiveConsoles(consoleTitle)
                    }
                }
            }.makeTitle()
        }

        val contentDescriptor = RunContentDescriptor(myConsoleView, /* todo */null, mainPanel, myConsoleInitTitle, null)
        Disposer.register(myProject, contentDescriptor)

        contentDescriptor.setFocusComputable { myConsoleView.consoleEditor.contentComponent }
        contentDescriptor.isAutoFocusContent = true

        // tool bar actions
        val actions = fillRunActionsToolbar(runToolbarActions)
        val outputActions = fillOutputActionsToolbar(outputToolbarActions)
        actions.addAll(outputActions)

        registerActionShortcuts(actions, myConsoleView.consoleEditor.component)
        registerActionShortcuts(actions, mainPanel)

        showContentDescriptor(contentDescriptor)
    }

    override fun runSync(requestEditorFocus: Boolean) {
        try {
            initAndRun()
            ProgressManager.getInstance().run(object : Task.Backgroundable(myProject, "Connecting to Console", false) {
                override fun run(indicator: ProgressIndicator) {
                    indicator.text = "Connecting to console..."
                    connect()
                    if (requestEditorFocus) {
                        myConsoleView.requestFocus()
                    }
                }
            })
        } catch (e: ExecutionException) {
            LOG.warn("Error running console", e)
            showErrorsInConsole(e)
        }
    }

    override fun run(requestEditorFocus: Boolean) {
        TransactionGuard.submitTransaction(myProject, Runnable { FileDocumentManager.getInstance().saveAllDocuments() })

        ApplicationManager.getApplication().executeOnPooledThread {
            ProgressManager.getInstance().run(object : Task.Backgroundable(myProject, "Connecting to Console", false) {
                override fun run(indicator: ProgressIndicator) {
                    indicator.text = "Connecting to console..."
                    try {
                        initAndRun()
                        connect()
                        if (requestEditorFocus) {
                            myConsoleView!!.requestFocus()
                        }
                    } catch (e: Exception) {
                        LOG.warn("Error running console", e)
//                        todo
//                        UIUtil.invokeAndWaitIfNeeded({ showErrorsInConsole(e) } as Runnable)
                    }

                }
            })
        }
    }

    private fun showErrorsInConsole(e: Exception) {
        TODO()
    }

    private fun initAndRun() {
        val commandLineProcess = createProcess()
        val process = commandLineProcess.process
        UIUtil.invokeAndWaitIfNeeded(Runnable {
            // Init console view
            myConsoleView = createConsoleView()
            myConsoleView.border = SideBorder(JBColor.border(), SideBorder.LEFT)
            myProcessHandler = RsConsoleProcessHandler(process, myConsoleView, /*myConsoleCommunication, */commandLineProcess.commandLine, StandardCharsets.UTF_8)

            myConsoleExecuteActionHandler = createExecuteActionHandler()

            ProcessTerminatedListener.attach(myProcessHandler)

            myProcessHandler.addProcessListener(object : ProcessAdapter() {
                override fun processTerminated(event: ProcessEvent) {
                    myConsoleView.isEditable = false
                }
            })

            // Attach to process
            myConsoleView.attachToProcess(myProcessHandler)
            createContentDescriptorAndActions()

            // Run
            myProcessHandler.startNotify()
        })
    }

    private fun createProcess(): CommandLineProcess {
        // todo
        val generalCommandLine = GeneralCommandLine("/home/dima/.cargo/bin/evcxr")
        generalCommandLine.withCharset(EncodingProjectManager.getInstance(myProject).defaultCharset)
//        generalCommandLine.withWorkDirectory(myWorkingDir)

//        myConsoleCommunication = RsConsoleCommunication(myProject)
//        try {
//            myConsoleCommunication.serve()
//        } catch (e: Exception) {
//            myConsoleCommunication.close()
//            throw ExecutionException(e.message, e)
//        }

        val process = generalCommandLine.createProcess()
        return CommandLineProcess(process, generalCommandLine.commandLineString)
    }

    private fun connect() {
        if (handshake()) {
            ApplicationManager.getApplication().invokeLater {
                // Propagate console communication to language console
                val consoleView = myConsoleView!!

//                consoleView.setConsoleCommunication(myPydevConsoleCommunication)
                consoleView.setExecutionHandler(myConsoleExecuteActionHandler)
//                myProcessHandler.addProcessListener(object : ProcessAdapter() {
//                    override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
//                        consoleView.print(event.text, outputType)
//                    }
//                })

                myConsoleExecuteActionHandler.isEnabled = true

                consoleView.initialized()
            }
        } else {
            myConsoleView.print("Couldn't connect to console process.", ProcessOutputTypes.STDERR)
            myConsoleView.isEditable = false
        }
    }

    protected fun createExecuteActionHandler(): RsConsoleExecuteActionHandler {
        val consoleExecuteActionHandler = RsConsoleExecuteActionHandlerImpl(myConsoleView, myProcessHandler)
        consoleExecuteActionHandler.isEnabled = false
//        ConsoleHistoryController(PyConsoleRootType.Companion.getInstance(), "", myConsoleView).install()
        return consoleExecuteActionHandler
    }

    // todo remove
    private fun handshake(): Boolean {
//        return myPydevConsoleCommunication.handshake()
        return true
    }

    companion object {
        const val STARTED_BY_RUNNER = "startedByRunner"
        val LOG = Logger.getInstance(RsConsoleRunnerImpl::class.java)

        private fun getExecutor(): Executor {
            return DefaultRunExecutor.getRunExecutorInstance()
        }
    }
}

private class CommandLineProcess internal constructor(val process: Process, val commandLine: String)
