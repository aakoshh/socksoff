package com.socksoff.server

import collection.JavaConverters._
import com.typesafe.scalalogging.LazyLogging
import java.net.Socket;
import java.io.{InputStream, OutputStream, IOException}
import scala.util.control.NonFatal
import sockslib.server.io.{Pipe, SocketPipe, StreamPipe}
import sockslib.server.{SocksServerBuilder, Socks5Handler}
import sockslib.common.methods.NoAuthenticationRequiredMethod

object ServerMain
  extends LazyLogging {
  def main(args: Array[String]): Unit = {
    try {
      // Try a simple socks erver.
      // val proxyServer = SocksServerBuilder.buildAnonymousSocks5Server
      val proxyServer = makeSocks5Server

      proxyServer.start()

      logger.info("SOCKS5 server listening on port 1080...")

      // Wait for Ctrl+C
      Thread.currentThread.join
    } catch {
      case NonFatal(ex) =>
        logger.error("Fatal error, exiting.", ex)
        System.exit(1)
    }
  }

  def makeSocks5Server = {

    class SmsSocketHandler() extends Socks5Handler {
      override def makeSocketPipe(socket1: Socket, socket2: Socket): SocketPipe = {

        new SocketPipe(socket1, socket2) {
          override def makeStreamPipe(in: InputStream, out: OutputStream, name: String): Pipe = {

            new StreamPipe(in, out, name) {
              override def doTransfer(buffer: Array[Byte]): Int = {
                try {
                  val length = in.read(buffer)
                  if (length > 0) {
                    out.write(buffer, 0, length)
                    out.flush()
                    getPipeListeners.asScala foreach { lnr =>
                      lnr.onTransfer(this, buffer, length)
                    }
                    length
                  } else {
                    -1
                  }
                } catch {
                  case ex: IOException =>
                    getPipeListeners.asScala foreach { lnr =>
                      lnr.onError(this, ex)
                    }
                    stop()
                    -1
                }
              }
            }
          }
        }
      }
    }

    SocksServerBuilder
      .newBuilder(classOf[SmsSocketHandler])
      .setSocksMethods(new NoAuthenticationRequiredMethod())
      .setBindPort(1080)
      .build()
  }
}