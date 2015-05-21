package com.gu.adapters.http

import com.gu.core._
import net.liftweb.json.JsonAST.JNothing
import org.json4s.ext.JodaTimeSerializers
import org.json4s.{DefaultFormats, FieldSerializer, CustomSerializer}
import org.json4s.JsonAST.{JField, JString}

object JsonFormats {
  val links = FieldSerializer[Argo]({ case ("links", Nil) => None })

  val status = new CustomSerializer[Status](format => (
    {
      case JString(Inactive.asString) => Inactive
      case JString(Pending.asString) => Pending
      case JString(Approved.asString) => Approved
      case JString(Rejected.asString) => Rejected
    },
    {
      case Inactive => JString(Inactive.asString)
      case Pending  => JString(Pending.asString)
      case Approved => JString(Approved.asString)
      case Rejected => JString(Rejected.asString)
    }
  ))

  val all =
    DefaultFormats +
      links +
      status ++
      JodaTimeSerializers.all
}

