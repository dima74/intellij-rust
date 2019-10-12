package org.rust.ide.console

import com.intellij.execution.process.KillableColoredProcessHandler
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.util.io.BaseOutputReader
import com.intellij.util.ui.UIUtil

import java.nio.charset.Charset

class RsConsoleProcessHandler(process: Process,
                              private val myConsoleView: RsConsoleView,
//                              private val myConsoleCommunication: RsConsoleCommunication,
                              commandLine: String,
                              charset: Charset) : KillableColoredProcessHandler(process, commandLine, charset) {

    init {
        Disposer.register(myConsoleView, Disposable {
            if (!isProcessTerminated) {
                destroyProcess()
            }
        })
    }

    override fun coloredTextAvailable(text: String, attributes: Key<*>) {
        myConsoleView.print(text, attributes)
    }

    override fun closeStreams() {
        doCloseCommunication()
        super.closeStreams()
    }

    override fun isSilentlyDestroyOnClose(): Boolean {
//        return !myConsoleCommunication.isExecuting()
        return true
    }

    override fun shouldKillProcessSoftly(): Boolean {
        return false
    }

    override fun readerOptions(): BaseOutputReader.Options {
        return BaseOutputReader.Options.forMostlySilentProcess()
    }

    private fun doCloseCommunication() {
        UIUtil.invokeAndWaitIfNeeded({
            try {
//                todo
//                myConsoleCommunication.close()
                Thread.sleep(300)
            } catch (e1: Exception) {
                // Ignore
            }
        } as Runnable)
    }
}

