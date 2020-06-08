/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.refactoring.move

import com.intellij.psi.PsiElement
import com.intellij.psi.util.parentsWithSelf
import com.intellij.refactoring.util.CommonRefactoringUtil
import com.intellij.refactoring.util.RefactoringUIUtil
import com.intellij.util.containers.MultiMap
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.resolve.knownItems
import org.rust.lang.core.types.ty.Ty
import org.rust.lang.core.types.ty.TyAdt
import org.rust.lang.core.types.ty.TyReference
import org.rust.lang.core.types.type

class RsMoveConflictsDetector(
    private val conflicts: MultiMap<PsiElement, String>,
    private val elementsToMove: List<ElementToMove>,
    private val sourceMod: RsMod,
    private val targetMod: RsMod
) {

    val itemsToMakePublic: MutableSet<RsElement> = mutableSetOf()

    fun detectInsideReferencesVisibilityProblems(insideReferences: List<RsMoveReferenceInfo>) {
        for (reference in insideReferences) {
            val pathOld = reference.pathOldOriginal
            if (reference.pathNewAccessible == null) {
                addVisibilityConflict(conflicts, pathOld, reference.target)
            }

            val usageMod = pathOld.containingMod
            val isSelfReference = pathOld.isInsideMovedElements(elementsToMove)
            if (!isSelfReference && !usageMod.superMods.contains(targetMod)) {
                itemsToMakePublic.add(reference.target)
            }
        }

        detectPrivateFieldOrMethodInsideReferences()
    }

    fun detectOutsideReferencesVisibilityProblems(outsideReferences: List<RsMoveReferenceInfo>) {
        for (reference in outsideReferences) {
            if (reference.pathNewAccessible == null) {
                addVisibilityConflict(conflicts, reference.pathOldOriginal, reference.target)
            }
        }

        detectPrivateFieldOrMethodOutsideReferences()
    }

    // for now we check only references from `sourceMod`
    // because for checking all references we should find them,
    // and it would very slow when moving e.g. many files
    private fun detectPrivateFieldOrMethodInsideReferences() {
        val movedElementsShallowDescendants = movedElementsShallowDescendantsOfType<RsElement>(elementsToMove).toSet()
        val sourceFile = sourceMod.containingFile
        val elementsToCheck = sourceFile.descendantsOfType<RsElement>() - movedElementsShallowDescendants

        // todo ? copy element to tmp mod (children of targetMod) and just call `isVisible`?
        fun checkVisibility(referenceElement: RsElement, target: RsVisible) {
            // it is enough to check only shallow descendants,
            // because if something in deep descendants is private,
            // then it was not accessible before move
            if (target !in movedElementsShallowDescendants) return
            // Enum variants in a pub enum are public by default
            val isEnumVariant = target is RsNamedFieldDecl && target.parent.parent is RsEnumVariant
            // Associated items in a pub Trait are public by default
            val isTraitMethodOrAssociatedItem = target.parent is RsMembers
            // todo restricted visibility
            if (target.visibility == RsVisibility.Private && !isEnumVariant && !isTraitMethodOrAssociatedItem) {
                addVisibilityConflict(conflicts, referenceElement, target)
            }
        }
        detectPrivateFieldOrMethodReferences(elementsToCheck.asSequence(), ::checkVisibility)
    }

    private fun detectPrivateFieldOrMethodOutsideReferences() {
        fun checkVisibility(referenceElement: RsElement, target: RsVisible) {
            if (!target.isInsideMovedElements(elementsToMove) && !target.isVisibleFrom(targetMod)) {
                addVisibilityConflict(conflicts, referenceElement, target)
            }
        }

        val elementsToCheck = movedElementsDeepDescendantsOfType<RsElement>(elementsToMove)
        detectPrivateFieldOrMethodReferences(elementsToCheck, ::checkVisibility)
    }

    private fun detectPrivateFieldOrMethodReferences(
        elementsToCheck: Sequence<RsElement>,
        checkVisibility: (RsElement, RsVisible) -> Unit
    ) {
        fun checkVisibility(reference: RsReferenceElement) {
            val target = reference.reference?.resolve() as? RsVisible ?: return
            checkVisibility(reference, target)
        }

        loop@ for (element in elementsToCheck) {
            when (element) {
                is RsDotExpr -> {
                    val fieldReference = element.fieldLookup ?: element.methodCall ?: continue@loop
                    checkVisibility(fieldReference)
                }
                is RsStructLiteralField -> {
                    val field = element.resolveToDeclaration() ?: continue@loop
                    checkVisibility(element, field)
                }
                is RsPatField -> {
                    val patBinding = element.patBinding ?: continue@loop
                    checkVisibility(patBinding)
                }
                is RsPatTupleStruct -> {
                    // it is ok to use `resolve` and not `deepResolve` here
                    // because type aliases can't be used in destructuring tuple struct:
                    val struct = element.path.reference?.resolve() as? RsStructItem ?: continue@loop
                    val fields = struct.tupleFields?.tupleFieldDeclList ?: continue@loop
                    for (field in fields) {
                        checkVisibility(element, field)
                    }
                }
                is RsPath -> {
                    // conflicts for simple paths are handled using `pathNewAccessible`/`pathNewFallback` machinery
                    val isInsideSimplePath = element.parentsWithSelf
                        .takeWhile { it is RsPath }
                        .any { isSimplePath(it as RsPath) }
                    if (!isInsideSimplePath && element.basePath().text != "self" /* todo */) {
                        // here we handle e.g. UFCS paths: `Struct1::method1`
                        checkVisibility(element)
                    }
                }
            }
        }
    }

    /*
     * Rules for inherent impls:
     * - An implementing type must be defined within the same crate as the original type definition
     * - https://doc.rust-lang.org/reference/items/implementations.html#inherent-implementations
     * - https://doc.rust-lang.org/error-index.html#E0116
     * We should check:
     * - When moving inherent impl: check that implementing type is also moved
     * - When moving struct/enum: check that all inherent impls are also moved
     *
     * Rules for trait impls:
     * - Orphan rules: https://doc.rust-lang.org/reference/items/implementations.html#orphan-rules
     * - https://doc.rust-lang.org/error-index.html#E0117
     * We should check (denote impls as `impl<P1..=Pn> Trait<T1..=Tn> for T0`):
     * - When moving trait impl:
     *     - either implemented trait is in target crate
     *     - or at least one of the types `T0..=Tn` is a local type
     * - When moving trait: for each impl of this trait which remains in source crate:
     *     - at least one of the types `T0..=Tn` is a local type
     * - Uncovering is not checking, because it is complicated
    */
    fun checkImpls() {
        if (sourceMod.crateRoot == targetMod.crateRoot) return

        val structsToMove = movedElementsDeepDescendantsOfType<RsStructOrEnumItemElement>(elementsToMove).toSet()
        val implsToMove = movedElementsDeepDescendantsOfType<RsImplItem>(elementsToMove)
        val (inherentImplsToMove, traitImplsToMove) = implsToMove
            .partition { it.traitRef == null }
            .run { Pair(first.toSet(), second.toSet()) }

        checkStructIsMovedTogetherWithInherentImpl(structsToMove, inherentImplsToMove)
        checkInherentImplIsMovedTogetherWithStruct(structsToMove, inherentImplsToMove)

        traitImplsToMove.forEach(this::checkTraitImplIsCoherentAfterMove)
    }

    // https://doc.rust-lang.org/reference/items/implementations.html#trait-implementation-coherence
    private fun checkTraitImplIsCoherentAfterMove(impl: RsImplItem) {
        fun RsElement.isLocalAfterMove(): Boolean =
            crateRoot == targetMod.crateRoot || isInsideMovedElements(elementsToMove)

        val traitRef = impl.traitRef!!
        val (trait, subst, _) = traitRef.resolveToBoundTrait() ?: return
        if (trait.isLocalAfterMove()) return
        val typeParameters = subst.typeSubst.values + (impl.typeReference?.type ?: return)
        val anyTypeParameterIsLocal = typeParameters.any {
            val ty = it.unwrapFundamentalTypes()
            if (ty is TyAdt) ty.item.isLocalAfterMove() else false
        }
        if (!anyTypeParameterIsLocal) {
            val message = "<a href=\"https://ya.ru\">Orphan rules</a> check failed for trait implementation after move"
            conflicts.putValue(impl, message)
        }
    }

    private fun checkStructIsMovedTogetherWithInherentImpl(
        structsToMove: Set<RsStructOrEnumItemElement>,
        inherentImplsToMove: Set<RsImplItem>
    ) {
        for (impl in inherentImplsToMove) {
            val struct = impl.implementingType?.item ?: continue
            if (struct !in structsToMove) {
                val structDescription = RefactoringUIUtil.getDescription(struct, true)
                val message = "Inherent implementation should be moved together with $structDescription"
                conflicts.putValue(impl, message)
            }
        }
    }

    private fun checkInherentImplIsMovedTogetherWithStruct(
        structsToMove: Set<RsStructOrEnumItemElement>,
        inherentImplsToMove: Set<RsImplItem>
    ) {
        for ((file, structsToMoveInFile) in structsToMove.groupBy { it.containingFile }) {
            // not working if there is inherent impl in other file
            // but usually struct and its inherent impls belong to same file
            val structInherentImpls = file.descendantsOfType<RsImplItem>()
                .filter { it.traitRef == null }
                .groupBy { it.implementingType?.item }
            for (struct in structsToMoveInFile) {
                val impls = structInherentImpls[struct] ?: continue
                for (impl in impls.filter { it !in inherentImplsToMove }) {
                    val structDescription = RefactoringUIUtil.getDescription(struct, true)
                    val message = "Inherent implementation should be moved together with $structDescription"
                    conflicts.putValue(impl, message)
                }
            }
        }
    }
}

fun addVisibilityConflict(conflicts: MultiMap<PsiElement, String>, reference: RsElement, target: RsElement) {
    val referenceDescription = RefactoringUIUtil.getDescription(reference.containingMod, true)
    val targetDescription = RefactoringUIUtil.getDescription(target, true)
    val message = "$referenceDescription uses $targetDescription which will be inaccessible after move"
    conflicts.putValue(reference, CommonRefactoringUtil.capitalize(message))
}

private val RsImplItem.implementingType: TyAdt? get() = typeReference?.type as? TyAdt

// https://doc.rust-lang.org/reference/glossary.html#fundamental-type-constructors
private fun Ty.unwrapFundamentalTypes(): Ty {
    when (this) {
        // &T -> T
        // &mut T -> T
        is TyReference -> return referenced
        // Box<T> -> T
        // Pin<T> -> T
        is TyAdt -> {
            if (item == item.knownItems.Box || item == item.knownItems.Pin) {
                return typeArguments.singleOrNull() ?: this
            }
        }
    }
    return this
}
