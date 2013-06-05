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
import scala.collection.mutable.ListBuffer
import scala.annotation.tailrec
import scala.util.parsing.input.Reader

/** Provides the parsers for all types of block-level elements of reStructuredText. 
 *  It merges the individual traits that provide implementations for list, tables, etc. and 
 *  adds the remaining block level parsers that do not fit into any of the subcategories 
 *  supported by the other traits.
 * 
 *  Block parsers are only concerned with splitting the document into 
 *  (potentially nested) blocks. They are used in the first phase of parsing,
 *  while delegating to inline parsers for the 2nd phase.
 * 
 * @author Jens Halm
 */
trait BlockParsers extends laika.parse.BlockParsers 
                      with ListParsers 
                      with TableParsers 
                      with ExplicitBlockParsers { self: InlineParsers =>

  
  override def ws = anyOf(' ') // other whitespace has been replaced with spaces by preprocessor
                        
  
  override def parseDocument (reader: Reader[Char]) = {
    val raw = super.parseDocument(reader)
    raw.copy(rewriteRules = List(RewriteRules(raw.document)))
  }
  
  
  /** Parses punctuation characters as supported by transitions (rules) and 
   *  overlines and underlines for header sections.
   */
  val punctuationChar = 
    anyOf('!','"','#','$','%','&','\'','(',')','[',']','{','}','*','+',',','-','.',':',';','/','<','>','=','?','@','\\','^','_','`','|','~')

  /** Parses a transition (rule).
   * 
   *  See [[http://docutils.sourceforge.net/docs/ref/rst/restructuredtext.html#transitions]].
   */  
  val transition = (punctuationChar min 4) ~ ws ~ eol ~ guard(blankLine) ^^^ Rule()  
    
  /** Parses a single paragraph. Everything between two blank lines that is not
   *  recognized as a special reStructuredText block type will be parsed as a regular paragraph.
   */
  def paragraph: Parser[Paragraph] = 
      ((not(blankLine) ~> restOfLine) +) ^^ { lines => Paragraph(parseInline(lines mkString "\n")) }
  

  /** Parses a section header with both overline and underline.
   * 
   *  See [[http://docutils.sourceforge.net/docs/ref/rst/restructuredtext.html#sections]].
   */
  def headerWithOverline: Parser[Block] = {
    (punctuationChar take 1) >> { start =>
      val char = start.charAt(0)
      anyOf(char) >> { deco =>
        val len = deco.length + 1
        (ws ~ eol ~> (anyBut('\n') max len) <~ 
         ws ~ eol ~ (anyOf(char) take len) ~
         ws ~ eol) ^^ { title => DecoratedHeader(OverlineAndUnderline(char), parseInline(title.trim)) }
      }
    }
  }
  
  /** Parses a section header with an underline, but no overline.
   * 
   *  See [[http://docutils.sourceforge.net/docs/ref/rst/restructuredtext.html#sections]].
   */
  def headerWithUnderline: Parser[Block] = {
    (anyBut(' ') take 1) ~ restOfLine >> { case char ~ rest =>
      val title = (char + rest).trim
      (punctuationChar take 1) >> { start =>
        val char = start.charAt(0)
        ((anyOf(char) min (title.length - 1)) ~
         ws ~ eol) ^^ { _ => DecoratedHeader(Underline(char), parseInline(title)) }
      }
    }
  }
  
  /** Parses a doctest block. This is a feature which is very specific to the
   *  world of Python where reStructuredText originates. Therefore the resulting
   *  `DoctestBlock` tree element is not part of the standard Laika tree model.
   *  When this block type is used the corresponding special renderers must 
   *  be enabled (e.g. the `ExtendedHTML` renderer for HTML).
   *  
   *  See [[http://docutils.sourceforge.net/docs/ref/rst/restructuredtext.html#doctest-blocks]]
   */
  def doctest: Parser[Block] = ">>> " ~> restOfLine ~ 
      ((not(blankLine) ~> restOfLine) *) ^^ { case first ~ rest => DoctestBlock((first :: rest) mkString "\n") }
  
  
  /** Parses a block quote with an optional attribution. 
   * 
   *  See [[http://docutils.sourceforge.net/docs/ref/rst/restructuredtext.html#block-quotes]]
   */
  def blockQuote: Parser[Block] = {
    
    import scala.math._
    
    val attributionStart = "---" | "--" | '\u2014' // em dash
        
    def attribution (indent: Int) = (ws take indent) ~ attributionStart ~> 
      indentedBlock(minIndent = indent, endsOnBlankLine = true) ^^ { block => 
      parseInline(block.lines mkString "\n")
    }
      
    guard(ws take 1) ~> indentedBlock(firstLineIndented = true, linePredicate = not(attributionStart)) >> { 
      block => opt(opt(blankLines) ~> attribution(block.minIndent)) ^^ { 
        spans => QuotedBlock(parseNestedBlocks(block), spans.getOrElse(Nil)) 
      }
    }
  }
  
  
  /** Builds a parser for a list of blocks based on the parser for a single block. 
   * 
   *  Overridden to add the processing required for cases where a block has influence
   *  on the parsing or processing of the subsequent block. 
   * 
   *  This includes checking each Paragraph for a double colon ending which turns 
   *  the following block into a literal block as well as processing internal
   *  link targets and section headers.  
   * 
   *  @param parser the parser for a single block element
   *  @return a parser for a list of blocks
   */
  override def blockList (parser: => Parser[Block]): Parser[List[Block]] = Parser { in =>
    val defaultBlock = parser <~ opt(blankLines)
    val litBlock = literalBlock | defaultBlock 
    val elems = new ListBuffer[Block]

    def processLiteralMarker (par: Paragraph) = {
      par.content.lastOption match {
        case Some(Text(text,opt)) if text.trim.endsWith("::") => 
          val drop = if (text.length > 2 && text.charAt(text.length-3) == ' ') 3 else 1
          val spans = par.content.init.toList ::: List(Text(text.dropRight(drop),opt))
          (Paragraph(spans,par.options), litBlock)
        case _ => (par, defaultBlock) 
      }
    }
    def toLinkId (h: DecoratedHeader) = {
      def flattenText (spans: Seq[Span]): String = ("" /: spans) {
        case (res, Text(text,_)) => res + text
        case (res, sc: SpanContainer[_]) => res + flattenText(sc.content)
        case (res, _) => res
      }
      flattenText(h.content).replaceAll("[^a-zA-Z0-9]+","-").replaceFirst("^-","").replaceFirst("-$","").toLowerCase
    }
    def result = {
      case class FinalBlock (options: Options = NoOpt) extends Block
      elems += FinalBlock()
      val processed = elems.toList.sliding(2).foldLeft(new ListBuffer[Block]()) {
        case (buffer, (InternalLinkTarget(Id(id1))) :: (InternalLinkTarget(Id(id2))) :: Nil) => 
          buffer += LinkAlias(id1, id2)
        case (buffer, (InternalLinkTarget(Id(id))) :: (et: ExternalLinkDefinition) :: Nil) => 
          buffer += et.copy(id = id)
        case (buffer, (h @ DecoratedHeader(_,_,oldOpt)) :: _) => 
          buffer += h.copy(options = oldOpt + Id(toLinkId(h)))  
        case (buffer, _ :: Nil)   => buffer
        case (buffer, other :: _) => buffer += other
        case (buffer, _)          => buffer
      }
      processed.toList
    }
    
    @tailrec 
    def parse (p: Parser[Block], in: Input): ParseResult[List[Block]] = p(in) match {
      case Success(Paragraph(Text(txt,_) :: Nil,_), rest) if txt.trim == "::" => parse(litBlock, rest)
      case Success(p: Paragraph, rest) => 
        val (paragraph, parser) = processLiteralMarker(p)
        elems += paragraph
        parse(parser, rest)
      case Success(x, rest) => elems += x; parse(defaultBlock, rest)
      case _                => Success(result, in)
    }

    parse(defaultBlock, in)
  }
  
  /** Parses a literal block, either quoted or indented.
   *  Only used when the preceding block ends with a double colon (`::`).
   * 
   *  See [[http://docutils.sourceforge.net/docs/ref/rst/restructuredtext.html#literal-blocks]]
   */
  def literalBlock: Parser[Block] = {
    val indented = indentedBlock(firstLineIndented = true) ^^ 
      { block => LiteralBlock(block.lines mkString "\n") }
    val quoted = block(guard(punctuationChar min 1), guard(punctuationChar min 1), failure("blank line always ends quoted block")) ^^ 
      { lines => LiteralBlock(lines mkString "\n") }  
    indented | quoted
  }
  
  def nonRecursiveBlock: Parser[Block] = comment | paragraph
  
  def topLevelBlock: Parser[Block] = bulletList | 
                                     enumList | 
                                     fieldList | 
                                     lineBlock |
                                     optionList | 
                                     explicitBlockItem |
                                     gridTable |
                                     simpleTable |
                                     doctest |
                                     blockQuote |
                                     headerWithOverline |
                                     transition |
                                     headerWithUnderline |
                                     definitionList |
                                     paragraph
 
  def nestedBlock: Parser[Block] = topLevelBlock
  
  
}