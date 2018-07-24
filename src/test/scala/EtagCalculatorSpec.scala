import java.io.File

import org.junit.runner.RunWith
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner
import org.specs2.mock.Mockito
import org.mockito.Mockito.mock
import org.mockito.Mockito.when
import org.mockito.Matchers._

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

@RunWith(classOf[JUnitRunner])
class EtagCalculatorSpec extends Specification {
  "EtagCalculator.eTagForFile" should {
    "calculate a single md5 hash for a file if it is larger than the given chunk size" in {
      val f = new File("./src/test/resources/sample2.lst")
      val result = EtagCalculator.eTagForFile(f,40960)
      result shouldEqual "89684d80211bdff0b4db7db7be362e9c"
    }

    "calculate a compound md5 hash for a file if it is larger than the given chunk size" in {
      val f = new File("./src/test/resources/sample2.lst")
      val result = EtagCalculator.eTagForFile(f,6)
      result shouldEqual "5e1534261e035cd0325d0888626103c4-21"
    }
  }

  "EtagCalculator.propertiesForFile" should {
    "asynchronously return a LocalProperties object for the given file" in {
      val f = new File("./src/test/resources/sample2.lst")
      val result = Await.result(EtagCalculator.propertiesForFile(f, 6), 5 seconds)
      result shouldEqual LocalFileProperties("5e1534261e035cd0325d0888626103c4-21",f.length)
    }
  }
}
