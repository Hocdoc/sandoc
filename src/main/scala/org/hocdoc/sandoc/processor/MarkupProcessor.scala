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

import java.io.OutputStream
import org.hocdoc.sandoc.OutputContentType
import org.apache.fop.apps.FopFactory
import org.apache.xmlgraphics.util.MimeConstants
import javax.xml.transform.TransformerFactory
import javax.xml.transform.stream.StreamSource
import java.io.StringReader
import java.io.File
import javax.xml.transform.sax.SAXResult
import java.net.URL
import grizzled.slf4j.Logging

abstract class MarkupProcessor extends Logging {

  protected val imagePath = "/xsl/docbook/images/"
  protected val calloutImagePath = imagePath + "callouts/"
    
  protected lazy val fopFactory = FopFactory.newInstance
  
  def render(inputText: String, outputContentType: OutputContentType.Value, 
             stream: OutputStream, xsltPath: String): Unit
  
  /** Renders a DocBook as PDF with Apache FOP. */
  protected def docBookToPdf(docbookXml: String, stream: OutputStream, xsltPath: String): Unit = {
    val startTime = System.currentTimeMillis
    val fop = fopFactory.newFop(MimeConstants.MIME_PDF, stream)
    val transformerFactory = TransformerFactory.newInstance
    val xslt = new StreamSource(new File(xsltPath));
    
    val transformer = transformerFactory.newTransformer(xslt);
    transformer.setParameter("use.extensions", "1")
    transformer.setParameter("fop.extensions", "0")
    transformer.setParameter("fop1.extensions", "1")         // generate bookmark tree
    transformer.setParameter("admon.graphics", "1")
    transformer.setParameter("admon.graphics.path", resourceURL(imagePath))
    transformer.setParameter("admon.graphics.extension", ".svg")
    transformer.setParameter("callout.graphics.path", resourceURL(calloutImagePath)) 
    
    val src = new StreamSource(new StringReader(docbookXml))    
    val result = new SAXResult(fop.getDefaultHandler)
    transformer.transform(src, result)
    
    val endTime = System.currentTimeMillis
    logger.info("FOP Time: " + (endTime - startTime))
  }

  /** Absolute URL of a file resource. */
  private def resourceURL(name: String): URL = 
    getClass.getResource(name)
  
}
