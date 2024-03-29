package com.avsystem.scex

import java.{lang => jl, util => ju}

import com.avsystem.scex.presentation.SymbolAttributes
import com.avsystem.scex.validation.{SymbolValidator, SyntaxValidator}

class ExpressionProfile(
  val name: String,
  val syntaxValidator: SyntaxValidator,
  val symbolValidator: SymbolValidator,
  val symbolAttributes: SymbolAttributes,
  val expressionHeader: String,
  val expressionUtils: NamedSource) {

  override def toString = s"ExpressionProfile[$name]"
}
