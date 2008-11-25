/* NSC -- new Scala compiler
 * Copyright 2005-2007 LAMP/EPFL
 * @author Stephane Micheloud
 */
//$Id: Command.scala 10730 2007-04-18 14:10:07Z michelou $

package scala.man1

trait Command {
  import _root_.scala.tools.docutil.ManPage._

  protected def cn: String
  def command = cn.substring(cn.lastIndexOf(".") + 1, cn.length() - 1)

  protected def MBold(contents: AbstractText) = Mono(Bold(contents))
  protected def MItalic(contents: AbstractText) = Mono(Italic(contents))

  protected def CmdLine(opts: AbstractText) =
    MBold(command) & Mono(" " & opts)

  protected def CmdOption(opt: String, params: AbstractText) =
    Mono(Bold(NDash & opt) & " " & params & " ")

  protected def CmdOption(opt: String): AbstractText =
    Mono(Bold(NDash & opt) & " ")

  protected def CmdOptionLong(opt: String, params: AbstractText) =
    Mono(Bold(NDash & NDash & opt) & " " & params & " ")

  protected def CmdOptionLong(opt: String): AbstractText =
    Mono(Bold(NDash & NDash & opt) & " ")

  protected def Argument(arg: String): AbstractText =
    "<" & Italic(arg) & ">"

  def authors = Section("AUTHOR",

    "Written by Martin Odersky and other members of the " &
    Link("Scala team", "http://scala-lang.org/community/") & ".")

  def copyright = Section("COPYRIGHT",

    "This is open-source software, available to you under a BSD-like license. " &
    "See accomponying \"copyright\" or \"LICENSE\" file for copying conditions. " &
    "There is NO warranty; not even for MERCHANTABILITY or FITNESS FOR A " &
    "PARTICULAR PURPOSE.")

  def bugs = Section("REPORTING BUGS",

    "Report bugs to " & Mono("<scala@listes.epfl.ch>") & ".")

  //private val df = new java.text.SimpleDateFormat("MMM d, yyyy")
  //private val rightNow = new java.util.Date()

  def lastModified: String = "April 18, 2007" // df.format(rightNow)

  def manpage: Document
}
