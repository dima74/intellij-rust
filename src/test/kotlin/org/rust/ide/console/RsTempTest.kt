/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.console

import com.intellij.codeInsight.completion.CompletionType
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Test

//class RsTempTest2 : BasePlatformTestCase() {
//    @Test
//    fun test() {
//        val content = "fn main() { let foo1 = 1; let x =  }"
//        val virtualFile = LightVirtualFile("temp.rs", content)
//        virtualFile.language = RsLanguage
//        println(virtualFile.language)
//
////        virtualFile.com
//    }
//}

class RsTempTest : BasePlatformTestCase() {
    @Test
    fun test2() {
        val content = "fn main() {\n let my_variable = 1;\n let x = <caret>\n }"
        myFixture.configureByText("temp.rs", content)
        val completions = myFixture.complete(CompletionType.BASIC)
        println(completions)
    }
}

//class SimpleCodeInsightTest : LightCodeInsightFixtureTestCase() {
//    fun testCompletion() {
//        myFixture.configureByFiles("CompleteTestData.java", "DefaultTestData.simple")
//        myFixture.complete(CompletionType.BASIC, 1)
//        val strings = myFixture.lookupElementStrings
////        assertTrue(strings!!.containsAll(Arrays.asList("key with spaces", "language", "message", "tab", "website")))
////        assertEquals(5, strings.size)
//    }
//}
