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

  fun execute(dataObject: Any): Int {

    val mf = DefaultMustacheFactory()
    val mustache = mf.compile(mf.getReader("template.mustache"), "hello")
    mustache.execute(PrintWriter("test.mustache"), dataObject).flush()
    return 0
  }

}

data class Item(val title: String, val createdOn: String, val text: String)
data class Keyworker(
  val offenderKeyworkerId: String?,
  val offenderNo: String?,
  val staffId: String?,
  val assignedDateTime: String?,
  val active: String?,
  val allocationReason: String?,
  val allocationType: String?,
  val userId: String?,
  val prisonId: String?,
  val expiryDateTime: String?,
  val deallocationReason: String?,
  val creationDateTime: String?,
  val createUserId: String?,
  val modifyDateTime: String?,
  val modifyUserId: String?,)