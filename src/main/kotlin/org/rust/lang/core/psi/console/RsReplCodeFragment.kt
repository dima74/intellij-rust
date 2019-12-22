/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.console

import com.intellij.psi.FileViewProvider
import org.rust.lang.core.psi.RsCodeFragment
import org.rust.lang.core.psi.RsCodeFragmentElementType
import org.rust.lang.core.psi.RsExpr
import org.rust.lang.core.psi.RsStmt
import org.rust.lang.core.psi.ext.*

class RsReplCodeFragment(fileViewProvider: FileViewProvider, override var context: RsElement)
    : RsCodeFragment(fileViewProvider, RsCodeFragmentElementType.REPL, context, false),
      RsInferenceContextOwner, RsItemsOwner {
    val stmts: List<RsStmt> get() = childrenOfType()
    val tailExpr: RsExpr? get() = children.lastOrNull()?.let { it as? RsExpr }
    val namedElements: List<RsNamedElement> get() = childrenOfType()
}
