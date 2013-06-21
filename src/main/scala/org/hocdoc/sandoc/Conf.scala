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

import org.rogach.scallop.ScallopConf

class Conf(args: Seq[String]) extends ScallopConf(args) {

  val defaultXslt = getClass.getResource("/xsl/docbook/fo/docbook.xsl").getPath
      
  version("Sandoc 0.2")
  banner("""Usage: sandoc -o file.pdf markdown1.md markdown2.md""".stripMargin)
  
  val fromFormat = opt[String](descr = "Specify input format.  Can be markdown, rst (reStructuredText) or asciidoc.")
  val toFormat = opt[String](descr = "Specify output format. Can be html, docbook or pdf.")
  val output = opt[String](descr = "Write output to this file.")
  val title = opt[String](descr = "Title of the document for DocBook/PDF output.")
  val xslt = opt[String](descr = "Specify the path of a custom XSL template for the DocBook creation.")
  val inputFiles = trailArg[List[String]]()
 
}