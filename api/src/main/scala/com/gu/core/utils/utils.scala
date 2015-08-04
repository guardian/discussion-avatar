package com.gu.core.utils

import org.joda.time.format.ISODateTimeFormat
import org.joda.time.{ DateTime, DateTimeZone }
import sun.nio.cs.US_ASCII

object ISODateFormatter {
  val dateFormat = ISODateTimeFormat.dateTimeNoMillis.withZone(DateTimeZone.UTC)
  def parse(s: String): DateTime = dateFormat.parseDateTime(s)
  def print(dt: DateTime): String = dateFormat.print(dt)
}

object ASCII {
  def apply(s: String) = new String(s.getBytes, new US_ASCII)
}

object KVLocationFromID {
  def apply(id: String): String = id.take(4).toList.mkString("/") + "/" + id
}
