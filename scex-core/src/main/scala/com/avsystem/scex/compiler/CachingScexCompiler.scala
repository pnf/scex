package com.avsystem.scex
package compiler

import java.util.concurrent.{ExecutionException, TimeUnit}
import java.{lang => jl, util => ju}

import com.avsystem.scex.parsing.PositionMapping
import com.avsystem.scex.validation.{SymbolValidator, SyntaxValidator}
import com.google.common.cache.CacheBuilder

import scala.util.Try

trait CachingScexCompiler extends ScexCompiler {

  import com.avsystem.scex.util.CommonUtils._

  private val preprocessingCache = CacheBuilder.newBuilder
    .expireAfterAccess(settings.expressionExpirationTime.value, TimeUnit.SECONDS)
    .maximumSize(settings.expressionCacheSize.value)
    .build[(String, Boolean), (String, PositionMapping)]

  private val expressionCache = CacheBuilder.newBuilder
    .expireAfterAccess(settings.expressionExpirationTime.value, TimeUnit.SECONDS)
    .maximumSize(settings.expressionCacheSize.value)
    .build[ExpressionDef, Try[RawExpression]]

  // holds names of packages to which profiles are compiled
  private val profileCompilationResultsCache =
    CacheBuilder.newBuilder.build[ExpressionProfile, Try[String]]

  // holds names of packages to which utils are compiled
  private val utilsCompilationResultsCache =
    CacheBuilder.newBuilder.build[String, Try[String]]

  // holds code of implicit adapters over Java classes that add Scala-style getters to Java bean getters
  private val javaGetterAdaptersCache =
    CacheBuilder.newBuilder.build[(Class[_], Boolean), Try[Option[String]]]

  private val syntaxValidatorsCache =
    CacheBuilder.newBuilder.build[String, SyntaxValidator]

  private val symbolValidatorsCache =
    CacheBuilder.newBuilder.build[String, SymbolValidator]

  override protected def preprocess(expression: String, template: Boolean) =
    preprocessingCache.get((expression, template), callable(super.preprocess(expression, template)))

  override protected def compileExpression(exprDef: ExpressionDef) =
    expressionCache.get(exprDef, callable(super.compileExpression(exprDef)))

  override protected def compileProfileObject(profile: ExpressionProfile) =
    profileCompilationResultsCache.get(profile, callable(super.compileProfileObject(profile)))

  override protected def compileExpressionUtils(source: NamedSource) =
    utilsCompilationResultsCache.get(source.name, callable(super.compileExpressionUtils(source)))

  override protected def compileJavaGetterAdapter(clazz: Class[_], full: Boolean) =
    javaGetterAdaptersCache.get((clazz, full), callable(super.compileJavaGetterAdapter(clazz, full)))

  override def compileSyntaxValidator(source: NamedSource) =
    unwrapExecutionException(syntaxValidatorsCache.get(source.name, callable(super.compileSyntaxValidator(source))))

  override def compileSymbolValidator(source: NamedSource) =
    unwrapExecutionException(symbolValidatorsCache.get(source.name, callable(super.compileSymbolValidator(source))))

  override def reset(): Unit = underLock {
    super.reset()
    expressionCache.invalidateAll()
    profileCompilationResultsCache.invalidateAll()
    utilsCompilationResultsCache.invalidateAll()
    javaGetterAdaptersCache.invalidateAll()
    syntaxValidatorsCache.invalidateAll()
    symbolValidatorsCache.invalidateAll()
  }

  private def unwrapExecutionException[T](code: => T) =
    try code catch {
      case e: ExecutionException => throw e.getCause
    }
}
