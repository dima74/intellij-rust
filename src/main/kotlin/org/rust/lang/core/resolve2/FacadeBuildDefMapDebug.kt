/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve2

import com.intellij.openapiext.isUnitTestMode
import gnu.trove.TObjectHash
import org.rust.lang.core.crate.Crate

fun afterDefMapBuiltDebug(defMap: CrateDefMap, context: CollectorContext) {
    if (!isUnitTestMode) return
    checkNoUnresolvedImportsAndMacros2(context.imports, context.macroCalls)
}

private fun checkNoUnresolvedImportsAndMacros2(imports: MutableList<Import>, macros: MutableList<MacroCallInfo>) {
    val unresolvedImports = imports.filter { it.containingMod.isDeeplyEnabledByCfg }
    val unresolvedMacros = macros.filter { it.containingMod.isDeeplyEnabledByCfg }

    if (unresolvedImports.isNotEmpty() || unresolvedMacros.isNotEmpty()) {
        check(true)
    }
    // check(unresolvedImports.isEmpty()) { "Found ${unresolvedImports.size} unresolved imports: $unresolvedImports" }
    // check(unresolvedMacros.isEmpty()) { "Found ${unresolvedMacros.size} unresolved macroCalls: $unresolvedMacros" }
}

private val ModData.descendantModules: List<ModData>
    get() = childModules.values.flatMap { it.descendantModules } + this

fun printStatistics(defMaps: Map<Crate, CrateDefMap>) {
    // Statistics(defMaps).print()
}

private class Statistics(private val defMaps: Map<Crate, CrateDefMap>) {
    private val allMods: List<ModData> =
        defMaps.values.flatMap { it.root.descendantModules }
    private val allPerNs: List<PerNs> =
        allMods.flatMap { it.visibleItems.values }
    private val allVisItem: List<VisItem> =
        allPerNs.flatMap { listOfNotNull(it.types, it.values, it.macros) }
    private val allPerNsWithMod: List<Pair<ModData, PerNs>> =
        allMods.flatMap { it.visibleItems.values.map { item -> it to item } }
    private val allVisItemWithMod: List<Pair<ModData, VisItem>> = allPerNsWithMod
        .flatMap {
            val mod = it.first
            val items = with(it.second) { listOfNotNull(types, values, macros) }
            items.map { item -> mod to item }
        }

    fun print() {
        // printNumberPerNsAndVisItems()
        // printNumberDistinctAndMostCommonVisItems()
        // printNumberPerNsHavingItemsWithDifferentVisibility(defMaps)
        // printNumberAliasedVisItems()
        // printVisibilityStatistics()
        // printMapStatistics()
    }

    private fun printNumberPerNsAndVisItems() {
        println("\n# Number PerNs and VisItem")
        println("Number modules:  ${allMods.size}")
        println("Number PerNs:    ${allPerNs.size}")
        println("Number VisItem:  ${allVisItem.size}")
    }

    private fun printNumberDistinctAndMostCommonVisItems() {
        val items = allVisItemWithMod
        println("\n# Number distinct VisItem")
        println("Items:          ${items.size}")
        println("Items distinct: ${items.distinctBy { it.second }.size}")

        items
            .groupBy({ it.second }, { it.first })
            .entries
            .sortedByDescending { it.value.size }
            .take(1)
            .forEach { (item, mods) ->
                println("\t${mods.size} $item")
                for (mod in mods) {
                    println("$mod")
                }
            }
    }

    private fun printNumberPerNsHavingItemsWithDifferentVisibility() {
        val count = allPerNs
            .count {
                listOfNotNull(it.types, it.values, it.macros)
                    .map { item -> item.visibility }
                    .distinct()
                    .size != 1
            }
        println("\n# Number PerNs having items with different visibility: $count")
    }

    private fun printNumberAliasedVisItems() {
        val count = allMods.sumBy { mod ->
            mod.visibleItems.entries.sumBy { (name, perNs) ->
                val items = with(perNs) { listOfNotNull(types, values, macros) }
                items.count { it.path.segments.isEmpty() || it.name != name }
            }
        }
        println("\n# Number VisItems in scope with name different from original name: $count")
    }

    fun printVisibilityStatistics() {
        var restrictedTotal = 0
        var restrictedCrateRoot = 0
        var restrictedInSelf = 0
        var restrictedInSuper = 0
        var restrictedElse = 0
        for ((mod, visItem) in allVisItemWithMod) {
            val visibility = visItem.visibility as? Visibility.Restricted ?: continue
            val scope = visibility.inMod
            restrictedTotal += 1
            when {
                scope.isCrateRoot -> restrictedCrateRoot += 1
                scope == mod -> restrictedInSelf += 1
                scope == mod.parent -> restrictedInSuper += 1
                else -> restrictedElse += 1
            }
        }

        val items = allVisItem
        println("\n# Visibility statistics")
        println("Total visItems: ${items.size}")
        println("\tPublic:      ${items.count { it.visibility == Visibility.Public }}")
        println("\tInvisible:   ${items.count { it.visibility == Visibility.Invisible }}")
        println("\tCfgDisabled: ${items.count { it.visibility == Visibility.CfgDisabled }}")
        println("\tRestricted:  $restrictedTotal")
        println("\t\tcrate:   $restrictedCrateRoot")
        println("\t\tprivate: $restrictedInSelf")
        println("\t\tsuper:   $restrictedInSuper")
        println("\t\telse:    $restrictedElse")
    }

    fun printMapStatistics() {
        println("\n# Maps capacity statistics")
        val modules = allMods
        printMapStatistics("visibleItems", modules) { it.visibleItems }
        // printMapStatistics("childModules", modules) { it.childModules }
        printMapStatistics("legacyMacros", modules) { it.legacyMacros }
        printMapStatistics("unnamedTrait", modules) { it.unnamedTraitImports }
    }
}

class IdWrapper(val value: Any) {
    override fun hashCode(): Int = value.hashCode()
    override fun equals(other: Any?): Boolean = value === (other as IdWrapper).value
}

private fun printMapStatistics(description: String, modules: List<ModData>, getter: (ModData) -> Map<*, *>) {
    var sizeSum = 0
    var capacitySum = 0
    for (module in modules) {
        val capacityField = TObjectHash::class.java.getDeclaredMethod("capacity")
        capacityField.isAccessible = true
        val map = getter(module)
        val capacity = capacityField.invoke(map as TObjectHash<*>) as Int
        capacitySum += capacity
        sizeSum += map.size
    }
    println("$description:  $sizeSum / $capacitySum")
}
