package org.rust.ide.console

import com.intellij.execution.console.ProcessBackedConsoleExecuteActionHandler
import com.intellij.execution.process.ProcessHandler

abstract class RsConsoleExecuteActionHandler(processHandler: ProcessHandler, preserveMarkup: Boolean) : ProcessBackedConsoleExecuteActionHandler(processHandler, preserveMarkup) {
  abstract override fun processLine(line: String)
//  abstract fun checkSingleLine(text: String): Boolean
//  abstract val cantExecuteMessage: String
//  abstract fun canExecuteNow(): Boolean
  abstract var isEnabled: Boolean
//  abstract val consoleCommunication: ConsoleCommunication
//  abstract fun updateConsoleState()
}
