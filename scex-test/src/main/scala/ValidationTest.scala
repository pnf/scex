import java.util.Collections
import java.{lang => jl, util => ju}

import com.avsystem.scex._
import com.avsystem.scex.compiler.ScexSettings
import com.avsystem.scex.japi.DefaultJavaScexCompiler
import com.avsystem.scex.presentation.SymbolAttributes
import com.avsystem.scex.util.PredefinedAccessSpecs
import com.avsystem.scex.validation._

import scala.language.{dynamics, existentials}
import scala.reflect.macros.Universe
import scala.runtime.RichInt


object ValidationTest {

  object Foo {

    object Bar {
      val c = 5
    }

  }

  class A[T] {
    def costam(buu: T): T = buu

    def hoho[U <: List[String]](cos: U) = ???

    def multiParens(a: Int)(b: String, c: Float)(implicit d: T): Unit = ???

    var a: T = _

    def b(): T = ???
  }

  class B extends A[Int] {
    override def costam(buu: Int) = buu * 2
  }

  object Dyn extends Dynamic {
    def selectDynamic(attr: String) = attr
  }

  def main(args: Array[String]) {

    import com.avsystem.scex.validation.SymbolValidator._

    val memberAccessSpecs = allow {
      StringContext.apply _
      ValidationTest.Foo.Bar.c
      String.CASE_INSENSITIVE_ORDER
      Some.apply _
      allStatic[String].members
      Collections.emptyList
      new B
      new JavaLol
      None
      Tuple2.apply _

      on {
        anyRef: AnyRef =>
          anyRef == (_: AnyRef)
          anyRef != (_: AnyRef)
      }

      on {
        tl: TypedLol[_] =>
          tl.toString
      }

      on {
        d: (TypedLol[T]#Dafuq[_] forSome {type T}) =>
          d.getStuff
      }

      on {
        s: String =>
          s.length
          s.concat _
          s.matches _
          s.reverse
          s.compare(_: String)
      }

      on {
        sc: StringContext =>
          sc.s _
      }

      on {
        al: ju.ArrayList[_] =>
          new ju.ArrayList(_: ju.Collection[_])
          al.all.members
      }

      on {
        any: Any =>
          any + (_: String)
          any -> (_: Any)
          any == (_: Any)
          any != (_: Any)
      }

      on {
        a: A[_] =>
          a.costam _
          a.hoho _
          a.b()
          a.multiParens(_: Int)(_: String, _: Float)(_: Nothing)
          a.getClass
          a.a_= _
      }

      on {
        i: Int =>
          i.implicitlyAs[RichInt].all.membersNamed.to
          i.all.constructors
          i.all.membersNamed("+")
      }

      on {
        jl: JavaLol =>
          jl.fuu
          jl.isFoo
      }

      Dyn.selectDynamic _

    } ++ deny {

      on {
        any: Any =>
          any.equals _
          any.hashCode
          any.##
          any.getClass
          any.asInstanceOf
          any.isInstanceOf
      }

      on {
        anyRef: AnyRef =>
          anyRef.eq _
          anyRef.synchronized _
      }

    }

    //memberAccessSpecs foreach println

    val syntaxValidator = new SyntaxValidator {
      def validateSyntax(u: Universe)(tree: u.Tree): (Boolean, List[u.Tree]) = {
        import u._

        tree match {
          case _: Block | _: Select | _: Apply | _: TypeApply | _: Ident |
               _: If | _: Literal | _: New | _: This | _: Typed | _: TypTree => (true, tree.children)
          case _ => (false, tree.children)
        }
      }
    }

    val symbolValidator = SymbolValidator(PredefinedAccessSpecs.basicOperations)
    val symbolAttributes = SymbolAttributes(Nil)

    val utils = NamedSource("test", "val lol = \"dafuq\"; def immaUtil = \"util, lol\"")
    val profile = new ExpressionProfile("test", syntaxValidator, symbolValidator, symbolAttributes, "", utils)
    val compiler = new DefaultJavaScexCompiler(new ScexSettings)

    val myexpr = "(null: A[_])"

    val expr = """ Some((3, "50")) """

    class TL extends TypedLol[TL]

    val typedLol = new TL
    val dafuq = new typedLol.Dafuq[ju.ArrayList[CharSequence]]

    type Typ = TypedLol[T]#Dafuq[F] forSome {type T <: TypedLol[T]; type F}

    //compiler.getCompiledExpression(profile, "ValidationTest.Dyn.costam", classOf[Object], classOf[String])

    val ic = compiler.getCompleter[ExpressionContext[Unit, Unit], Object](profile)
    val completion = ic.getTypeCompletion("${None}", 1)
    completion.members foreach println

  }

}
