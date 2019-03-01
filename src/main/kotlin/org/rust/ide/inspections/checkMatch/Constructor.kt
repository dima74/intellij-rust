/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections.checkMatch

import org.rust.lang.core.psi.RsEnumItem
import org.rust.lang.core.psi.RsEnumVariant
import org.rust.lang.core.psi.RsStructItem
import org.rust.lang.core.psi.ext.RsFieldsOwner
import org.rust.lang.core.psi.ext.fieldTypes
import org.rust.lang.core.psi.ext.size
import org.rust.lang.core.psi.ext.variants
import org.rust.lang.core.types.ty.*

sealed class Constructor {

    /** The constructor of all patterns that don't vary by constructor, e.g. struct patterns and fixed-length arrays */
    object Single : Constructor() {
        override fun coveredByRange(from: Constant, to: Constant, included: Boolean): Boolean = true
    }

    /** Enum variants */
    data class Variant(val variant: RsEnumVariant) : Constructor()

    /** Literal values */
    data class ConstantValue(val value: Constant) : Constructor() {
        override fun coveredByRange(from: Constant, to: Constant, included: Boolean): Boolean =
            if (included) {
                value >= from && value <= to
            } else {
                value >= from && value < to
            }
    }

    /** Ranges of literal values (`2..=5` and `2..5`) */
    data class ConstantRange(val start: Constant, val end: Constant, val includeEnd: Boolean = false) : Constructor() {
        override fun coveredByRange(from: Constant, to: Constant, included: Boolean): Boolean =
            if (includeEnd) {
                ((end < to) || (included && to == end)) && (start >= from)
            } else {
                ((end < to) || (!included && to == end)) && (start >= from)
            }
    }

    /** Array patterns of length n */
    data class Slice(val size: Int) : Constructor()

    fun arity(type: Ty): Int = when (type) {
        is TyTuple -> type.types.size

        is TySlice, is TyArray -> when (this) {
            is Constructor.Slice -> size
            is Constructor.ConstantValue -> 0
            else -> throw CheckMatchException("Incompatible constructor")
        }

        is TyReference -> 1

        is TyAdt -> when {
            type.item is RsStructItem -> type.item.size
            type.item is RsEnumItem && this is Variant -> variant.size
            else -> throw CheckMatchException("Incompatible constructor")
        }

        else -> 0
    }

    open fun coveredByRange(from: Constant, to: Constant, included: Boolean): Boolean = false

    fun subTypes(type: Ty): List<Ty> = when (type) {
        is TyTuple -> type.types

        is TySlice, is TyArray -> when (this) {
            is Slice -> (0 until this.size).map { type }
            is ConstantValue -> emptyList()
            else -> throw CheckMatchException("Incompatible constructor")
        }

        is TyReference -> listOf(type.referenced)

        is TyAdt -> when {
            this is Single && type.item is RsFieldsOwner -> type.item.fieldTypes
            this is Variant -> variant.fieldTypes
            else -> emptyList()
        }


        else -> emptyList()
    }

    companion object {
        fun allConstructors(ty: Ty): List<Constructor> =
            when {
                ty is TyBool -> listOf(true, false).map { ConstantValue(Constant.Boolean(it)) }

                ty is TyAdt && ty.item is RsEnumItem -> ty.item.variants.map { Variant(it) }

                // TODO: TyInteger, TyChar (see `all_constructors` at `https://github.com/rust-lang/rust/blob/master/src/librustc_mir/hair/pattern/_match.rs`)
                ty is TyArray && ty.size != null -> TODO()
                ty is TyArray || ty is TySlice -> TODO()

                else -> listOf(Single)
            }
    }
}
