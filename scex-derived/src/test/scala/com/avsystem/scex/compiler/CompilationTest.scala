package com.avsystem.scex
package compiler

import java.{lang => jl, util => ju}

import com.avsystem.scex.compiler.ScexCompiler.CompilationFailedException
import com.avsystem.scex.japi.DefaultJavaScexCompiler
import com.avsystem.scex.presentation.{Attributes, SymbolAttributes}
import com.avsystem.scex.symboldsl.SymbolInfo
import com.avsystem.scex.util.{PredefinedAccessSpecs, SimpleContext}
import com.avsystem.scex.validation.SymbolValidator.MemberAccessSpec
import com.avsystem.scex.validation.{SymbolValidator, SyntaxValidator}
import org.scalatest.{BeforeAndAfterAll, Suite}

import scala.reflect.io.AbstractFile
import scala.reflect.runtime.universe.TypeTag

/**
 * Created: 18-11-2013
 * Author: ghik
 */
trait CompilationTest extends BeforeAndAfterAll {
  this: Suite =>
  val settings = new ScexSettings
  settings.classfileDirectory.value = "testClassfileCache"
  val compiler = new DefaultJavaScexCompiler(settings)

  override protected def beforeAll() = {
    val classfileDir = AbstractFile.getDirectory(compiler.settings.classfileDirectory.value)
    if (classfileDir != null) {
      classfileDir.delete()
    }
  }

  def catchAndPrint(code: => Any) {
    try code catch {
      case t: Throwable => t.printStackTrace(System.out)
    }
  }

  private var profileId = 0

  def newProfileName() = {
    profileId += 1
    "test" + profileId
  }

  def createProfile(acl: List[MemberAccessSpec] = Nil, attributes: List[SymbolInfo[Attributes]] = Nil,
    header: String = "import com.avsystem.scex.compiler._", utils: String = "") = {

    val profileName = newProfileName()
    val expressionUtils = NamedSource(profileName, utils)
    new ExpressionProfile(profileName, SyntaxValidator.SimpleExpressions, SymbolValidator(acl),
      SymbolAttributes(attributes), header, expressionUtils)
  }

  def assertMemberAccessForbidden(expr: => Any) {
    try expr catch {
      case e: CompilationFailedException =>
        assert(e.errors.forall(_.msg.startsWith("Member access forbidden")))
    }
  }

  def evaluateTemplate[T: TypeTag](expr: String, acl: List[MemberAccessSpec] = defaultAcl, header: String = "") =
    compiler.getCompiledExpression[SimpleContext[Unit], T](
      createProfile(acl), expr, template = true, header).apply(SimpleContext(()))

  def evaluate[T: TypeTag](expr: String, acl: List[MemberAccessSpec] = defaultAcl) = {
    compiler.getCompiledExpression[SimpleContext[Unit], T](createProfile(acl), expr, template = false).apply(SimpleContext(()))
  }

  def defaultAcl = PredefinedAccessSpecs.basicOperations
}
