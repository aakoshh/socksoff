package com.socksoff.server

import com.typesafe.scalalogging.LazyLogging
import scala.util.control.NonFatal
import sockslib.server.SocksServerBuilder

object ServerMain
  extends LazyLogging {
  def main(args: Array[String]): Unit = {
    try {
      // Try a simple socks erver.
      val proxyServer = SocksServerBuilder.buildAnonymousSocks5Server
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
}