/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.console

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project

abstract class RsConsoleRunnerFactory {

    abstract fun createConsoleRunner(project: Project, contextModule: Module?): RsConsoleRunner

    companion object {
        fun getInstance(): RsConsoleRunnerFactory =
            ApplicationManager.getApplication().getComponent(RsConsoleRunnerFactory::class.java)
    }
}
