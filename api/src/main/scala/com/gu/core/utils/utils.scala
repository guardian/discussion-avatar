package com.gu.core.utils

import org.joda.time.format.ISODateTimeFormat
import org.joda.time.{DateTime, DateTimeZone}
import org.apache.commons.lang3.StringEscapeUtils.escapeJava

object ISODateFormatter {
  val dateFormat = ISODateTimeFormat.dateTimeNoMillis.withZone(DateTimeZone.UTC)
  def parse(s: String): DateTime = dateFormat.parseDateTime(s)
  def print(dt: DateTime): String = dateFormat.print(dt)
}

object EscapedUnicode {
  def apply(s: String) = escapeJava(s)
}

object KVLocationFromID {
  def apply(id: String): String = id.take(4).toList.mkString("/") + "/" + id
}
