package com.socksoff.sms

/** Implement this and inject with something that forwards to the Android SmsManager. */
trait SmsService {
  def sendTextMessage(message: String): Unit
}