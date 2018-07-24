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
      val lp = new ListParser("src/test/resources/sample1.lst")
      val mockFunction = mock[Function2[String,String,Boolean]]
      when(mockFunction.apply(anyString,anyString)).thenReturn(false)

      lp.foreach(mockFunction)

      there was one(mockFunction).apply("1234","/path/to/firstasset.mxf") andThen one(mockFunction).apply("1234","/path/to/secondasset.mxf") andThen one(mockFunction).apply("4567","/path/to/another/setofassets/thing.mov")
    }

    "skip over invalid lines" in {
      val lp = new ListParser("src/test/resources/sample2.lst")
      val mockFunction = mock[Function2[String,String,Boolean]]

      when(mockFunction.apply(anyString,anyString)).thenReturn(false)
      lp.foreach(mockFunction)

      there was one(mockFunction).apply("1234","/path/to/firstasset.mxf") andThen one(mockFunction).apply("1234","/path/to/secondasset.mxf") andThen one(mockFunction).apply("4567","/path/to/another/setofassets/thing.mov")

    }

    "stop processing if the block returns true" in {
      val lp = new ListParser("src/test/resources/sample1.lst")
      val mockFunction = mock[Function2[String,String,Boolean]]
      when(mockFunction.apply(anyString,anyString)).thenReturn(true)

      lp.foreach(mockFunction)

      there was one(mockFunction).apply("1234","/path/to/firstasset.mxf")

    }
  }
}
