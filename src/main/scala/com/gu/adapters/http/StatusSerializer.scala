package com.gu.adapters.http

import com.gu.entities._
import org.json4s.CustomSerializer
import org.json4s.JsonAST.JString

class StatusSerializer extends CustomSerializer[Status](format => (
  {
    case JString("approved") => Approved
    case JString("rejected") => Approved
    case JString("pending") => Approved
    case JString("all") => Approved
  },
  {
    case Approved => JString("approved")
    case Rejected => JString("rejected")
    case Pending  => JString("pending")
    case All      => JString("all")
  }
))
