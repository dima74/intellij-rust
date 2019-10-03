/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.completion

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.editor.EditorModificationUtil
import com.intellij.patterns.ElementPattern
import com.intellij.patterns.PatternCondition
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiElement
import com.intellij.util.ProcessingContext
import org.rust.lang.RsLanguage
import org.rust.lang.core.psi.RsElementTypes

object RsCfgAttributeCompletionProvider : CompletionProvider<CompletionParameters>() {

    private val NAME_OPTIONS: List<String> = listOf(
        "unix",
        "windows",
        "test",
        "debug_assertions"
    )

    private val NAME_VALUE_OPTIONS: List<String> = listOf(
        "target_arch",
        "target_endian",
        "target_env",
        "target_family",
        "target_feature",
        "target_os",
        "target_pointer_width",
        "target_vendor"
    )

    private val OPERATORS: List<String> = listOf(
        "all",
        "any",
        "not"
    )

    override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
        for (option in NAME_OPTIONS) {
            result.addElement(LookupElementBuilder.create(option))
        }

        for (option in NAME_VALUE_OPTIONS) {
            result.addElement(
                LookupElementBuilder.create(option).withInsertHandler { ctx, _ ->
                    if (!ctx.alreadyHasValue) {
                        ctx.document.insertString(ctx.selectionEndOffset, " = \"\"")
                    }
                    EditorModificationUtil.moveCaretRelatively(ctx.editor, 4)
                }
            )
        }

        for (operator in OPERATORS) {
            result.addElement(
                LookupElementBuilder.create(operator).withInsertHandler { ctx, _ ->
                    if (!ctx.alreadyHasCallParens) {
                        ctx.document.insertString(ctx.selectionEndOffset, "()")
                    }
                    EditorModificationUtil.moveCaretRelatively(ctx.editor, 1)
                }
            )
        }
    }

    private val InsertionContext.alreadyHasValue: Boolean
        get() = nextCharIs('=')

    val elementPattern: ElementPattern<PsiElement>
        get() {
            val cfgAttr = PlatformPatterns.psiElement(RsElementTypes.META_ITEM)
                .withParent(PlatformPatterns.psiElement(RsElementTypes.OUTER_ATTR))
                .with(object : PatternCondition<PsiElement>("cfg") {
                    override fun accepts(t: PsiElement, context: ProcessingContext?): Boolean =
                        t.firstChild.text == "cfg"
                })

            val cfgOption = PlatformPatterns.psiElement(RsElementTypes.META_ITEM)
                .withParent(
                    PlatformPatterns.psiElement(RsElementTypes.META_ITEM_ARGS)
                        .withParent(cfgAttr)
                )

            return PlatformPatterns.psiElement()
                .inside(cfgOption)
                .withLanguage(RsLanguage)
        }
}
