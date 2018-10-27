package com.socksoff.server

import collection.JavaConverters._
import com.typesafe.scalalogging.LazyLogging
import java.net.{Socket, InetAddress}
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

    // Don't actually want to connect to the remote address.
    class DummySocket(address: InetAddress, port: Int) extends Socket() {
      override def getInetAddress(): InetAddress = address
      override def getPort(): Int = port
      override def getLocalAddress(): InetAddress = {
        // The same address tehe base class returns in case of an error.
        InetAddress.getByAddress(Array[Byte](0,0,0,0))
      }
      override def getLocalPort(): Int = 0
    }

    class SmsSocketHandler() extends Socks5Handler {
      override def makeRemoteSocket(address: InetAddress, port: Int): Socket = {
        // Return a socket that the base handler can ask for the address
        // but which doesn't actually connect.
        new DummySocket(address, port)
      }

      override def makeSocketPipe(socket1: Socket, socket2: Socket): SocketPipe = {

        logger.info(s"Connecting to ${socket2.getInetAddress}:${socket2.getPort}")

        // Make a real socket now.
        val destination = new Socket(socket2.getInetAddress, socket2.getPort)

        new SocketPipe(socket1, destination) {
          // Will make two pipes to move the data between the two sockets in both directions.
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