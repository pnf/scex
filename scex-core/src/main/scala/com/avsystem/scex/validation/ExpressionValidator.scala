package com.avsystem.scex.validation

import com.avsystem.scex.compiler.ExpressionProfile
import java.{util => ju, lang => jl}
import scala.language.experimental.macros
import scala.reflect.macros.Context
import scala.util.DynamicVariable
import com.avsystem.scex.util.CommonUtils._
import com.avsystem.scex.compiler.annotation.{ContextAdapter, JavaGetterAdapter, BooleanIsGetter}

/**
 * Object used during expression compilation to validate the expression (syntax, invocations, etc.)
 * This must be a Scala object and not a class because it contains macros. Validation is performed against
 * given ExpressionProfile which is injected into this object by ExpressionCompiler by means of a dynamic variable.
 */
object ExpressionValidator {

  val profileVar: DynamicVariable[ExpressionProfile] = new DynamicVariable(null)

  def validate[C, R](expr: R): R = macro validate_impl[C, R]

  def validate_impl[C: c.WeakTypeTag, R](c: Context)(expr: c.Expr[R]): c.Expr[R] = {
    import c.universe._
    val profile = profileVar.value

    expr.tree.foreach { subtree =>
      if (!profile.syntaxValidator.isSyntaxAllowed(c.universe)(subtree)) {
        c.error(subtree.pos, s"Cannot use language construct: ${subtree.getClass.getSimpleName}")
      }
    }

    def needsValidation(symbol: Symbol) =
      symbol != null && (symbol.isMethod || isJavaField(symbol))

    def validateAccess(pos: Position, tpe: Type, symbol: Symbol, icSymbol: Option[Symbol]) {
      if (needsValidation(symbol)) {
        if (!profile.symbolValidator.isInvocationAllowed(c)(tpe, symbol, icSymbol)) {
          c.error(pos, s"Cannot call ${memberSignature(symbol)} on ${tpe.typeSymbol.fullName}")
        }
      }
    }

    lazy val adapterAnnotType = typeOf[JavaGetterAdapter]
    lazy val booleanGetterAnnotType = typeOf[BooleanIsGetter]
    lazy val contextAdapterAnnotType = typeOf[ContextAdapter]

    def isAdapter(tpe: Type) =
      tpe != null && tpe.typeSymbol.annotations.exists(_.tpe =:= adapterAnnotType)

    def isContextAdapter(symbol: Symbol) =
      symbol.annotations.exists(_.tpe =:= contextAdapterAnnotType)

    def isBooleanGetterAdapter(symbol: Symbol) =
      symbol.annotations.exists(_.tpe =:= booleanGetterAnnotType)

    // gets Java getter called by implicit wrapper
    def getJavaGetter(symbol: Symbol, javaTpe: Type): Symbol = {
      val prefix = if (isBooleanGetterAdapter(symbol)) "is" else "get"
      val name = prefix + symbol.name.toString.capitalize

      def fail = throw new Error(s"Could not get java getter for $symbol on $javaTpe")

      javaTpe.member(newTermName(name)) match {
        case s if isJavaParameterlessMethod(s) => s
        case overloaded: TermSymbol => overloaded.alternatives.find(isJavaParameterlessMethod) match {
          case Some(s) => s
          case None => fail
        }
        case _ => fail
      }
    }

    lazy val contextTpe = weakTypeOf[C]

    def validateTree(tree: Tree) {
      tree match {
        case tree@Select(contextAdapter@Ident(_), _) if isContextAdapter(contextAdapter.symbol) =>
          validateAccess(tree.pos, contextTpe, getJavaGetter(tree.symbol, contextTpe), None)

        case tree@Select(apply@Apply(fun, List(qualifier)), _)
          if isStaticImplicitConversion(fun.symbol) && apply.pos == qualifier.pos =>

          if (isAdapter(apply.tpe)) {
            validateAccess(tree.pos, qualifier.tpe, getJavaGetter(tree.symbol, qualifier.tpe), None)
          } else {
            validateAccess(tree.pos, qualifier.tpe, tree.symbol, Some(fun.symbol))
          }

          validateTree(qualifier)

        case tree@Select(qualifier, _) =>
          validateAccess(tree.pos, qualifier.tpe, tree.symbol, None)
          validateTree(qualifier)

        case _ =>
          tree.children.foreach(child => validateTree(child))
      }
    }

    validateTree(expr.tree)

    expr
  }
}
