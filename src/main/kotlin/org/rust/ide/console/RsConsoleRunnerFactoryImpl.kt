package org.rust.ide.console

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project

// PydevConsoleRunnerFactory
class RsConsoleRunnerFactoryImpl : RsConsoleRunnerFactory() {

    override fun createConsoleRunner(project: Project, contextModule: Module?): RsConsoleRunner {
        return RsConsoleRunnerImpl(project)
    }
}
