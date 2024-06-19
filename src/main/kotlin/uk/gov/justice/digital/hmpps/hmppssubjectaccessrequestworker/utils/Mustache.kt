package uk.gov.justice.digital.hmpps.hmppssubjectaccessrequestworker.utils
import com.github.mustachejava.DefaultMustacheFactory
import com.github.mustachejava.Mustache
import com.github.mustachejava.MustacheFactory
import java.io.PrintWriter

class Mustache {

  fun compile(file: String): Int {
    val mf: MustacheFactory = DefaultMustacheFactory()
    val m: Mustache? = mf.compile(file)
    println(m)
    return 0
  }

  fun execute(): Int {
    val item = Item("Item 1", "02/02/02", "This is the first item.")
    val mf = DefaultMustacheFactory()
    val mustache = mf.compile(mf.getReader("template.mustache"), "hello")
    mustache.execute(PrintWriter("test.mustache"), item).flush()
    return 0
  }

}

data class Item(val title: String, val createdOn: String, val text: String)