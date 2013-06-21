Sandoc
======

Sandoc is a simple markup converter like his big brother
[Pandoc](http://johnmacfarlane.net/pandoc/). It is self contained in one JAR
, so there is no need to install LaTeX, DocBook, Haskell or Ruby for PDF
creation.

## Features

### Supported input formats

* [Markdown](http://daringfireball.net/projects/markdown/): with the
  [Laika toolkit](http://planet42.github.io/Laika)
* [reStructuredText](http://docutils.sourceforge.net/docs/ref/rst/introduction.html): with the 
  [Laika toolkit](http://planet42.github.io/Laika)
* [AsciiDoc](http://www.methods.co.nz/asciidoc/): with
  [AsciiDoctor](http://asciidoctor.org/)

### Supported output formats

* HTML
* [DocBook](http://www.docbook.org/)
* PDF: via DocBook and [Apache FOP](http://xmlgraphics.apache.org/fop/)

## Usage

TODO: As Sandoc is in the early beta stage, there were no JAR packages released.

    > sbt
    > run --help

    Sandoc 0.2
    Usage: sandoc -o file.pdf markdown1.md markdown2.md
      -f, --from-format  <arg>   Specify input format.  Can be markdown, rst
                                 (reStructuredText) or asciidoc.
      -o, --output  <arg>        Write output to this file.
          --title  <arg>         Title of the document for DocBook/PDF output.
      -t, --to-format  <arg>     Specify output format. Can be html, docbook or pdf.
      -x, --xslt  <arg>          Specify the path of a custom XSL template for the
                                 DocBook creation.
          --help                 Show help message
          --version              Show version of this program

     trailing arguments:
      input-files (required)

Create the Laika-Documentation as PDF:

    run -o laika.pdf --title Laika testDocs/intro.md testDocs/basics.md testDocs/markup.md testDocs/renderer.md testDocs/tree-rewriting.md testDocs/parser.md testDocs/renderer.md

## Build instructions

Sandoc is written in less than 200 lines of Scala code and uses the [sbt](http://www.scala-sbt.org/) build tool:

    sbt package

For PDF hypenation support you may want to include [FOP  hyphenation pattern files](http://xmlgraphics.apache.org/fop/0.95/hyphenation.html).
Because of licence issues they are not distributed with Sandoc.

## License

Apache License, Version 2.0
