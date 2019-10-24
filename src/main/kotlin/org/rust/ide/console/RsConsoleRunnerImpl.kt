/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.console

import com.intellij.execution.ExecutionException
import com.intellij.execution.ExecutionManager
import com.intellij.execution.Executor
import com.intellij.execution.configurations.PtyCommandLine
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
import com.intellij.ui.JBColor
import com.intellij.ui.SideBorder
import com.intellij.util.ui.JBEmptyBorder
import com.intellij.util.ui.UIUtil
import org.rust.cargo.project.model.cargoProjects
import org.rust.cargo.runconfig.command.workingDirectory
import org.rust.openapiext.withWorkDirectory
import java.awt.BorderLayout
import java.nio.charset.StandardCharsets
import javax.swing.BorderFactory
import javax.swing.JPanel

class RsConsoleRunnerImpl : RsConsoleRunner {

    private val myProject: Project
    private val myTitle: String

    private lateinit var myConsoleCommunication: RsConsoleCommunication
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
        // rerun
        // stop
        // new window

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
                            myConsoleView.requestFocus()
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
        myConsoleCommunication = RsConsoleCommunication()
        UIUtil.invokeAndWaitIfNeeded(Runnable {
            // Init console view
            myConsoleView = createConsoleView()
            myConsoleView.border = SideBorder(JBColor.border(), SideBorder.LEFT)
            myProcessHandler = RsConsoleProcessHandler(process, myConsoleView, myConsoleCommunication, commandLineProcess.commandLine, StandardCharsets.UTF_8)

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
//        todo
//        val arguments = listOf("/home/dima/.cargo/bin/evcxr")
        val arguments = listOf("/home/dima/repl/evcxr/target/debug/evcxr", "--disable-readline")
        val myWorkingDir = myProject.cargoProjects.allProjects.firstOrNull()?.workingDirectory

        val commandLine = PtyCommandLine(arguments)
            .withInitialColumns(PtyCommandLine.MAX_COLUMNS)
            .withConsoleMode(true)
            .withCharset(StandardCharsets.UTF_8)
            .withWorkDirectory(myWorkingDir)

        val process = commandLine.createProcess()
        return CommandLineProcess(process, commandLine.commandLineString)
    }

    private fun connect() {
        if (handshake()) {
            ApplicationManager.getApplication().invokeLater {
                myConsoleView.setExecutionHandler(myConsoleExecuteActionHandler)

                myConsoleExecuteActionHandler.isEnabled = true

                myConsoleView.initialized()
            }
        } else {
            myConsoleView.print("Couldn't connect to console process.", ProcessOutputTypes.STDERR)
            myConsoleView.isEditable = false
        }
    }

    private fun createExecuteActionHandler(): RsConsoleExecuteActionHandler {
        val consoleExecuteActionHandler = RsConsoleExecuteActionHandler(myProcessHandler, myConsoleCommunication, myConsoleView)
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
