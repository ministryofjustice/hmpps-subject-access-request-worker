package uk.gov.justice.digital.hmpps.hmppssubjectaccessrequestworker.utils
import com.github.mustachejava.DefaultMustacheFactory
import com.github.mustachejava.Mustache
import com.github.mustachejava.MustacheFactory

class Mustache {

  fun compile(file: String): Int {
    val mf: MustacheFactory = DefaultMustacheFactory()
    val m: Mustache? = mf.compile(file)
    println(m)
    return 0
  }
}