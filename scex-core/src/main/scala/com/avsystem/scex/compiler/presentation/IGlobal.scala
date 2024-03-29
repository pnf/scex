package com.avsystem.scex
package compiler.presentation

import java.{lang => jl, util => ju}

import com.avsystem.scex.compiler.ScexGlobal

import scala.collection.mutable
import scala.tools.nsc.Settings
import scala.tools.nsc.interactive.Global
import scala.tools.nsc.reporters.Reporter
import scala.tools.nsc.symtab.Flags.{ACCESSOR, PARAMACCESSOR}

/**
 * Created: 13-12-2013
 * Author: ghik
 *
 * I needed to hack a custom implementation of type completion, hence this class.
 */
class IGlobal(settings: Settings, reporter: Reporter, val classLoader: ClassLoader)
  extends Global(settings, reporter) with ScexGlobal {

  import definitions._

  abstract class ScexMember extends Member {
    def ownerTpe: Type

    def implicitTree: Tree

    def implicitType: Type

    override def implicitlyAdded = implicitTree != EmptyTree
  }

  case class ScexTypeMember(
    ownerTpe: Type,
    sym: Symbol,
    tpe: Type,
    accessible: Boolean,
    implicitTree: Tree,
    implicitType: Type) extends ScexMember

  case class ScexScopeMember(
    sym: Symbol,
    tpe: Type,
    accessible: Boolean,
    viaImport: Tree) extends ScexMember {
    def ownerTpe = viaImport.tpe

    def implicitTree = EmptyTree

    def implicitType = NoType
  }

  private class Members extends mutable.LinkedHashMap[Name, Set[ScexTypeMember]] {
    override def default(key: Name) = Set()

    private def matching(sym: Symbol, symtpe: Type, ms: Set[ScexTypeMember]): Option[ScexTypeMember] = ms.find { m =>
      (m.sym.name == sym.name) && (m.sym.isType || (m.tpe matches symtpe))
    }

    private def keepSecond(m: ScexTypeMember, sym: Symbol, implicitTree: Tree): Boolean = {
      val implicitlyAdded = implicitTree != EmptyTree
      def superclasses(symbol: Symbol): Set[Symbol] =
        if (symbol.isType) symbol.asType.toType.baseClasses match {
          case _ :: tail => tail.toSet
          case Nil => Set.empty
        } else Set.empty

      def argumentType(tpe: Type) = tpe match {
        case MethodType(List(param), _) => param.typeSignature
        case _ => NoType
      }

      def compare[A](a1: A, a2: A)(f: (A, A) => Boolean) =
        (if (f(a1, a2)) 1 else 0) + (if (f(a2, a1)) -1 else 0)

      lazy val higherPriorityImplicit = m.implicitlyAdded && implicitlyAdded && {
        val specificityPoints = compare(argumentType(implicitTree.tpe), argumentType(m.implicitTree.tpe))(_ <:< _)
        val priorityPoints = compare(implicitTree.symbol.owner, m.implicitTree.symbol.owner)((s1, s2) => superclasses(s1).contains(s2))
        specificityPoints + priorityPoints > 0
      }

      (m.sym.hasFlag(ACCESSOR | PARAMACCESSOR) && !sym.hasFlag(ACCESSOR | PARAMACCESSOR) &&
        (!implicitlyAdded || m.implicitlyAdded)) || higherPriorityImplicit
    }

    def add(sym: Symbol, pre: Type, implicitTree: Tree)(toMember: (Symbol, Type) => ScexTypeMember) {
      if (sym.hasGetter) {
        add(sym.getterIn(sym.owner), pre, implicitTree)(toMember)
      } else if (!sym.name.decodedName.containsName("$") && !sym.isSynthetic && sym.hasRawInfo) {
        val symtpe = pre.memberType(sym) onTypeError ErrorType
        matching(sym, symtpe, this(sym.name)) match {
          case Some(m) =>
            if (keepSecond(m, sym, implicitTree)) {
              this(sym.name) = this(sym.name) - m + toMember(sym, symtpe)
            }
          case None =>
            this(sym.name) = this(sym.name) + toMember(sym, symtpe)
        }
      }
    }

    def addNonShadowed(other: Members) = {
      for ((name, ms) <- other)
        if (ms.nonEmpty && this(name).isEmpty) this(name) = ms
    }

    def allMembers: Vector[ScexTypeMember] = values.toVector.flatten
  }

  case class TypeCompletionContext(context: Context, prefixTree: Tree, pre: Type, ownerTpe: Type)

  def typeCompletionContext(typedTree: Tree, pos: Position) = {
    val context = doLocateContext(pos)
    var tree = typedTree

    // apparently, in some cases with dynamics, the tree comes completely untyped

    if (tree.tpe == null) {
      tree = analyzer.newTyper(context).typedQualifier(tree)
    }

    // now, drop incomplete selection

    tree match {
      case Select(qual, name) if tree.tpe == ErrorType && !(qual.tpe != ErrorType && qual.tpe <:< dynamicTpe) =>
        tree = qual
      case SelectDynamic(qual, "<error>") =>
        tree = qual
      case _ =>
    }

    // manually help the compiler understand that the qualifier is a proper dynamic call

    tree match {
      case Select(qual, name) if tree.tpe == ErrorType && (qual.tpe != ErrorType && qual.tpe <:< dynamicTpe) =>
        val literalPos = tree.pos.withStart(tree.pos.end min (qual.pos.end + 1)).makeTransparent
        val literal = Literal(Constant(name.decoded)).setPos(literalPos)
        tree = Apply(Select(qual, TermName("selectDynamic")).setPos(qual.pos), List(literal)).setPos(tree.pos)
      case _ =>
    }

    // possibly retype to catch up with changes made to the tree

    val shouldRetypeQualifier = tree.tpe match {
      case null => true
      case mt: MethodType => mt.isImplicit || mt.params.isEmpty
      case _ => false
    }

    if (shouldRetypeQualifier) {
      tree = analyzer.newTyper(context).typedQualifier(tree)
    }

    // remove dangling implicit conversion

    tree match {
      case ImplicitlyConverted(qual, _) =>
        tree = qual
      case _ =>
    }

    val pre = stabilizedType(tree)

    val ownerTpe = tree.tpe match {
      case analyzer.ImportType(expr) => expr.tpe
      case null => pre
      case MethodType(List(), rtpe) => rtpe
      case _ => tree.tpe
    }

    TypeCompletionContext(context, tree, pre, ownerTpe)
  }

  /**
   * Reimplementation of `scala.tools.interactive.Global.typeMembers` method, adjusted to SCEX needs:
   * <ul>
   * <li>returned completion members contain more information (e.g. implicit view tree instead of just symbol)</li>
   * <li>there is a number of hacks and workarounds for scalac inability to properly handle dynamic invocations</li>
   * <li>all members are returned at once, instead of returning a stream</li>
   * </ul>
   */
  def typeMembers(completionContext: TypeCompletionContext) = {
    val TypeCompletionContext(context, tree, pre, ownerTpe) = completionContext

    val superAccess = tree.isInstanceOf[Super]
    val members = new Members

    def addTypeMember(sym: Symbol, pre: Type, implicitTree: Tree, implicitType: Type) = {
      val implicitlyAdded = implicitTree != EmptyTree
      members.add(sym, pre, implicitTree) { (s, st) =>
        new ScexTypeMember(ownerTpe, s, st,
          context.isAccessible(if (s.hasGetter) s.getterIn(s.owner) else s, pre, superAccess && !implicitlyAdded),
          implicitTree, implicitType)
      }
    }

    /** Create a function application of a given view function to `tree` and typecheck it.
      */
    def viewApply(view: analyzer.SearchResult): Tree = {
      assert(view.tree != EmptyTree)
      analyzer.newTyper(context.makeImplicit(reportAmbiguousErrors = false))
        .typed(Apply(view.tree, List(tree)) setPos tree.pos)
        .onTypeError(EmptyTree)
    }

    for (sym <- ownerTpe.members)
      addTypeMember(sym, pre, EmptyTree, NoType)

    val applicableViews: List[analyzer.SearchResult] =
      if (ownerTpe.isErroneous || ownerTpe <:< NullTpe || ownerTpe <:< NothingTpe) List()
      else new analyzer.ImplicitSearch(
        tree, functionType(List(ownerTpe), AnyClass.tpe), isView = true,
        context0 = context.makeImplicit(reportAmbiguousErrors = false)).allImplicits

    for (view <- applicableViews) {
      val vtree = viewApply(view)
      val vpre = stabilizedType(vtree)
      for (sym <- vtree.tpe.members) {
        addTypeMember(sym, vpre, view.tree, vpre)
      }
    }

    members.allMembers
  }

}
