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

package laika.parse.rst

import laika.tree.Elements._
import laika.parse.rst.Elements._

/** 
 *  The default rewrite rules that get applied to the raw document tree after parsing
 *  reStructuredText markup. These rules are responsible for resolving  substitution 
 *  references and interpreted text which are specific to reStructuredText and get usually
 *  executed alongside the generic rules. .
 * 
 *  @author Jens Halm
 */
object RewriteRules extends (Document => PartialFunction[Element,Option[Element]]) {

  
  class DefaultRules (val document: Document) { 
    
    val substitutions = document.collect { 
      case SubstitutionDefinition(id,content,_) => (id,content) 
    } toMap
    
    val textRoles = document.collect { 
      case CustomizedTextRole(id,f,_) => (id,f)                                   
    } toMap  
    
    def invalidSpan (message: String, fallback: String) =
      InvalidSpan(SystemMessage(laika.tree.Elements.Error, message), Text(fallback))
      
    /** Function providing the default rewrite rules when passed a document instance.
     */
    val rewrite: PartialFunction[Element, Option[Element]] = {
          
      case SubstitutionReference(id,_) =>
        substitutions.get(id).orElse(Some(invalidSpan("unknown substitution id: " + id, "|"+id+"|")))
        
      case InterpretedText(role,text,_,_) =>
        textRoles.get(role).orElse(Some({s: String => invalidSpan("unknown text role: " + role, "`"+s+"`")})).map(_(text))
        
      case SubstitutionDefinition(_,_,_) => None // TODO - should be covered by default rules
      case CustomizedTextRole(_,_,_) => None
      
    }
  }

  /** Provides the default rewrite rules specific to reStructuredText 
   *  for the specified document. These rules usually get executed
   *  alongside the generic default rules.
   */
  def apply (document: Document) = (new DefaultRules(document)).rewrite
  
  
}