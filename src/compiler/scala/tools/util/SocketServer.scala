/*                     __                                               *\
**     ________ ___   / /  ___     Scala API                            **
**    / __/ __// _ | / /  / _ |    (c) 2002-2007, LAMP/EPFL             **
**  __\ \/ /__/ __ |/ /__/ __ |    http://scala-lang.org/               **
** /____/\___/_/ |_/____/_/ | |                                         **
**                          |/                                          **
\*                                                                      */

// $Id: SocketServer.scala 14416 2008-03-19 01:17:25Z mihaylov $

package scala.tools.util

import java.lang.System
import java.io.PrintWriter
import java.io.BufferedOutputStream
import java.io.{BufferedReader, InputStreamReader}
import java.io.IOException
import java.net.ServerSocket

/** The abstract class <code>SocketServer</code> implements the server
 *  communication for the fast Scala compiler.
 *
 *  @author  Martin Odersky
 *  @version 1.0
 */
abstract class SocketServer {

  def shutDown: Boolean
  def session()

  var out: PrintWriter = _
  var in: BufferedReader = _

  def fatal(msg: String): Nothing = {
    System.err.println(msg)
    exit(1)
  }

  val port: Int = try {
    val s = new ServerSocket(0) // a port of 0 creates a socket on any free port.
    val p = s.getLocalPort()
    s.close()
    p
  } catch {
    case e: IOException =>
      fatal("Could not listen on any port; exiting.")
  }

  def run() {
    while (!shutDown) {
      val serverSocket = try {
        new ServerSocket(port)
      } catch {
        case e: IOException =>
          fatal("Could not listen on port: " + port + "; exiting.")
      }
      val clientSocket = try {
        serverSocket.accept()
      } catch {
        case e: IOException =>
          fatal("Accept on port " + port + " failed; exiting.")
      }

      out = new PrintWriter(clientSocket.getOutputStream(), true)
      in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()))
      val bufout = new BufferedOutputStream(clientSocket.getOutputStream, 10240)

      scala.Console.withOut(bufout) {
        session()
      }
      
      bufout.close()
      out.close()
      in.close()
      clientSocket.close()
      serverSocket.close()
    }
  }

}

