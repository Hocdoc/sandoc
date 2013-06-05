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

package org.hocdoc.sandoc

import org.hocdoc.sandoc.processor._
import java.io.FileOutputStream
import scala.io.Source
import laika.api.Transform
import laika.parse.markdown.Markdown
import laika.render.HTML
import laika.parse.rst.ReStructuredText
import laika.render.PrettyPrint

object Main extends App {
  
  val conf = new Conf(args)
  val inputText = readTextFromFiles(conf.inputFiles())
  val title = conf.title.get.getOrElse(conf.inputFiles().head)
  val processor = inputContentType match {
    case InputContentType.Markdown         => new LaikaProcessor(InputContentType.Markdown, title)
    case InputContentType.ReStructuredText => new LaikaProcessor(InputContentType.ReStructuredText, title)
    case InputContentType.AsciiDoc         => new AsciidocProcessor
  }
  
  val stream = conf.output.get.map(x => new FileOutputStream(x)).getOrElse(System.out)
  try {
    processor.render(inputText, outputContentType, stream)
  } finally {
    if(stream != System.out) stream.close()
  }
  
  def inputContentType: InputContentType.Value = {
    val text = conf.fromFormat.get.getOrElse(fileExtension(conf.inputFiles().head))
    InputContentType(text).getOrElse(InputContentType.Markdown)
  }
  
  def outputContentType: OutputContentType.Value = {
    val text = conf.toFormat.get.getOrElse(fileExtension(conf.output.get.getOrElse("")))
    OutputContentType(text).getOrElse(OutputContentType.Html)
  }
    
  /** 
   * Concate the content of text files. 
   * Between every two files two newlines will be also added.
   */
  def readTextFromFiles(filenames: List[String]): String =
    filenames.map(x => Source.fromFile(x).mkString).mkString("\n\n")

  /** Return the file extension (last `.`-word) or an empty string. */
  def fileExtension(filename: String): String = filename.lastIndexOf('.') match {
    case x: Int if x >= 0 => filename.substring(x + 1)
    case _                => ""
  }
}

