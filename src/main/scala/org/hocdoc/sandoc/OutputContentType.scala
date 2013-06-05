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

/** Supported output content types. */
object OutputContentType extends Enumeration {

  val Html, DocBook, Pdf = Value
  
  /** Convert a textual description of the content type (like a file extension) to an optional OutputContentType. */
  def apply(text: String): Option[OutputContentType.Value] = text.toLowerCase match {
    case "html"    => Some(Html)
    case "htm"     => Some(Html)
    case "xml"     => Some(DocBook)
    case "docbook" => Some(DocBook)
    case "pdf"     => Some(Pdf)
    case _         => None
  }
}