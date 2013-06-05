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

import org.asciidoctor.Asciidoctor
import org.asciidoctor.Attributes
import org.asciidoctor.Options
import java.io.OutputStream
import java.io.StringWriter
import java.io.StringReader
import java.io.PrintWriter

import org.hocdoc.sandoc.OutputContentType

class AsciidocProcessor extends MarkupProcessor {

  private val asciidoctor = Asciidoctor.Factory.create
    
  override def render(inputText: String, outputContentType: OutputContentType.Value, stream: OutputStream): Unit = {
    val isPdf = outputContentType == OutputContentType.Pdf
    
    val renderReader = new StringReader(inputText)
    val stringWriter = new StringWriter
    val renderWriter = if(isPdf) stringWriter else new PrintWriter(stream)
    asciidoctor.render(renderReader, renderWriter, renderOptions(outputContentType))
    
    if(isPdf) docBookToPdf(stringWriter.toString, stream)
  }
  
  private def renderOptions(outputContentType: OutputContentType.Value): Options = {
    val attributes = new Attributes
    val out = outputContentType
    attributes.setBackend(if(outputContentType == OutputContentType.Html) "html" else "docbook")
    attributes.setIcons(Attributes.ORIGINAL_ADMONITION_ICONS_WITH_IMG)
    
    val options = new Options
    options.setHeaderFooter(true)
    options.setAttributes(attributes)
    options
  }
}