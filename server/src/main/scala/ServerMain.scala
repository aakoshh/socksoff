package com.socksoff.server

import collection.JavaConverters._
import com.typesafe.scalalogging.LazyLogging
import java.net.{Socket, InetAddress}
import java.io.{InputStream, OutputStream, IOException}
import scala.util.control.NonFatal
import scala.util.Try
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

    class DummyInputStream() extends InputStream {
      override def read() = 0
    }

    class DummyOutputStream()extends OutputStream() {
      override def write(b: Int): Unit = {}
    }

    // Don't actually want to connect to the remote address.
    class DummySocket(address: InetAddress, port: Int) extends Socket() {
      override def getInetAddress(): InetAddress = address
      override def getPort(): Int = port
      override def getLocalAddress(): InetAddress = {
        // The same address tehe base class returns in case of an error.
        InetAddress.getByAddress(Array[Byte](0,0,0,0))
      }
      override def getLocalPort(): Int = 0
      override def getInputStream(): InputStream = new DummyInputStream()
      override def getOutputStream(): OutputStream = new DummyOutputStream()
    }

    class SmsSocketHandler() extends Socks5Handler {
      override def makeRemoteSocket(address: InetAddress, port: Int): Socket = {
        // Return a socket that the base handler can ask for the address
        // but which doesn't actually connect.
        new DummySocket(address, port)
      }

      // socket2 is going to be the dummy.
      override def makeSocketPipe(socket1: Socket, socket2: Socket): SocketPipe = {

        logger.info(s"Connecting to ${socket2.getInetAddress}:${socket2.getPort}")

        // Make a real socket now.
        val remote = new Socket(socket2.getInetAddress, socket2.getPort)
        val remoteIn = remote.getInputStream
        val remoteOut = remote.getOutputStream

        new SocketPipe(socket1, socket2) {

          override def close(): Boolean = {
            Try(remote.close())
            super.close()
          }

          // Will make two pipes to move the data between the two sockets in both directions.
          override def makeStreamPipe(in: InputStream, out: OutputStream, name: String): Pipe = {

            new StreamPipe(in, out, name) {
              override def doTransfer(buffer: Array[Byte]): Int = {
                try {
                  (in, out) match {
                    case (localIn, _: DummyOutputStream) =>
                      // Here we can send via SMS instead.
                      val length = localIn.read(buffer)
                      if (length > 0) {
                        send(remoteOut, buffer, length)
                      } else {
                        -1
                      }

                    case (_: DummyInputStream, localOut) =>
                      // `in` is the DummySocket, so we have to check if got all the SMS messages
                      // and send it to it if we do, in chunks.
                      val length = remoteIn.read(buffer)
                      if (length > 0) {
                        send(localOut, buffer, length)
                      } else {
                        -1
                      }

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

              def send(out: OutputStream, buffer: Array[Byte], length: Int): Int = {
                out.write(buffer, 0, length)
                out.flush()
                getPipeListeners.asScala foreach { lnr =>
                  lnr.onTransfer(this, buffer, length)
                }
                length
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