/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.console

import com.intellij.execution.configurations.PtyCommandLine
import com.intellij.execution.process.KillableColoredProcessHandler
import com.intellij.openapi.util.Key
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Test
import java.io.PrintWriter
import java.nio.charset.StandardCharsets
import java.util.*
import java.util.concurrent.LinkedBlockingQueue

class RsConsoleTest : BasePlatformTestCase() {
    @Test
    fun testProcessOutputFormat() {
        val process = createProcess()
        val scanner = Scanner(process.inputStream)
        val writer = PrintWriter(process.outputStream)

        assertEquals(scanner.nextLine(), "Welcome to evcxr. For help, type :help")
        assertEquals(scanner.nextLine(), "\u001b[?2004h" + "\u001b[6n")
        assertEquals(scanner.nextLine(), "\u001b[0K" + ">> ")

        writer.println("1 + 2")
        writer.flush()

        assertEquals(scanner.nextLine(), "\u001b[3C" + "1 + 2" + "\u001b[?2004l")
        assertEquals(scanner.nextLine(), "3")
        assertEquals(scanner.nextLine(), "\u001b[?2004h" + "\u001b[6n")
        assertEquals(scanner.nextLine(), "\u001b[0K" + ">> ")

        process.destroy()
    }

    @Test
    fun testProcessHandler() {
        val process = createProcess()
        val queue = LinkedBlockingQueue<String>()
        val processHandler = object : KillableColoredProcessHandler(process, executable) {
            override fun coloredTextAvailable(text: String, attributes: Key<*>) {
                super.coloredTextAvailable(text, attributes)
                queue.put(text)
            }
        }
        processHandler.startNotify()
        val writer = PrintWriter(process.outputStream)

        assertEquals(queue.take(), executable + "\n")
        assertEquals(queue.take(), "Welcome to evcxr. For help, type :help\n")
        assertEquals(queue.take(), "\r")
        assertEquals(queue.take(), ">> \r")

        writer.println("1 + 2")
//        writer.println("+")
        writer.flush()

//        while (true) {
//            val line = queue.take()
//            print(line)
//        }

        assertEquals(queue.take(), "1 + 2")
        assertEquals(queue.take(), "\n")
        assertEquals(queue.take(), "3\n")
        assertEquals(queue.take(), "\r")
        assertEquals(queue.take(), ">> \r")

        processHandler.destroyProcess()
    }

    private fun createProcess(): Process {
        val arguments = listOf(executable)
        val commandLine = PtyCommandLine(arguments)
            .withInitialColumns(PtyCommandLine.MAX_COLUMNS)
            .withConsoleMode(false)
            .withCharset(StandardCharsets.UTF_8)
        val process = commandLine.createProcess()
        return process
    }

    companion object {
        const val executable = "/home/dima/.cargo/bin/evcxr"
    }
}
