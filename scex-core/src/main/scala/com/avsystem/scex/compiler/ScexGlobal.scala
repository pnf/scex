package com.avsystem.scex
package compiler

import java.{lang => jl, util => ju}

import com.avsystem.scex.util.MacroUtils

import scala.collection.mutable
import scala.reflect.internal.util._
import scala.reflect.io.AbstractFile
import scala.tools.nsc.Global
import scala.tools.nsc.plugins.Plugin

/**
 * Created: 01-04-2014
 * Author: ghik
 */
trait ScexGlobal extends Global with MacroUtils with SymbolErasures {
  val universe: this.type = this

  def loadAdditionalPlugins(): List[Plugin] = Nil

  def parseExpression(code: String, template: Boolean) = {
    val (wrappedCode, offset) = CodeGeneration.wrapForParsing(code, template)
    val sourceFile = new BatchSourceFile("(for_parsing)", wrappedCode)
    val unit = new CompilationUnit(sourceFile)
    val PackageDef(_, List(ModuleDef(_, _, Template(_, _, List(_, expressionTree))))) = new syntaxAnalyzer.UnitParser(unit).parse()
    moveTree(expressionTree, -offset)
  }

  def movePosition(pos: Position, offset: Int) = pos match {
    case tp: TransparentPosition => new TransparentPosition(tp.source, tp.start + offset, tp.point + offset, tp.end + offset)
    case rp: RangePosition => new RangePosition(rp.source, rp.start + offset, rp.point + offset, rp.end + offset)
    case op: OffsetPosition => new OffsetPosition(op.source, op.point + offset)
    case _ => pos
  }

  def moveTree(tree: Tree, offset: Int) = {
    tree.foreach { t =>
      t.setPos(movePosition(t.pos, offset))
    }
    tree
  }

  /**
   * Locator with slightly modified inclusion check.
   *
   * @param pos
   */
  class Locator(pos: Position) extends Traverser {
    var last: Tree = _

    def locateIn(root: Tree): Tree = {
      this.last = EmptyTree
      traverse(root)
      this.last
    }

    override def traverse(t: Tree) {
      t match {
        case tt: TypeTree if tt.original != null && includes(tt.pos, tt.original.pos) =>
          traverse(tt.original)
        case _ =>
          if (includes(t.pos, pos)) {
            if (!t.pos.isTransparent) last = t
            super.traverse(t)
          } else t match {
            case mdef: MemberDef =>
              traverseTrees(mdef.mods.annotations)
            case _ =>
          }
      }
    }

    private def includes(pos1: Position, pos2: Position) =
      (pos1 includes pos2) && pos1.end > pos2.start
  }

  override protected def loadRoughPluginsList() =
    loadAdditionalPlugins() ::: super.loadRoughPluginsList()

  // toplevel symbol dropping is implemented based on how it's done in the Scala Presentation Compiler
  // (which happens e.g. when a source file is deleted)
  private val toplevelSymbolsMap = new mutable.WeakHashMap[AbstractFile, mutable.Set[Symbol]]

  override def registerTopLevelSym(sym: Symbol): Unit = {
    toplevelSymbolsMap.getOrElseUpdate(sym.sourceFile, new mutable.HashSet) += sym
  }

  def forgetSymbolsFromSource(file: AbstractFile) = {
    val symbols = toplevelSymbolsMap.get(file).map(_.toSet).getOrElse(Set.empty)
    symbols.foreach { s =>
      //like in: scala.tools.nsc.interactive.Global.filesDeleted
      s.owner.info.decls unlink s
    }
    toplevelSymbolsMap.remove(file)
  }
}
