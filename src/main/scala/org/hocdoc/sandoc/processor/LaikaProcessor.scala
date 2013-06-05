/*
 * Copyright 2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.hocdoc.sandoc.processor

import laika.api.Parse
import laika.parse.markdown.Markdown
import laika.parse.rst.ReStructuredText
import laika.api.Render
import laika.render.HTML
import laika.render.DocBook
import java.io.OutputStream

import org.hocdoc.sandoc.OutputContentType
import org.hocdoc.sandoc.InputContentType

class LaikaProcessor(inputContentType: InputContentType.Value, title: String) extends MarkupProcessor {

  private val parser = inputContentType match {
    case InputContentType.Markdown         => Parse as Markdown
    case InputContentType.ReStructuredText => Parse as ReStructuredText
    case _                                 => throw new IllegalArgumentException("Unsupported Laika input content type.")
  }

  override def render(inputText: String, outputContentType: OutputContentType.Value, stream: OutputStream): Unit = {
    val doc = parser fromString inputText
    
    outputContentType match {
      case OutputContentType.Html =>Render as HTML from doc toStream stream
      case OutputContentType.DocBook => Render as (DocBook withTitle(title)) from doc toStream stream
      case OutputContentType.Pdf => docBookToPdf(Render as (DocBook withTitle(title)) from doc toString, stream)
    }
  }
  
}