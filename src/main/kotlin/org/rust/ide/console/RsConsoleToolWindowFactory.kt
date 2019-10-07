/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.console

import com.intellij.openapi.application.TransactionGuard
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory

class RsConsoleToolWindowFactory : ToolWindowFactory, DumbAware {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val isStartedFromRunner = toolWindow.component.getClientProperty(RsConsoleRunnerImpl.STARTED_BY_RUNNER)
        // we need it to distinguish Console toolwindows started by Console Runner from ones started by toolwindow activation
        if (isStartedFromRunner != "true") {
            val runner = RsConsoleRunnerFactory.getInstance().createConsoleRunner(project, null)
            TransactionGuard.submitTransaction(project, Runnable { runner.runSync(true) })
        }
    }

    companion object {
        const val ID: String = "Rust Console"
    }
}
