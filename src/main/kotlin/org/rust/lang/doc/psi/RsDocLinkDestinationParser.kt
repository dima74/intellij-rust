/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.doc.psi

import com.intellij.lang.LanguageParserDefinitions
import com.intellij.lang.impl.PsiBuilderImpl
import com.intellij.lang.parser.GeneratedParserUtilBase
import com.intellij.lang.parser.GeneratedParserUtilBase._NONE_
import com.intellij.psi.TokenType
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.impl.source.tree.TreeElement
import com.intellij.util.CharTable
import org.rust.lang.RsLanguage
import org.rust.lang.core.lexer.RsLexer
import org.rust.lang.core.parser.RustParser
import org.rust.lang.doc.psi.RsDocElementTypes.DOC_LINK_DEFINITION

/**
 * `fn@mod1::mod2::func#anchor`
 *  ~~~ prefix         ~~~~~~~ suffix
 *     ~~~~~~~~~~~~~~~~ text
 */
private class LinkInfo private constructor(
    val text: CharSequence,
    val prefixLength: Int,
    val suffixLength: Int,
) {
    constructor(text: CharSequence) : this(text, prefixLength = 0, suffixLength = 0)

    fun subSequence(start: Int, end: Int): LinkInfo {
        if (start == 0 && end == text.length) return this
        check(start in 0..end && end <= text.length)
        val newText = text.subSequence(start, end)
        return LinkInfo(newText, prefixLength + start, suffixLength + (text.length - end))
    }

    fun removePrefix(length: Int): LinkInfo = subSequence(length, text.length)
    fun removeSuffix(length: Int): LinkInfo = subSequence(0, text.length - length)
}

/**
 * See `preprocess_link` in `src/librustdoc/passes/collect_intra_doc_links.rs`
 * Algorithm:
 * - Remove backticks
 * - Remove hash suffix
 * - Remove disambiguator prefix/suffix
 * - Parse path using usual parser (it will parse generics if any)
 */
object RsDocLinkDestinationParser {
    fun parse(text: CharSequence, charTable: CharTable): TreeElement {
        val info = createLinkInfo(text) ?: return docDataLeaf(text, charTable)
        return parsePathAndCreateNodes(text, charTable, info) ?: docDataLeaf(text, charTable)
    }

    private fun createLinkInfo(text: CharSequence): LinkInfo? {
        if (text.isEmpty() || text.contains("/")) return null

        return LinkInfo(text)
            .trimBackticks()
            ?.removeHashAnchor()
            ?.removeDisambiguator()
            ?.takeIf { canBeCorrectLink(it.text) }
    }

    // "`func`" -> "func"
    // Remove any number of backticks at the beginning and end.
    // Rustdoc also removes backticks in the middle, but we don't support it
    private fun LinkInfo.trimBackticks(): LinkInfo? {
        var start = 0
        var end = text.length
        while (text[start] == '`') {
            ++start
        }
        if (start == end) return null
        while (text[end - 1] == '`') {
            --end
        }
        return subSequence(start, end)
    }

    // "mod1::mod2#anchor" -> "mod1::mod2"
    private fun LinkInfo.removeHashAnchor(): LinkInfo? {
        val parts = text.split('#')
        if (parts.size > 2) return null  // multiple #'s - invalid link
        if (parts.size < 2) return this  // no anchors

        val link = parts[0]
        val hash = parts[1]

        // anchor to an element of the current page - ignore
        if (link.isBlank()) return null

        return removeSuffix("#".length + hash.length)
    }

    // "fn@func" -> "func"
    // "gen!()" -> "gen"
    private fun LinkInfo.removeDisambiguator(): LinkInfo? {
        val index = text.indexOf('@')
        if (index != -1) {
            val prefix = text.substring(0, index)
            if (prefix !in KNOWN_PREFIXES) return null
            return removePrefix(prefix.length + "@".length)
                .takeIf { it.text.isNotBlank() }
        } else {
            for (suffix in KNOWN_SUFFIXES) {
                if (text.endsWith(suffix) && text.length > suffix.length) {
                    return removeSuffix(suffix.length)
                }
            }
        }
        return this
    }

    private val KNOWN_PREFIXES: Set<String> = hashSetOf(
        "struct", "enum", "trait", "union", "module", "mod", "const", "constant", "static",
        "function", "fn", "method", "derive", "type", "value", "macro", "prim", "primitive"
    )
    private val KNOWN_SUFFIXES: Array<String> = arrayOf("!()", "!{}", "![]", "()", "!")

    private fun canBeCorrectLink(link: CharSequence): Boolean =
        link.all { it.isAlphanumeric() || it in ":_<>, !*&;" }

    private fun Char.isAlphanumeric(): Boolean = Character.isAlphabetic(code) || isDigit()

    private fun parsePathAndCreateNodes(text: CharSequence, charTable: CharTable, info: LinkInfo): TreeElement? {
        val path = parseRsPath(info.text, charTable) ?: return null
        val root = DOC_LINK_DEFINITION.createCompositeNode()
        if (info.prefixLength > 0) {
            val prefix = text.subSequence(0, info.prefixLength)
            root.rawAddChildrenWithoutNotifications(docDataLeaf(prefix, charTable))
        }
        root.rawAddChildrenWithoutNotifications(path)
        if (info.suffixLength > 0) {
            val suffix = text.subSequence(text.length - info.suffixLength, text.length)
            root.rawAddChildrenWithoutNotifications(docDataLeaf(suffix, charTable))
        }
        return root.firstChildNode
    }

    private fun parseRsPath(pathText: CharSequence, charTable: CharTable): TreeElement? {
        val parserDefinition = LanguageParserDefinitions.INSTANCE.forLanguage(RsLanguage)
            ?: error("No parser definition for language $RsLanguage")
        val plainBuilder = PsiBuilderImpl(null, null, parserDefinition, RsLexer(), charTable, pathText, null, null)

        val builder = GeneratedParserUtilBase.adapt_builder_(
            DOC_LINK_DEFINITION,
            plainBuilder,
            RustParser(),
            RustParser.EXTENDS_SETS_
        )

        val rootMarker = GeneratedParserUtilBase.enter_section_(builder, 0, _NONE_, null)

        if (!RustParser.TypePathGenericArgs(builder, 0)) {
            return null
        }

        GeneratedParserUtilBase.exit_section_(builder, 0, rootMarker, DOC_LINK_DEFINITION, true, true, GeneratedParserUtilBase.TRUE_CONDITION)

        val treeBuilt = builder.treeBuilt
        if (treeBuilt.findChildByType(TokenType.ERROR_ELEMENT) != null) return null
        return treeBuilt.firstChildNode as TreeElement
    }

    private fun docDataLeaf(text: CharSequence, charTable: CharTable) =
        LeafPsiElement(RsDocElementTypes.DOC_DATA, charTable.intern(text))
}
