/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.presentation

import com.intellij.ide.projectView.PresentationData
import com.intellij.navigation.ItemPresentation
import org.rust.ide.colors.RsColor
import org.rust.ide.icons.addVisibilityIcon
import org.rust.lang.core.macros.isExpandedFromMacro
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.psi.impl.RsPatIdentImpl
import org.rust.lang.core.types.inference

fun getPresentation(psi: RsElement): ItemPresentation {
    val location = run {
        val mod = psi.containingMod
        "(in ${mod.qualifiedName ?: mod.modName ?: psi.containingFile.name})"
    }

    val name = presentableName(psi)
    return PresentationData(name, location, psi.getIcon(0), null)
}

fun getPresentationForStructure(psi: RsElement): ItemPresentation {
    val presentation = buildString {
        fun appendCommaList(xs: List<String>) {
            append('(')
            append(xs.joinToString(", "))
            append(')')
        }
        append(presentableName(psi))
        when (psi) {
            is RsFunction -> {
                appendCommaList(psi.valueParameters.mapNotNull { it.typeReference?.getStubOnlyText() })

                val ret = psi.retType?.typeReference
                if (ret != null) append(" -> ${ret.getStubOnlyText()}")
            }
            is RsConstant -> {
                psi.typeReference?.let { append(": ${it.getStubOnlyText()}") }
            }
            is RsNamedFieldDecl -> {
                psi.typeReference?.let { append(": ${it.getStubOnlyText()}") }
            }
            is RsLetDecl -> {
                val typeReference = psi.typeReference
                if (typeReference != null) {
                    append(": ${typeReference.getStubOnlyText()}")
                } else {
                    val inference = psi.inference
                    val pat = psi.pat
                    if (inference != null && pat != null) {
                        append(": ${inference.getPatType(pat)}")
                    }
                }
            }
            is RsEnumVariant -> {
                val fields = psi.tupleFields
                if (fields != null) {
                    appendCommaList(fields.tupleFieldDeclList.map { it.typeReference.getStubOnlyText() })
                }
            }
        }
    }
    var icon = psi.getIcon(0)
    if ((psi as? RsVisibilityOwner)?.isPublic == true) {
        icon = icon.addVisibilityIcon(true)
    }

    val textAttributes = if (psi.isExpandedFromMacro) RsColor.GENERATED_ITEM.textAttributesKey else null

    return PresentationData(presentation, null, icon, textAttributes)
}

private fun presentableName(psi: RsElement): String? {
    return when (psi) {
        is RsNamedElement -> psi.name
        is RsImplItem -> {
            val type = psi.typeReference?.text ?: return null
            val trait = psi.traitRef?.text
            buildString {
                if (trait != null) {
                    append("$trait for ")
                }
                append(type)
                append(typeParameterBounds(psi))
            }
        }
        is RsLetDecl -> (psi.pat as? RsPatIdentImpl)?.patBinding?.name
        else -> null
    }
}

private fun typeParameterBounds(impl: RsImplItem): String {
    val allBounds = impl.typeParameters.mapNotNull { param ->
        val name = param.name ?: return@mapNotNull null
        val bounds = param.bounds.mapNotNull inner@{
            val bound = it.bound.traitRef?.path?.referenceName ?: return@inner null
            if (it.hasQ) "?$bound" else bound
        }
        if (bounds.isNotEmpty()) bounds.joinToString(prefix = "$name: ", separator = " + ") else null
    }
    return if (allBounds.isNotEmpty()) allBounds.joinToString(prefix = " where ", separator = ", ") else ""
}

val RsDocAndAttributeOwner.presentableQualifiedName: String?
    get() {
        val qName = (this as? RsQualifiedNamedElement)?.qualifiedName
        if (qName != null) return qName
        if (this is RsMod) return modName
        return name
    }

fun breadcrumbName(e: RsElement): String? {
    fun lastComponentWithoutGenerics(path: RsPath) = path.referenceName

    return when (e) {
        is RsMacro -> e.name?.let { "$it!" }

        is RsModItem, is RsStructOrEnumItemElement, is RsTraitItem, is RsConstant, is RsTypeAlias ->
            (e as RsNamedElement).name

        is RsImplItem -> {
            val typeName = run {
                val typeReference = e.typeReference
                (typeReference?.typeElement as? RsBaseType)?.path?.let { lastComponentWithoutGenerics(it) }
                    ?: typeReference?.text
                    ?: return null
            }

            val traitName = e.traitRef?.path?.let { lastComponentWithoutGenerics(it) }
            val start = if (traitName != null) "$traitName for" else "impl"
            "$start $typeName"
        }

        is RsFunction -> e.name?.let { "$it()" }
        else -> null
    }
}
