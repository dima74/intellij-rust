/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.console

interface RsConsoleRunner {

    fun runSync(requestEditorFocus: Boolean)

    fun run(requestEditorFocus: Boolean)
}
