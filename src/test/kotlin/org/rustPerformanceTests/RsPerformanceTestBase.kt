/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rustPerformanceTests

import org.apache.commons.lang3.ObjectUtils
import org.rust.lang.core.macros.MacroExpansionScope
import org.rust.lang.core.macros.macroExpansionManager
import org.rust.stdext.getAverageAndStandardDeviation
import kotlin.system.measureTimeMillis

abstract class RsPerformanceTestBase : RsRealProjectTestBase() {

    // It is a performance test, but we don't want to waste time measuring CPU performance
    override fun isPerformanceTest(): Boolean = false

    protected fun openProject(info: RealProjectInfo) {
        project.macroExpansionManager.setUnitTestExpansionModeAndDirectory(MacroExpansionScope.ALL, name)
        openRealProject(info) ?: error("Can't open project")
    }
}

// fun profile2(label: String, warmupIterations: Int = 10, action: () -> Unit) {
//     val listTimings = ListTimings()
//     for (i in 0..Int.MAX_VALUE) {
//         val timings = Timings()
//         val time = measureTimeMillis {
//             action()
//         }
//         timings.addMeasure(label, time)
//         listTimings.add(timings)
//         listTimings.print()
//     }
// }

fun profile(label: String, warmupIterations: Int = 10, action: () -> Unit) {
    val times = mutableListOf<Long>()
    for (i in 0..Int.MAX_VALUE) {
        val time = measureTimeMillis {
            action()
        }

        val iteration = "#${i + 1}".padStart(5)
        val statistics = if (i < warmupIterations) {
            "warmup"
        } else {
            times += time
            val timeMin = times.min()
            val timeMedian = ObjectUtils.median(*times.toTypedArray())
            val (_, standardDeviation) = times.getAverageAndStandardDeviation()
            "min = $timeMin ms, median = $timeMedian ms, std = ${standardDeviation.toInt()}"
        }
        val timePadded = "$time".padStart(4)
        println("$iteration: $label in $timePadded ms  ($statistics)")
    }
}
