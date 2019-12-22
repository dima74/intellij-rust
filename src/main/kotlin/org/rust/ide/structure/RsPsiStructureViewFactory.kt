/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.structure

import com.intellij.ide.structureView.StructureViewBuilder
import com.intellij.ide.structureView.StructureViewModel
import com.intellij.ide.structureView.TreeBasedStructureViewBuilder
import com.intellij.lang.PsiStructureViewFactory
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiFile
import org.rust.lang.core.psi.RsFileBase

class RsPsiStructureViewFactory : PsiStructureViewFactory {
    override fun getStructureViewBuilder(psiFile: PsiFile): StructureViewBuilder {
        return getStructureViewBuilder(psiFile, true)
    }

    fun getStructureViewBuilder(psiFile: PsiFile, isRootNodeShown: Boolean): StructureViewBuilder {
        val rustFile = psiFile as RsFileBase
        return object : TreeBasedStructureViewBuilder() {
            override fun createStructureViewModel(editor: Editor?): StructureViewModel {
                return RsStructureViewModel(editor, rustFile)
            }

            override fun isRootNodeShown(): Boolean {
                return isRootNodeShown
            }
        }
    }
}
