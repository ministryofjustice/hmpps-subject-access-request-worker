package uk.gov.justice.digital.hmpps.hmppssubjectaccessrequestworker.utils
import com.github.mustachejava.DefaultMustacheFactory
import com.github.mustachejava.Mustache
import com.github.mustachejava.MustacheFactory
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.PrintWriter

class Mustache {

  fun compile(file: String): Int {
    val mf: MustacheFactory = DefaultMustacheFactory()
    val m: Mustache? = mf.compile(file)
    println(m)
    return 0
  }

  fun execute(): Int {
    val item = JSONArray(File("input1.json").readText(Charsets.UTF_8)).toList()
    val mf = DefaultMustacheFactory()
    val mustache = mf.compile(mf.getReader("template1.mustache"), "hello")
    mustache.execute(PrintWriter("output1.html"), item).flush()
    return 0
  }

}

data class Item(val title: String, val createdOn: String, val text: String)