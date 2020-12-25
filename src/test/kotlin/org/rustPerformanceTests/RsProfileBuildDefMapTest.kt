/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rustPerformanceTests

import org.rust.lang.core.resolve2.forceRebuildDefMapForAllCrates

class RsProfileBuildDefMapTest : RsPerformanceTestBase() {

    fun `test build rustc`() = doTest(RUSTC)

    fun `test build empty`() = doTest(EMPTY)
    fun `test build Cargo`() = doTest(CARGO)
    fun `test build mysql_async`() = doTest(MYSQL_ASYNC)
    fun `test build tokio`() = doTest(TOKIO)
    fun `test build amethyst`() = doTest(AMETHYST)
    fun `test build clap`() = doTest(CLAP)
    fun `test build diesel`() = doTest(DIESEL)
    fun `test build rust_analyzer`() = doTest(RUST_ANALYZER)
    fun `test build xi_editor`() = doTest(XI_EDITOR)
    fun `test build juniper`() = doTest(JUNIPER)

    private fun doTest(info: RealProjectInfo) {
        openProject(info)
        profile("buildDefMap") {
            project.forceRebuildDefMapForAllCrates(multithread = false)
        }
    }
}
