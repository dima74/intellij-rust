/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.utils

import com.intellij.psi.PsiElement
import org.rust.lang.core.crate.Crate
import org.rust.lang.core.psi.ext.*
import org.rust.lang.utils.evaluation.CfgEvaluator
import org.rust.lang.utils.evaluation.ThreeValuedLogic

val PsiElement.isEnabledByCfg: Boolean
    get() = ancestors.filterIsInstance<RsDocAndAttributeOwner>().all { it.isEnabledByCfgSelf }

fun PsiElement.isEnabledByCfg(crate: Crate): Boolean =
    ancestors.filterIsInstance<RsDocAndAttributeOwner>().all { it.isEnabledByCfgSelf(crate) }

val PsiElement.isCfgUnknown: Boolean
    get() = ancestors.filterIsInstance<RsDocAndAttributeOwner>().any { it.isCfgUnknownSelf }

/** Returns `true` if this attribute is `#[cfg_attr()]` and it is disabled */
val RsAttr.isDisabledCfgAttrAttribute: Boolean
    get() {
        val metaItem = metaItem
        if (metaItem.name != "cfg_attr") return false
        val condition = metaItem.metaItemArgs?.metaItemList?.firstOrNull() ?: return false
        val crate = containingCrate ?: return false
        return CfgEvaluator.forCrate(crate).evaluateCondition(condition) == ThreeValuedLogic.False
    }
