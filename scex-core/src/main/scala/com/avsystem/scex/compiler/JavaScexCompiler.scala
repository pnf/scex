package com.avsystem.scex.compiler

import JavaTypeParsing._
import com.avsystem.scex.compiler.ScexCompiler.{CompileError, CompilationFailedException}
import com.avsystem.scex.compiler.ScexPresentationCompiler.Param
import com.avsystem.scex.util.CacheImplicits
import com.avsystem.scex.{TypeTag, Expression}
import com.google.common.cache.CacheBuilder
import java.lang.reflect.Type
import java.{util => ju, lang => jl}
import scala.beans.{BooleanBeanProperty, BeanProperty}

trait JavaScexCompiler extends ScexPresentationCompiler {

  import CacheImplicits._

  private val typesCache = CacheBuilder.newBuilder.weakKeys
    .build[Type, String](javaTypeAsScalaType _)

  @throws[CompilationFailedException]
  def getCompiledStringExpression[C](
    profile: ExpressionProfile,
    expression: String,
    contextClass: Class[C]): Expression[C, String] = {

    getCompiledStringExpressionByType(profile, expression, contextClass)
  }

  @throws[CompilationFailedException]
  def getCompiledStringExpression[C](
    profile: ExpressionProfile,
    expression: String,
    contextType: TypeTag[C]): Expression[C, String] = {

    getCompiledStringExpressionByType(profile, expression, contextType)
  }

  @throws[CompilationFailedException]
  def getCompiledStringExpressionByType[C](
    profile: ExpressionProfile,
    expression: String,
    contextType: Type): Expression[C, String] = {

    val scalaContextType = typesCache.get(contextType)
    val contextClass = erasureOf(contextType)

    getCompiledStringExpression(profile, expression, scalaContextType, contextClass)
  }

  @throws[CompilationFailedException]
  def getCompiledExpression[C, R](
    profile: ExpressionProfile,
    expression: String,
    contextType: Class[C],
    resultType: Class[R]): Expression[C, R] = {

    getCompiledExpressionByTypes(profile, expression, contextType, resultType)
  }

  @throws[CompilationFailedException]
  def getCompiledExpression[C, R](
    profile: ExpressionProfile,
    expression: String,
    contextType: Class[C],
    resultType: TypeTag[R]): Expression[C, R] = {

    getCompiledExpressionByTypes(profile, expression, contextType, resultType)
  }

  @throws[CompilationFailedException]
  def getCompiledExpression[C, R](
    profile: ExpressionProfile,
    expression: String,
    contextType: TypeTag[C],
    resultType: Class[R]): Expression[C, R] = {

    getCompiledExpressionByTypes(profile, expression, contextType, resultType)
  }

  @throws[CompilationFailedException]
  def getCompiledExpression[C, R](
    profile: ExpressionProfile,
    expression: String,
    contextType: TypeTag[C],
    resultType: TypeTag[R]): Expression[C, R] = {

    getCompiledExpressionByTypes(profile, expression, contextType, resultType)
  }

  @throws[CompilationFailedException]
  def getCompiledExpressionByTypes[C, R](
    profile: ExpressionProfile,
    expression: String,
    contextType: Type,
    resultType: Type): Expression[C, R] = {

    val scalaContextType = typesCache.get(contextType)
    val contextClass = erasureOf(contextType)
    val scalaResultType = typesCache.get(resultType)

    getCompiledExpression[C, R](profile, expression, scalaContextType, contextClass, scalaResultType)
  }

  class JavaInteractiveContext(wrapped: InteractiveContext) {

    import scala.collection.JavaConverters._

    private def memberToJava(scalaMember: ScexPresentationCompiler.Member) = scalaMember match {
      case ScexPresentationCompiler.Member(name, params, tpe, implicitlyAdded) =>
        JavaScexCompiler.Member(name, params.map(_.asJavaCollection).asJavaCollection, tpe, implicitlyAdded)
    }

    private def completionToJava(scalaCompletion: ScexPresentationCompiler.Completion) = scalaCompletion match {
      case ScexPresentationCompiler.Completion(members, errors) =>
        JavaScexCompiler.Completion(members.map(memberToJava).asJavaCollection, errors.asJavaCollection)
    }

    def getErrors(expression: String) =
      wrapped.getErrors(expression).asJavaCollection

    def getScopeCompletion(expression: String, position: Int) =
      completionToJava(wrapped.getScopeCompletion(expression, position))

    def getTypeCompletion(expression: String, position: Int) =
      completionToJava(wrapped.getTypeCompletion(expression, position))
  }

  def getJavaInteractiveContext(
    profile: ExpressionProfile,
    contextType: Type,
    resultType: Type): JavaInteractiveContext = {

    new JavaInteractiveContext(getInteractiveContext(
      profile, typesCache.get(contextType), erasureOf(contextType), typesCache.get(resultType)))
  }
}

object JavaScexCompiler {
  def apply(compilerConfig: ScexCompilerConfig) =
    new DefaultJavaScexCompiler(compilerConfig)

  case class Member(@BeanProperty name: String, @BeanProperty params: ju.Collection[ju.Collection[Param]],
    @BeanProperty `type`: String, @BooleanBeanProperty implicitlyAdded: Boolean)

  case class Completion(@BeanProperty members: ju.Collection[Member], @BeanProperty errors: ju.Collection[CompileError])

}
