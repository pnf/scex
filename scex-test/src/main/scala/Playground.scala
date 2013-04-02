import tools.nsc.Settings
import tools.nsc.interpreter.IMain

import scala.reflect.runtime.{universe => ru}
import java.{util => ju, lang => jl}

/**
 * Created with IntelliJ IDEA.
 * User: ghik
 * Date: 25.01.13
 * Time: 20:40
 */
object Playground {
  def main(args: Array[String]) {
    val settings = new Settings
    settings.usejavacp.value = true

    val repl = new IMain(settings)

    val codeTemplate = """
                         |import com.avsystem.scex._
                         |import com.avsystem.ump.core.db.entities.Device
                         |
                         |val expr = { __ctx: Device =>
                         |  import __ctx._
                         |  InvocationValidators.validate({%s})
                         |}
                       """.stripMargin

    val myexpr =
      """
        |__ctx.oui.asInstanceOf[java.util.List[String]].size()
      """.stripMargin

    repl interpret codeTemplate.format(myexpr)
  }

  private def generateAdapter(clazz: Class[_]) = {
    val classSymbol = ru.runtimeMirror(clazz.getClassLoader).classSymbol(clazz)
    val tpe = classSymbol.toType

    val keywords = List("class")

    val adapterTemplate = """
                            |implicit class %s_Adapter(val wrapped: %s) extends AnyVal {
                            |%s
                            |}
                          """.stripMargin

    val getterPattern = "get([A-Z][a-z0-9]*)+"
    def propNameFromGetter(getter: String) = getter(3).toLower + getter.substring(4)

    val propsWithGetters = tpe.members.withFilter {
      member =>
        val name = member.name.decoded
        member.isMethod &&
          member.isPublic &&
          member.asMethod.paramss == List(List()) &&
          name.matches(getterPattern) &&
          !keywords.contains(propNameFromGetter(name))
    } map {
      member =>
        val name = member.name.decoded
        (propNameFromGetter(name), name)
    }

    if (propsWithGetters.nonEmpty) {
      val sb = new StringBuilder
      propsWithGetters foreach {
        case (prop, getter) =>
          sb ++= s"    def $prop = wrapped.$getter\n"
      }
      val propDefs = sb.mkString

      val typeName = tpe.erasure.toString
      val wrapperClassName = classSymbol.fullName.replaceAll("\\.", "_")

      Some(adapterTemplate.format(wrapperClassName, typeName, propDefs))
    } else {
      None
    }

  }
}

object Stuff {
  def main(args: Array[String]) {
    def on[T](fun: (T => Any)*) = fun

    val f = on[String](_.replaceFirst _)
    println(f.getClass)
  }
}

class Target {
  var counter = 0

  def getSomeValue(x: Double, y: Double): Double = {
    counter += 1
    x + y * y
  }

  def doSomeWork(x: Double): Double = math.sin(x)

  def getStuff() = 5

  def someValue: Double = 7.234234
}