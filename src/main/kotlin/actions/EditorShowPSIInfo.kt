/*
 * Copyright 2020 Nazmul Idris. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package actions

import Colors.*
import com.intellij.lang.Language
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task.Backgroundable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import org.intellij.plugins.markdown.lang.psi.MarkdownRecursiveElementVisitor
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownHeaderImpl
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownParagraphImpl
import printDebugHeader
import printlnAndLog
import whichThread


internal class EditorShowPSIInfo : AnAction() {
  val sleepDurationMs: Long = 100

  /** [kotlin anonymous objects](https://medium.com/@agrawalsuneet/object-expression-in-kotlin-e75735f19f5d) */
  private val count = object {
    var paragraph: Int = 0
    var header: Int = 0
  }

  override fun actionPerformed(e: AnActionEvent) {
    printDebugHeader()

    val psiFile = e.getRequiredData(CommonDataKeys.PSI_FILE)
    val psiFileViewProvider = psiFile.viewProvider
    val project = e.getRequiredData(CommonDataKeys.PROJECT)
    val editor = e.getRequiredData(CommonDataKeys.EDITOR)
    val progressTitle = "Doing heavy PSI computation"

    val task = object : Backgroundable(project, progressTitle) {
      override fun run(indicator: ProgressIndicator) {
        doWorkInBackground(
            project, psiFile, psiFileViewProvider, indicator, editor)
      }
    }

    task.queue()

    // No need for the code below if you use `task.queue()`.
    // ProgressManager
    //    .getInstance()
    //    .runProcessWithProgressAsynchronously(
    //        task, BackgroundableProcessIndicator(task))

  }

  private fun doWorkInBackground(project: Project,
                                 psiFile: PsiFile,
                                 psiFileViewProvider: FileViewProvider,
                                 indicator: ProgressIndicator,
                                 editor: Editor
  ) {
    printDebugHeader()
    ANSI_YELLOW(whichThread()).printlnAndLog()

    indicator.isIndeterminate = true

    val languages = psiFileViewProvider.languages

    buildString {

      when {
        languages.contains("Markdown") -> runReadAction { navigateMarkdownTree(psiFile, indicator, project) }
        languages.contains("Java")     -> runReadAction { navigateJavaTree(psiFile, indicator, project, editor) }
        else                           -> append(ANSI_RED("No supported languages found"))
      }

      append("languages: $languages\n")
      append("count.header: ${count.header}\n")
      append("count.paragraph: ${count.paragraph}\n")

      checkCancelled(indicator, project)

    }.printlnAndLog()
  }

  private fun navigateJavaTree(psiFile: PsiFile,
                               indicator: ProgressIndicator,
                               project: Project,
                               editor: Editor
  ) {
    printDebugHeader()

    val offset = editor.caretModel.offset
    val element: PsiElement? = psiFile.findElementAt(offset)

    val javaPsiInfo = buildString {

      sleep(sleepDurationMs * 20)

      element?.apply {
        append("Element at caret: $element\n")
        val containingMethod: PsiMethod? = PsiTreeUtil.getParentOfType(element, PsiMethod::class.java)

        containingMethod?.apply {
          append("Containing method: ${containingMethod.name}\n")

          containingMethod.containingClass?.apply {
            append("Containing class: ${this.name} \n")
          }

          val list = mutableListOf<PsiLocalVariable>()
          containingMethod.accept(object : JavaRecursiveElementVisitor() {
            override fun visitLocalVariable(variable: PsiLocalVariable) {
              list.add(variable)

              // The following line ensures that ProgressManager.checkCancelled()
              // is called.
              super.visitLocalVariable(variable)
            }
          })
          if (list.isNotEmpty())
            append(list.joinToString(prefix = "Local variables:\n", separator = "\n") { it -> "- ${it.name}" })

        }

      }

    }

    val message = if (javaPsiInfo == "") "No PsiElement at caret!" else javaPsiInfo
    message.printlnAndLog()
    ApplicationManager.getApplication().invokeLater {
      Messages.showMessageDialog(project, message, "PSI Java Info", null)
    }

  }

  private fun navigateMarkdownTree(psiFile: PsiFile,
                                   indicator: ProgressIndicator,
                                   project: Project
  ) {
    psiFile.accept(object : MarkdownRecursiveElementVisitor() {
      override fun visitParagraph(paragraph: MarkdownParagraphImpl) {
        printDebugHeader()
        ANSI_YELLOW(whichThread()).printlnAndLog()

        this@EditorShowPSIInfo.count.paragraph++
        sleep()
        checkCancelled(indicator, project)

        // The following line ensures that ProgressManager.checkCancelled()
        // is called.
        super.visitParagraph(paragraph)
      }

      override fun visitHeader(header: MarkdownHeaderImpl) {
        printDebugHeader()
        ANSI_YELLOW(whichThread()).printlnAndLog()

        this@EditorShowPSIInfo.count.header++
        sleep()
        checkCancelled(indicator, project)

        // The following line ensures that ProgressManager.checkCancelled()
        // is called.
        super.visitHeader(header)
      }
    })

  }

  private fun sleep(durationMs: Long = sleepDurationMs) {
    val formattedDuration = "%.3f sec".format(durationMs / 1000f)
    ANSI_YELLOW(whichThread() + ANSI_RED(" sleeping for $formattedDuration 😴")).printlnAndLog()
    Thread.sleep(durationMs)
    ANSI_YELLOW(whichThread() + ANSI_BLUE(" awake 😳")).printlnAndLog()
  }

  private fun Set<Language>.contains(language: String): Boolean =
      this.any { language.equals(it.id, ignoreCase = true) }

  private fun checkCancelled(indicator: ProgressIndicator,
                             project: Project
  ) {
    printDebugHeader()
    ANSI_YELLOW(whichThread()).printlnAndLog()

    if (indicator.isCanceled) {
      ANSI_RED("Task was cancelled").printlnAndLog()
      ApplicationManager
          .getApplication()
          .invokeLater {
            Messages.showWarningDialog(
                project, "Task was cancelled", "Cancelled")
          }
    }
  }

  override fun update(e: AnActionEvent) =
      EditorBaseAction.mustHaveProjectAndEditor(e)
}