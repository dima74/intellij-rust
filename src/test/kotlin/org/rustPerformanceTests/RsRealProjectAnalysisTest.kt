/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rustPerformanceTests

import com.intellij.codeInspection.ex.InspectionToolRegistrar
import com.intellij.ide.annotator.AnnotatorBase
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VfsUtil
import org.rust.ide.annotator.RsErrorAnnotator
import org.rust.ide.inspections.RsLocalInspectionTool
import org.rust.ide.inspections.RsUnresolvedReferenceInspection
import org.rust.lang.RsFileType
import org.rust.lang.core.crate.crateGraph
import org.rust.lang.core.macros.MacroExpansionScope
import org.rust.lang.core.macros.macroExpansionManager
import org.rust.lang.core.psi.RsFile
import org.rust.lang.core.psi.ext.RsMod
import org.rust.lang.core.psi.ext.childModules
import org.rust.lang.core.psi.shouldIndexFile
import org.rust.lang.core.resolve2.defMapService
import org.rust.lang.core.resolve2.getOrUpdateIfNeeded
import org.rust.lang.core.resolve2.isNewResolveEnabled
import org.rust.openapiext.toPsiFile

open class RsRealProjectAnalysisTest : RsRealProjectTestBase() {

    /** Don't run it on Rustc! It's a kind of stress-test */
    fun `test analyze rustc`() = doTest(RUSTC)

    fun `test analyze empty`() = doTest(EMPTY)
    fun `test analyze Cargo`() = doTest(CARGO)
    fun `test analyze mysql_async`() = doTest(MYSQL_ASYNC)
    fun `test analyze tokio`() = doTest(TOKIO)
    fun `test analyze amethyst`() = doTest(AMETHYST)
    fun `test analyze clap`() = doTest(CLAP)
    fun `test analyze diesel`() = doTest(DIESEL)
    fun `test analyze rust_analyzer`() = doTest(RUST_ANALYZER)
    fun `test analyze xi_editor`() = doTest(XI_EDITOR)
    fun `test analyze juniper`() = doTest(JUNIPER)

    private val earlyTestRootDisposable = Disposer.newDisposable()

    protected fun doTest(info: RealProjectInfo, failOnFirstFileWithErrors: Boolean = false) {
        val errorConsumer = if (failOnFirstFileWithErrors) FAIL_FAST else COLLECT_ALL_EXCEPTIONS
        doTest(info, errorConsumer)
    }

    protected fun doTest(info: RealProjectInfo, consumer: AnnotationConsumer) {
        Disposer.register(
            earlyTestRootDisposable,
            project.macroExpansionManager.setUnitTestExpansionModeAndDirectory(MacroExpansionScope.ALL, name)
        )
        AnnotatorBase.enableAnnotator(RsErrorAnnotator::class.java, testRootDisposable)
        val inspections = InspectionToolRegistrar.getInstance().createTools()
            .map { it.tool }
            .filterIsInstance<RsLocalInspectionTool>()

        for (inspection in inspections) {
            setUpInspection(inspection)
        }

        myFixture.enableInspections(*inspections.toTypedArray())

        println("Opening the project `${info.name}`")
        val base = openRealProject(info) ?: return

        println("Collecting files to analyze")

        val stdlibBaseDir = rustupFixture.stdlib!!

        val isStdlib = info.name == STDLIB
        val filesToCheck = if (isStdlib) {
            stdlibBaseDir.findDescendants {
                it.fileType == RsFileType && run {
                    val file = it.toPsiFile(project)
                    file is RsFile && file.crateRoot != null && file.cargoWorkspace != null
                }
            }
        } else {
            val crates = project.crateGraph.topSortedCrates.reversed()
            if (project.isNewResolveEnabled) {
                for (crate in crates) {
                    project.defMapService.getOrUpdateIfNeeded(crate.id ?: continue)
                }
            }
            crates
                .filter {
                    val crateRoot = it.rootModFile ?: return@filter false
                    shouldIndexFile(project, crateRoot)
                }
                .mapNotNull { it.rootMod }
                .flatMap { getAllFiles(it) }
                .map { it.virtualFile }
        }

        for ((index, file) in filesToCheck.withIndex()) {
            if (!isStdlib && VfsUtil.isAncestor(stdlibBaseDir, file, true)) continue

            val path = if (VfsUtil.isAncestor(base, file, true)) {
                file.path.substring(base.path.length + 1)
            } else {
                file.path
            }
            println("Analyzing $index/${filesToCheck.size} $path")
            myFixture.openFileInEditor(file)
            val infos = myFixture.doHighlighting(HighlightSeverity.ERROR)
            val text = myFixture.editor.document.text
            for (highlightInfo in infos) {
                val position = myFixture.editor.offsetToLogicalPosition(highlightInfo.startOffset)
                val annotation = Annotation(
                    path,
                    // Use 1-based indexing to be compatible with editor UI and `Go to Line/Column` action
                    position.line + 1,
                    position.column + 1,
                    text.substring(highlightInfo.startOffset, highlightInfo.endOffset),
                    highlightInfo.description,
                    highlightInfo.inspectionToolId
                )
                consumer.consumeAnnotation(annotation)
            }
        }
        consumer.finish()
    }

    protected open fun setUpInspection(inspection: RsLocalInspectionTool) {
        when (inspection) {
            is RsUnresolvedReferenceInspection -> inspection.ignoreWithoutQuickFix = false
        }
    }

    override fun tearDown() {
        Disposer.dispose(earlyTestRootDisposable)
        super.tearDown()
    }

    companion object {

        private const val STDLIB = "stdlib"

        val FAIL_FAST = object : AnnotationConsumer {
            override fun consumeAnnotation(annotation: Annotation) {
                error(annotation.toString())
            }
            override fun finish() {}
        }

        val COLLECT_ALL_EXCEPTIONS = object : AnnotationConsumer {

            val annotations = mutableListOf<Annotation>()

            override fun consumeAnnotation(annotation: Annotation) {
                annotations += annotation
            }

            override fun finish() {
                if (annotations.isNotEmpty()) {
                    error("Error annotations found:\n\n" + annotations.joinToString("\n\n"))
                }
            }
        }
    }

    interface AnnotationConsumer {
        fun consumeAnnotation(annotation: Annotation)
        fun finish()
    }

    data class Annotation(
        val filePath: String,
        val line: Int,
        val column: Int,
        val highlightedText: String,
        val error: String,
        val inspectionToolId: String?
    ) {
        override fun toString(): String {
            val suffix = if (inspectionToolId != null) " by $inspectionToolId" else ""
            return "$filePath:$line:$column '$highlightedText' ($error)$suffix"
        }
    }
}

private fun getAllFiles(crateRoot: RsFile): List<RsFile> {
    val result = mutableListOf<RsFile>()
    fun go(mod: RsMod) {
        if (mod is RsFile) result += mod
        mod.childModules.forEach(::go)
    }
    go(crateRoot)
    return result
}
