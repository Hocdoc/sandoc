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

package laika.render

import laika.tree.Elements._
import laika.io.Output
  
/** A renderer for DocBook output. May be directly passed to the `Render` or `Transform` APIs:
 * 
 *  {{{
 *  Render as DocBook5 from document toFile "hello.xml"
 *  
 *  Transform from Markdown to DocBook5 fromFile "hello.md" toFile "hello.xml"
 *  }}}
 * 
 *  @author Bernhard Berger
 */
class DocBook private (messageLevel: Option[MessageLevel], title: String) extends ((Output, Element => Unit) => (HTMLWriter, Element => Unit)) {
 
  /** Specifies the minimum required level for a system message
   *  to get included into the output by this renderer.
   */
  def withMessageLevel (level: MessageLevel) = new DocBook(Some(level), title)
  
  def withTitle (newTitle: String) = new DocBook(messageLevel, newTitle)
  
  /** The actual setup method for providing both the writer API for customized
   *  renderers as well as the actual default render function itself. The default render
   *  function always only renders a single element and then delegates to the composite
   *  renderer passed to this function as a parameter when rendering children. This way
   *  user customizations are possible on a per-element basis.
   *  
   *  @param output the output to write to
   *  @param render the composite render function to delegate to when elements need to render their children
   *  @return a tuple consisting of the writer API for customizing
   *  the renderer as well as the actual default render function itself
   */
  def apply (output: Output, render: Element => Unit) = {
    val out = new HTMLWriter(output asFunction, render, "\n")  
    (out, renderElement(out))
  }

  
  private def renderElement (out: HTMLWriter)(elem: Element): Unit = {
    
    def include (msg: SystemMessage) = {
      messageLevel flatMap {lev => if (lev <= msg.level) Some(lev) else None} isDefined
    }
    
    def noneIfDefault [T](actual: T, default: T) = if (actual == default) None else Some(actual.toString)
    
    def renderBlocks (blocks: Seq[Block], close: String) = blocks match {
      case ss @ SpanSequence(_,_) :: Nil => out << "<para>" << ss << "</para>" << close
      case Paragraph(content,opt) :: Nil => out << "<para>" << SpanSequence(content,opt) << "</para>" << close
      case other                         => out <<|> other <<| close
    }
    
    def renderTable (table: Table) = {
      val children = List(table.columns,table.head,table.body) filterNot (_.content.isEmpty)
      
      out << "<informaltable>" <<|> "<tgroup cols=\"" + table.body.content.head.content.size + "\">" <<|> children <<| "</tgroup>" <<| "</informaltable>"
    }
    
    object WithFallback {
      def unapply (value: Element) = value match {
        case c: Customizable => c.options.fallback
        case _ => None
      }
    }
    
    def renderBlockContainer [T <: BlockContainer[T]](con: BlockContainer[T]) = {
  
      def toTable (label: String, content: Seq[Block], options: Options): Table = {
        val left = Cell(BodyCell, List(SpanSequence(List(Text("["+label+"]")))))
        val right = Cell(BodyCell, content)
        val row = Row(List(left,right))
        Table(TableHead(Nil), TableBody(List(row)),
            Columns.options(Styles("label"),NoOpt), options)
      }
      
      def quotedBlockContent (content: Seq[Block], attr: Seq[Span]) = 
        if (attr.isEmpty) content
        else content :+ Paragraph(attr, Styles("attribution"))
      
      con match {
        case Document(content)                => out << 
          """<!DOCTYPE article PUBLIC "-//OASIS//DTD DocBook XML V4.5//EN" "http://www.oasis-open.org/docbook/xml/4.5/docbookx.dtd">""" <<| 
          "<article>" <<|>  "<artheader><title>" << title << "</title></artheader>" << content <<| "</article>"       
        case Section(header, content,opt)     => out << "<section>"  <<|> header   <<|> content <<| "</section>"
        case QuotedBlock(content,attr,opt)    => out << "<blockquote>"; renderBlocks(quotedBlockContent(content,attr), "</blockquote>")
        case BulletListItem(content,_,opt)    => out << "<listitem>";   renderBlocks(content, "</listitem>") 
        case EnumListItem(content,_,_,opt)    => out << "<listitem>";   renderBlocks(content, "</listitem>") 
        case DefinitionListItem(term,defn,_)  => out << "<glossentry><glossterm>" << term << "</glossterm>" <<| "<glossdef>"; renderBlocks(defn, "</glossdef></glossentry>")
        case LineBlock(content,opt)           => out << "<literallayout>" <<|> content <<| "</literallayout>"
        
        case Footnote(label,content,opt)   => out << "<footnote>" <<|> content <<| "</footnote>"
//        case Citation(label,content,opt)   => renderTable(toTable(label,content,opt + Styles("citation")))
        
        case BlockSequence(content, NoOpt)  => out << content
        
        case WithFallback(fallback)         => out << fallback
//        case c: Customizable                => out <<@ ("div",c.options) <<|> c.content <<| "</div>"
        case unknown                        => out << "<div>" <<|> unknown.content <<| "</div>"
      }
    }
    
    def renderSpanContainer [T <: SpanContainer[T]](con: SpanContainer[T]) = con match {
      case Paragraph(content,opt)         => out << "<para>" <<    content <<  "</para>"  
      case Emphasized(content,opt)        => out << "<emphasis>"  <<    content <<  "</emphasis>" 
      case Strong(content,opt)            => out <<@ ("emphasis", NoOpt, ("role", "strong")) <<    content <<  "</emphasis>" 
      case Line(content,opt)              => out << content
      case Header(level, content, opt)    => out << "<title>" << content << "</title>"

      case ExternalLink(content, url, title, opt)  => out <<@ ("ulink", opt, "url"->url) << content << "</ulink>"
      case InternalLink(content, url, title, opt)  => out <<@ ("ulink", opt, "url"->url) << content << "</ulink>"
      
      case SpanSequence(content, NoOpt)   => out << content
      
      case WithFallback(fallback)         => out << fallback
//      case c: Customizable                => out <<@ ("span",c.options) << c.content << "</span>"
      case unknown                        => out << unknown.content // "<span>" << unknown.content << "</span>"
    }
    
    def renderListContainer [T <: ListContainer[T]](con: ListContainer[T]) = con match {
      case EnumList(content,format,start,opt) => out << "<orderedlist>" <<|> content <<| "</orderedlist>"
      case BulletList(content,_,opt)   => out << "<itemizedlist>" <<|> content <<| "</itemizedlist>"
      case DefinitionList(content,opt) => out << "<glosslist>" <<|> content <<| "</glosslist>"
      
      case WithFallback(fallback)      => out << fallback
      case c: Customizable             => out <<@ ("para",c.options) <<|> c.content <<| "</para>"
      case unknown                     => out << "<para>" <<|> unknown.content <<| "</para>"
    }
    
    def renderTextContainer (con: TextContainer) = con match {
      case Text(content,opt)           => out <<&   content
      case Literal(content,opt)        => out << "<literal>" <<<& content << "</literal>" 
      case LiteralBlock(content,opt)   => out << "<programlisting>" <<<&  content << "</programlisting>"
      case Comment(content,opt)        => out << "<!-- " << content << " -->"
      
      case WithFallback(fallback)      => out << fallback
      case c: Customizable             => out << c.content
      case unknown                     => out <<& unknown.content
    }
    
    def renderSimpleBlock (block: Block) = block match {
      case Rule(opt)                   => out <<@ ("hr",opt) 
      case InternalLinkTarget(opt)     => out <<@ ("a",opt) << "</a>"
      
      case WithFallback(fallback)      => out << fallback
      case unknown                     => ()
    }
    
    def renderSimpleSpan (span: Span) = span match {
      case CitationLink(label,opt)     => out <<@ ("a",opt + Styles("citation"),"href"->("#"+label)) << "[" << label << "]</a>" 
      case FootnoteLink(id,label,opt)  => out <<@ ("a",opt + Styles("footnote"),"href"->("#"+id))    << "[" << label << "]</a>" 
      case Image(text,url,title,opt)   => out << "<mediaobject>" << 
                                          "<alt>" << text << "</alt>" << 
                                          "<imageobject>" <<@ ("imagedata", opt,("fileref", url), ("width", "100%")) << "</imagedata></imageobject>" <<
                                          "</mediaobject>"
      case LineBreak(opt)              => out           // No <br>-Tag in DocBook
      
      case WithFallback(fallback)      => out << fallback
      case unknown                     => ()
    }
    
    def renderTableElement (elem: TableElement) = elem match {
      case TableHead(rows,opt)         => out << "<thead>" <<|> rows <<| "</thead>"
      case TableBody(rows,opt)         => out << "<tbody>" <<|> rows <<| "</tbody>"     
//      case Columns(columns,opt)        => out <<@ ("colgroup",opt) <<|> columns <<| "</colgroup>"  
//      case Column(opt)            => out <<@ ("col",opt) << "</col>"  
      case Row(cells,opt)         => out << "<row>" <<|> cells <<| "</row>"
      case Cell(_, content, colspan, rowspan, opt) => out << "<entry>"; renderBlocks(content, "</entry>") 
    }
    
    def renderUnresolvedReference (ref: Reference) = {
      out << InvalidSpan(SystemMessage(Error,"unresolved reference: " + ref), Text(ref.source)) 
    }
    
    def renderInvalidElement (elem: Invalid[_ <: Element]) = elem match {
      case InvalidBlock(msg, fallback, opt) => if (include(msg)) out << List(Paragraph(List(msg),opt), fallback)
                                               else out << fallback
      case e                                => if (include(e.message)) out << e.message << " " << e.fallback
                                               else out << e.fallback 
    }
    
    def renderSystemMessage (message: SystemMessage) = {
      if (include(message)) 
        out << "<warning><para>" << message.content << "</para></warning>"
    }
    
    
    elem match {
      case e: SystemMessage       => renderSystemMessage(e)
      case e: Table               => renderTable(e)
      case e: TableElement        => renderTableElement(e)
      case e: Reference           => renderUnresolvedReference(e)
      case e: Invalid[_]          => renderInvalidElement(e)
      case e: BlockContainer[_]   => renderBlockContainer(e)
      case e: SpanContainer[_]    => renderSpanContainer(e)
      case e: ListContainer[_]    => renderListContainer(e)
      case e: TextContainer       => renderTextContainer(e)
      case e: Block               => renderSimpleBlock(e)
      case e: Span                => renderSimpleSpan(e)

      case unknown                => ()  
    }  
  } 
}

/** The default instance of the DocBook renderer.
 */
object DocBook extends DocBook(None, "")
