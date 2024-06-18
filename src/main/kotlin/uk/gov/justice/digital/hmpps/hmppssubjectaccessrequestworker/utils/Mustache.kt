package uk.gov.justice.digital.hmpps.hmppssubjectaccessrequestworker.utils
import com.github.mustachejava.DefaultMustacheFactory
import com.github.mustachejava.Mustache
import com.github.mustachejava.MustacheFactory

class Mustache {
  var mf: MustacheFactory = DefaultMustacheFactory()
  var m: Mustache? = mf.compile("template.mustache")
  fun compile(subject: String): Int {
    println(m)
    return 0
  }
}