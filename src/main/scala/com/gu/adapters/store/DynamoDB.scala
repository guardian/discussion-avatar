package com.gu.adapters.store

import java.nio.ByteBuffer

import com.amazonaws.services.dynamodbv2.model.AttributeValue

trait AttributeValues {
  def S(str: String) = new AttributeValue().withS(str)
  def N(number: Long) = new AttributeValue().withN(number.toString)
  def N(number: Double) = new AttributeValue().withN(number.toString)
  def B(bytes: ByteBuffer) = new AttributeValue().withB(bytes)
  def BOOL(bool: Boolean) = new AttributeValue().withBOOL(bool)
}

object AttributeValues extends AttributeValues
