package com.avsystem.scex
package compiler

import java.{lang => jl, util => ju}

import scala.reflect.internal.util.Position

/**
 * Created: 13-12-2013
 * Author: ghik
 */
class ExpressionSourceFile(
  val exprDef: ExpressionDef,
  sourceName: String,
  val code: String,
  startOffset: Int) extends ScexSourceFile(sourceName, code, shared = false) {

  require(exprDef != null, "Expression definition cannot be null")

  val expressionPos = Position.range(this, startOffset, startOffset, startOffset + exprDef.expression.length)
  lazy val bareSource = new ScexSourceFile(sourceName, exprDef.originalExpression, shared = false)
}
