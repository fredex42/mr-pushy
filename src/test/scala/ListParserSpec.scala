import org.junit.runner.RunWith
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner
import org.specs2.mock.Mockito
import org.mockito.Mockito.mock
import org.mockito.Mockito.when
import org.mockito.Matchers._

@RunWith(classOf[JUnitRunner])
class ListParserSpec extends Specification with Mockito {
  "ListParser" should {
    "call the passed block once for each project, filepath pair" in {
      val lp = new ListParser("src/test/resources/sample1.lst",noProjects=false)
      val mockFunction = mock[Function2[Option[String],String,Boolean]]
      when(mockFunction.apply(any[Option[String]],anyString)).thenReturn(false)

      lp.foreach(mockFunction)

      there was one(mockFunction).apply(Some("1234"),"/path/to/firstasset.mxf") andThen one(mockFunction).apply(Some("1234"),"/path/to/secondasset.mxf") andThen one(mockFunction).apply(Some("4567"),"/path/to/another/setofassets/thing.mov")
    }

    "skip over invalid lines" in {
      val lp = new ListParser("src/test/resources/sample2.lst",noProjects=false)
      val mockFunction = mock[Function2[Option[String],String,Boolean]]

      when(mockFunction.apply(any[Option[String]],anyString)).thenReturn(false)
      lp.foreach(mockFunction)

      there was one(mockFunction).apply(Some("1234"),"/path/to/firstasset.mxf") andThen one(mockFunction).apply(Some("1234"),"/path/to/secondasset.mxf") andThen one(mockFunction).apply(Some("4567"),"/path/to/another/setofassets/thing.mov")

    }

    "stop processing if the block returns true" in {
      val lp = new ListParser("src/test/resources/sample1.lst",noProjects=false)
      val mockFunction = mock[Function2[Option[String],String,Boolean]]
      when(mockFunction.apply(any[Option[String]],anyString)).thenReturn(true)

      lp.foreach(mockFunction)

      there was one(mockFunction).apply(Some("1234"),"/path/to/firstasset.mxf")

    }

    "call the passed block once for each filepath when the noProjects flag is set" in {
      val lp = new ListParser("src/test/resources/sample3.lst",noProjects=true)
      val mockFunction = mock[Function2[Option[String],String,Boolean]]
      when(mockFunction.apply(any[Option[String]],anyString)).thenReturn(false)

      lp.foreach(mockFunction)

      there was one(mockFunction).apply(None,"/path/to/a/random/file.txt") andThen one(mockFunction).apply(None,"/path/to/another/random/file.mxf") andThen one(mockFunction).apply(None,"/path/to/yet/another/random/file.aiff")
    }

    "when the noProjects flag is set, skip over lines with anything other than a / at the start" in {
      val lp = new ListParser("src/test/resources/sample3.lst",noProjects=true)
      val mockFunction = mock[Function2[Option[String],String,Boolean]]
      when(mockFunction.apply(any[Option[String]],anyString)).thenReturn(false)

      lp.foreach(mockFunction)

      there was one(mockFunction).apply(None,"/path/to/a/random/file.txt") andThen one(mockFunction).apply(None,"/path/to/yet/another/random/file.aiff")
    }
  }
}
