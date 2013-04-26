import java.{util => ju, lang => jl}
import scala.collection.mutable
import scala.collection.JavaConverters._
import scala.reflect.runtime.{universe => ru}

object TypeConvertersTest {

  import com.avsystem.scex.TypeConverters._

  def main(args: Array[String]) {
    val clazz = classOf[TypedLol[T]#Dafuq[F] forSome {type T; type F}]

    println(javaTypeAsScalaType(clazz))
    println(boundedTypeVariables(classToExistential(clazz).typeVars))
  }
}
