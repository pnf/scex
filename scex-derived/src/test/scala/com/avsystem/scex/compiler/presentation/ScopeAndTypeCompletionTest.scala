package com.avsystem.scex.compiler.presentation

import com.avsystem.scex.compiler.presentation.ScexPresentationCompiler.{MemberFlags, Member, Param}
import com.avsystem.scex.compiler.presentation.ScopeAndTypeCompletionTest.Root
import com.avsystem.scex.compiler.{CompilationTest, ScexFunSuite}
import com.avsystem.scex.presentation.Attributes
import com.avsystem.scex.presentation.annotation.{Documentation, ParameterNames}
import com.avsystem.scex.util.SimpleContext

/**
 * Author: ghik
 * Created: 11/18/14.
 */
class ScopeAndTypeCompletionTest extends ScexFunSuite with CompilationTest with CompletionTest {

  import com.avsystem.scex.util.CommonUtils._

  val acl = {
    import com.avsystem.scex.validation.SymbolValidator._
    allow {
      on { s: String =>
        s.charAt _
        s.toInt
      }
      on { r: Root =>
        r.method _
      }
    }
  }

  val attrs = {
    import com.avsystem.scex.presentation.SymbolAttributes._
    attributes {
      on { s: String =>
        s.charAt _ --> Attributes(documentation = "doc of charAt", paramNames = List("index"))
      }
    }
  }

  val utils =
    """
      |import com.avsystem.scex.compiler.presentation._
      |import com.avsystem.scex.presentation.annotation._
      |
      |implicit class rootOps(root: ScopeAndTypeCompletionTest.Root) {
      |  @Documentation("implicit method doc")
      |  def implicitMethod: Int = 42
      |}
      |
      |@Documentation("util stuff")
      |val utilStuff = 5
      |
    """.stripMargin

  val profile = createProfile(acl, attrs, utils = utils)

  test("simple type completion test") {
    val completer = compiler.getCompleter[SimpleContext[Unit], Any](profile, template = false)
    val completion = completer.getTypeCompletion("\"\"", 1).passTo(c => c.copy(members = c.members.sortBy(_.name)))

    assert(completion.members.map(asPartial) === Vector(
      PartialMember("charAt", scexType[Char], List(List(Param("index", scexType[Int]))), doc = "doc of charAt"),
      PartialMember("toInt", scexType[Int])
    ))
  }

  test("attribute annotations test") {
    val completer = compiler.getCompleter[SimpleContext[Root], Any](profile, template = false)
    val completion = completer.getTypeCompletion("_root", 4).passTo(c => c.copy(members = c.members.sortBy(_.name)))

    assert(completion.members.map(asPartial) === Vector(
      PartialMember("implicitMethod", scexType[Int], doc = "implicit method doc"),
      PartialMember("method", scexType[Any], List(List(
        Param("annotArg", scexType[Any]),
        Param("moar", scexType[Any])
      )), doc = "handles stuff")
    ))
  }

  test("simple scope completion test") {
    val completer = compiler.getCompleter[SimpleContext[Root], Any](profile, template = false)
    val completion = completer.getScopeCompletion.passTo(c => c.copy(members = c.members.sortBy(_.name)))

    completion.members.foreach(println)

    assert(completion.members.map(asPartial) === Vector(
      PartialMember("method", scexType[Any], List(List(
        Param("annotArg", scexType[Any]),
        Param("moar", scexType[Any])
      )), doc = "handles stuff"),
      PartialMember("utilStuff", scexType[Int], doc = "util stuff")
    ))
  }

}

object ScopeAndTypeCompletionTest {

  trait Root {
    @ParameterNames(Array("annotArg"))
    @Documentation("handles stuff")
    def method(arg: Any, moar: Any): Any
  }

}
