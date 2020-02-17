/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.console

import com.intellij.execution.process.AnsiEscapeDecoder
import com.intellij.execution.process.KillableProcessHandler
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.util.io.BaseOutputReader
import org.rust.cargo.runconfig.RsAnsiEscapeDecoder
import java.nio.charset.Charset

class RsConsoleProcessHandler(
    process: Process,
    private val consoleView: RsConsoleView,
    private val consoleCommunication: RsConsoleCommunication,
    commandLine: String,
    charset: Charset
) : KillableProcessHandler(process, commandLine, charset), AnsiEscapeDecoder.ColoredTextAcceptor {


    private val decoder: AnsiEscapeDecoder = RsAnsiEscapeDecoder()

    init {
        Disposer.register(consoleView, Disposable {
            if (!isProcessTerminated) {
                destroyProcess()
            }
        })
    }

    override fun notifyTextAvailable(text: String, outputType: Key<*>) {
        decoder.escapeText(text, outputType, this)
    }

    override fun coloredTextAvailable(textOriginal: String, attributes: Key<*>) {
        val text = consoleCommunication.processText(textOriginal)
        consoleView.print(text, attributes)
    }

    override fun isSilentlyDestroyOnClose(): Boolean = !consoleCommunication.isExecuting

    override fun shouldKillProcessSoftly(): Boolean = true

    override fun readerOptions(): BaseOutputReader.Options = BaseOutputReader.Options.forMostlySilentProcess()
}

