/* NSC -- new Scala compiler
 * Copyright 2005-2009 LAMP/EPFL
 * @author  Martin Odersky
 */
// $Id: NameTransformer.scala 16894 2009-01-13 13:09:41Z cunei $

package scala.tools.nsc.util

object NameTransformer {
  private val nops = 128
  private val ncodes = 26 * 26

  private class OpCodes(val op: Char, val code: String, val next: OpCodes)

  private val op2code = new Array[String](nops)
  private val code2op = new Array[OpCodes](ncodes)

  private def enterOp(op: Char, code: String) = {
    op2code(op) = code
    val c = (code.charAt(1) - 'a') * 26 + code.charAt(2) - 'a'
    code2op(c) = new OpCodes(op, code, code2op(c))
  }

  /* Note: decoding assumes opcodes are only ever lowercase. */
  enterOp('~', "$tilde")
  enterOp('=', "$eq")
  enterOp('<', "$less")
  enterOp('>', "$greater")
  enterOp('!', "$bang")
  enterOp('#', "$hash")
  enterOp('%', "$percent")
  enterOp('^', "$up")
  enterOp('&', "$amp")
  enterOp('|', "$bar")
  enterOp('*', "$times")
  enterOp('/', "$div")
  enterOp('+', "$plus")
  enterOp('-', "$minus")
  enterOp(':', "$colon")
  enterOp('\\', "$bslash")
  enterOp('?', "$qmark")
  enterOp('@', "$at")

  /** Replace operator symbols by corresponding "<code>$op_name</code>".
   *
   *  @param name ...
   *  @return     ...
   */
  def encode(name: String): String = {
    var buf: StringBuilder = null
    val len = name.length()
    var i = 0
    while (i < len) {
      val c = name charAt i
      if (c < nops && (op2code(c) ne null)) {
        if (buf eq null) {
          buf = new StringBuilder()
          buf.append(name.substring(0, i))
        }
        buf.append(op2code(c))
      /* Handle glyphs that are not valid Java/JVM identifiers */
      } else if (!Character.isJavaLetterOrDigit(c)) {
	if (buf eq null) {
	  buf = new StringBuilder()
	  buf.append(name.substring(0, i))
	}
        /* Annoying hack to format a hexadeciaml number with leading
           zeros -- there does not appear to be any function pre-Java
           1.5 to do this. */ 
	buf.append("$u" + Integer.toHexString(c + 0x10000).substring(1).toUpperCase)
      } else if (buf ne null) {
        buf.append(c)
      }
      i += 1
    }
    if (buf eq null) name else buf.toString()
  }

  /** Replace <code>$op_name</code> by corresponding operator symbol.
   *
   *  @param name0 ...
   *  @return      ...
   */
  def decode(name0: String): String = {
    //System.out.println("decode: " + name);//DEBUG
    val name = if (name0.endsWith("<init>")) name0.substring(0, name0.length() - ("<init>").length()) + "this"
               else name0;
    var buf: StringBuilder = null
    val len = name.length()
    var i = 0
    while (i < len) {
      var ops: OpCodes = null
      var unicode = false
      val c = name charAt i
      if (c == '$' && i + 2 < len) {
        val ch1 = name.charAt(i+1)
        if ('a' <= ch1 && ch1 <= 'z') {
          val ch2 = name.charAt(i+2)
          if ('a' <= ch2 && ch2 <= 'z') {
            ops = code2op((ch1 - 'a') * 26 + ch2 - 'a')
            while ((ops ne null) && !name.startsWith(ops.code, i)) ops = ops.next
            if (ops ne null) {
              if (buf eq null) {
                buf = new StringBuilder()
                buf.append(name.substring(0, i))
              }
              buf.append(ops.op)
              i += ops.code.length()
            }
            /* Handle the decoding of Unicode glyphs that are 
             * not valid Java/JVM identifiers */
          } else if ((len - i) >= 6 && // Check that there are enough characters left
	             ch1 == 'u' && 
                     ((Character.isDigit(ch2)) || 
		     ('A' <= ch2 && ch2 <= 'F'))) {
            /* Skip past "$u", next four should be hexadecimal */
            val hex = name.substring(i+2, i+6)
            try {
              val str = Integer.parseInt(hex, 16).toChar
              if (buf eq null) {
                buf = new StringBuilder()
                buf.append(name.substring(0, i))
              }
              buf.append(str)
              /* 2 for "$u", 4 for hexadecimal number */
              i += 6
              unicode = true
            } catch {
              case _:NumberFormatException =>
                /* <code>hex</code> did not decode to a hexadecimal number, so
                 * do nothing. */
            }
                       }
        }
      }
      /* If we didn't see an opcode or encoded Unicode glyph, and the
        buffer is non-empty, write the current character and advance
         one */ 
      if ((ops eq null) && !unicode) { 
	if (buf ne null)
          buf.append(c) 
	i += 1 
      } 
    }
    //System.out.println("= " + (if (buf == null) name else buf.toString()));//DEBUG
    if (buf eq null) name else buf.toString()
  }
}
