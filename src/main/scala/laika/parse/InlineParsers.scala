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

package laika.parse

import scala.annotation.tailrec
import scala.collection.mutable.ListBuffer

import laika.tree.Elements.Span
import laika.tree.Elements.Text
  
/** A generic base trait for inline parsers. Provides base parsers that abstract
 *  aspects of inline parsing common to most lightweight markup languages.
 *  
 *  It contains helper parsers that abstract the typical logic required for parsing
 *  nested spans. In many cases a parser has to recognize the end of the span as well
 *  as potentially the start of a nested spans. These two concerns are usually unrelated.
 *  
 *  This trait offers helpers that simplify creating these types of parsers and also
 *  optimize performance of inline parsing. Due to the nature of lightweight text markup
 *  inline parsing would usually require trying a long list of choicing on each input
 *  character, which is slow. These base parsers work based on mappings from the first
 *  character of an inline span to the corresponding full parser.
 *  
 *  @author Jens Halm
 */
trait InlineParsers extends MarkupParsers {
  

  /** The mapping of markup start characters to their corresponding
   *  span parsers.
   * 
   *  A parser mapped to a start character is not required
   *  to successfully parse the subsequent input. If it fails the 
   *  character that triggered the parser invocation will be treated
   *  as normal text. The mapping is merely used as a performance
   *  optimization. The parser will be invoked with the input 
   *  offset pointing to the character after the one
   *  specified as the key for the mapping.
   */
  def spanParsers: Map[Char,Parser[Span]]
  
  
  /** Abstracts the internal process of building up the result of an inline parser.
   *  Since some inline parser produce a tree of nested spans whereas others may
   *  only produce a text result, they often require the same logic in how they
   *  deal with nested constructs.
   */
  trait ResultBuilder[Elem, +To] {
    def fromString (str: String): Elem
    def += (item: Elem): Unit
    def result: To
  } 
  
  /** ResultBuilder that produces a list of spans.
   */
  class SpanBuilder extends ResultBuilder[Span, List[Span]] {
    val buffer = new ListBuffer[Span]
    def fromString (str: String) = Text(str)
    def += (item: Span) = buffer += item
    
    def mergeAdjacentTextSpans (spans: List[Span]): List[Span] = {
      (List[Span]() /: spans) {  
        case (Text(text1,_) :: rest, Text(text2,_)) => Text(text1 ++ text2) :: rest // TODO - deal with options
        case (xs, x) => x :: xs
      }.reverse
    }
    def result = mergeAdjacentTextSpans(buffer.toList)
  }

  /** ResultBuilder that produces a String.
   */
  class TextBuilder extends ResultBuilder[String, String] {
    val builder = new scala.collection.mutable.StringBuilder
    def fromString (str: String) = str
    def += (item: String) = builder ++= item
    def result = builder.toString
  }

  /** Generic base parser that parses inline elements based on the specified
   *  helper parsers. Usually not used directly by parser implementations,
   *  this is the base parser the other inline parsers of this trait delegate to.
   *  
   *  @tparam Elem the element type produced by a single parser for a nested span
   *  @tparam To the type of the result this parser produces
   *  @param parser the parser for the text of the current span element
   *  @param nested a mapping from the start character of a span to the corresponding parser for nested span elements
   *  @param resultBuilder responsible for building the final result of this parser based on the results of the helper parsers
   *  @return the resulting parser
   */ 
  def inline [Elem,To] (text: => TextParser, 
                        nested: => Map[Char, Parser[Elem]], 
                        resultBuilder: => ResultBuilder[Elem,To]) = Parser { in =>

    lazy val builder = resultBuilder // evaluate only once
    lazy val nestedMap = nested
    lazy val textParser = text.stopChars(nestedMap.keySet.toList:_*)
    
    def addText (text: String) = if (!text.isEmpty) builder += builder.fromString(text)
    
    def nestedSpanOrNextChar (parser: Parser[Elem], input: Input) = { 
      parser(input.rest) match {
        case Success(result, next) => builder += result; next
        case _ => builder += builder.fromString(input.first.toString); input.rest 
      }
    }
    
    @tailrec
    def parse (input: Input) : ParseResult[To] = {
      textParser.applyInternal(input) match {
        case NoSuccess(msg, _)     => Failure(msg, in)
        case Success((text,onStopChar), next) => {
          addText(text); 
          if (onStopChar) nestedMap.get(next.first) match {
            case None         => Success(builder.result, next)
            case Some(parser) => {
              val newIn = nestedSpanOrNextChar(parser, next)
              if (newIn.atEnd) Success(builder.result, newIn)
              else parse(newIn)
            }
          }
          else Success(builder.result, next)
        }
      }
    }
      
    parse(in)
  }
  
  /** Parses a list of spans based on the specified helper parsers.
   * 
   *  @param parser the parser for the text of the current span element
   *  @param nested a mapping from the start character of a span to the corresponding parser for nested span elements
   *  @return the resulting parser
   */
  def spans (parser: => TextParser, spanParsers: => Map[Char, Parser[Span]]) 
      = inline(parser, spanParsers, new SpanBuilder)
  
  /** Parses text based on the specified helper parsers.
   * 
   *  @param parser the parser for the text of the current element
   *  @param nested a mapping from the start character of a span to the corresponding parser for nested span elements
   *  @return the resulting parser
   */
  def text (parser: => TextParser, nested: => Map[Char, Parser[String]]): Parser[String] 
      = inline(parser, nested, new TextBuilder)
  
  /** Fully parses the input string and produces a list of spans.
   *  
   *  This function is expected to always succeed, errors would be considered a bug
   *  of this library, as the parsers treat all unknown or malformed markup as regular
   *  text. Some parsers might additionally insert system message elements in case
   *  of markup errors.
   *  
   *  @param source the input to parse
   *  @param spanParsers a mapping from the start character of a span to the corresponding parser
   *  @return the result of the parser in form of a list of spans
   */
  def parseInline (source: String, spanParsers: Map[Char, Parser[Span]]) = 
    parseMarkup(inline(any, spanParsers, new SpanBuilder), source)
    
  /** Fully parses the input string and produces a list of spans, using the
   *  default span parsers returned by the `spanParsers` method.
   * 
   *  This function is expected to always succeed, errors would be considered a bug
   *  of this library, as the parsers treat all unknown or malformed markup as regular
   *  text. Some parsers might additionally insert system message elements in case
   *  of markup errors.
   *  
   *  @param source the input to parse
   *  @return the result of the parser in form of a list of spans
   */
  def parseInline (source: String): List[Span] = parseInline(source, spanParsers)

}
