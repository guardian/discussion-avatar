package com.gu.adapters.http

import com.gu.entities._
import org.json4s.CustomSerializer
import org.json4s.JsonAST.JString

class StatusSerializer extends CustomSerializer[Status](format => (
  {
    case JString(Inactive.asString) => Inactive
    case JString(Pending.asString) => Pending
    case JString(Approved.asString) => Approved
    case JString(Rejected.asString) => Rejected
    case JString(All.asString) => All
  },
  {
    case Inactive => JString(Inactive.asString)
    case Pending  => JString(Pending.asString)
    case Approved => JString(Approved.asString)
    case Rejected => JString(Rejected.asString)
    case All      => JString(All.asString)
  }
))