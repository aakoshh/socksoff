package com.socksoff.sms

import collection.JavaConverters._
import com.typesafe.scalalogging.LazyLogging
import java.net.{Socket, InetAddress}
import java.io.{InputStream, OutputStream, IOException}
import java.util.concurrent.atomic.AtomicLong
import java.util.Base64
import java.nio.charset.StandardCharsets
import scala.collection.concurrent.TrieMap
import scala.util.control.NonFatal
import scala.util.Try
import sockslib.server.io.{Pipe, SocketPipe, StreamPipe}
import sockslib.server.{SocksServerBuilder, Socks5Handler}
import sockslib.common.methods.NoAuthenticationRequiredMethod

class SmsSocksProxyServer(
  smsService: SmsService,
  port: Int)
  extends LazyLogging {

  import SmsSocksProxyServer._

  // How much data to put in a text message.
  val MessageSize = 100

  val proxyServer = {
    SocksServerBuilder
      .newBuilder(classOf[SmsSocketHandler])
      .setSocksMethods(new NoAuthenticationRequiredMethod())
      .setBindPort(port)
      .build()
  }

  def start(): Unit = {
    proxyServer.start()
  }

  def shutdown(): Unit = {
    proxyServer.shutdown()
  }

  val counter = new AtomicLong(0)
  val handlers = TrieMap.empty[Long, SmsSocketHandler]

  /** Parse the text message and forward it to the right handler. */
  def onTextMessage(message: String): Unit = {
    try {
      val (header, body) = message.splitAt(message.indexOf('\n'))
      val Array(handlerId, chunkId) = header.split(':')

      logger.debug(s"Received chunk $handlerId:$chunkId")

      // The body will have the header separator.
      val handler = handlers(handlerId.toLong)
      handler.onChunk(chunkId.toInt, body.drop(1))
    } catch {
      case NonFatal(ex) =>
        logger.error(s"Unable to parse SMS: $message")
    }
  }

  // Inner class so it can have access to the `smsService`
  class SmsSocketHandler() extends Socks5Handler {

    // Register so we can pick the right handler when a message comes.
    val id = counter.incrementAndGet()
    handlers += id -> this

    // An incrementing counter for outgoing chunks, starting from 1.
    val chunkIds = Stream.from(0).iterator

    // Keep track of the chunks we received from in SMS messages.
    val chunks = TrieMap.empty[Int, Array[Byte]]

    // When it's known, set the last chunk ID.
    var lastChunkId = 0

    def onChunk(chunkId: Int, text: String) = {
      if (text.endsWith(".")) {
        val bytes = Base64.getDecoder.decode(text.dropRight(1))
        chunks(chunkId) = bytes
        // Indicate that we got the last chunk.
        lastChunkId = chunkId
      } else {
        val bytes = Base64.getDecoder.decode(text)
        chunks(chunkId) = bytes
      }
    }

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

        def sendInSMS(buffer: Array[Byte], length: Int): Unit = {
          val text = Base64.getEncoder.encodeToString(buffer.take(length))
          val chunks = text.grouped(MessageSize) map { chunk =>
            val chunkId = chunkIds.next()
            val message = s"$id:$chunkId\n$chunk"
            message
          }
          chunks foreach { chunk =>
            logger.debug(s"Sending SMS: $id:$chunkId")
            smsService.sendTextMessage(chunk)
          }
        }

        // Will make two pipes to move the data between the two sockets in both directions.
        override def makeStreamPipe(in: InputStream, out: OutputStream, name: String): Pipe = {

          new StreamPipe(in, out, name) {

            // Keep track of which chunk we're expecting next.
            var nextChunkId = 1

            override def doTransfer(buffer: Array[Byte]): Int = {
              try {
                (in, out) match {
                  case (localIn, _: DummyOutputStream) =>
                    // Send the data as SMS messages.
                    val length = localIn.read(buffer)
                    if (length > 0) {
                      sendInSMS(buffer, length)
                      length
                    } else {
                      // There's no notification when the message is over, it just waits for the read.
                      -1
                    }

                  case (_: DummyInputStream, localOut) =>
                    if (chunks contains nextChunkId) {
                      val buffer = chunks(nextChunkId)
                      send(localOut, buffer, buffer.length)
                      nextChunkId += 1
                      buffer.length
                    } else if (nextChunkId > lastChunkId) {
                      // We sent out the last chunk, nothing more to do.
                      -1
                    } else {
                      // Let the outside loop call this again a bit later.
                      Thread.sleep(10)
                      0
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
}

object SmsSocksProxyServer {
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
}