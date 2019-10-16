/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.console

class RsConsoleCommunication() {

    var isExecuting: Boolean = false
        private set
    private var hasSkippedFirstLine: Boolean = false

    fun onExecutionBegin() {
        isExecuting = true
        hasSkippedFirstLine = false
    }

    fun processText(text: String): String {
        var text = text.replace("\r", "")
        if (!hasSkippedFirstLine && text.contains('\n')) {
            text = text.replace('\n', '\r')
            hasSkippedFirstLine = true
        }

        if (text == ">> ") {
            isExecuting = false
            return ""
        }
        return text
    }
}
