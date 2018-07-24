import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.model.ObjectMetadata
import org.junit.runner.RunWith
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner
import org.specs2.mock.Mockito
import org.mockito.Mockito.mock
import org.mockito.Mockito.when
import org.mockito.Matchers._

import scala.concurrent.Await
import scala.concurrent.duration._

@RunWith(classOf[JUnitRunner])
class FileCheckerSpec extends Specification with Mockito {
  "FileChecker.canDelete" should {
    "call out to S3 for remote metadata and return true if both local and remote checksum and size match" in {
      implicit val mockedS3Client = mock[AmazonS3]
      val fakeMeta = mock[ObjectMetadata]
      when(fakeMeta.getETag()).thenReturn("da8186debb05658c72e0b58cfddf14ad")
      when(fakeMeta.getContentLength).thenReturn(1234567)

      when(mockedS3Client.getObjectMetadata(anyString,anyString)).thenReturn(fakeMeta)

      val fakeLocalData = LocalFileProperties("da8186debb05658c72e0b58cfddf14ad", 1234567)
      Await.result(FileChecker.canDelete("test-bucket","/path/to/test/key", fakeLocalData), 1 seconds) shouldEqual true
      there was one(mockedS3Client.getObjectMetadata("test-bucket","/path/to/test/key"))
    }

    "call out to S3 for remote metadata and return false if checksums fail but sizes match" in {
      implicit val mockedS3Client = mock[AmazonS3]
      val fakeMeta = mock[ObjectMetadata]
      when(fakeMeta.getETag()).thenReturn("da8186debb05658c72e0b58cfddf14ab")
      when(fakeMeta.getContentLength).thenReturn(1234567)

      when(mockedS3Client.getObjectMetadata(anyString,anyString)).thenReturn(fakeMeta)

      val fakeLocalData = LocalFileProperties("da8186debb05658c72e0b58cfddf14ad", 1234567)
      Await.result(FileChecker.canDelete("test-bucket","/path/to/test/key", fakeLocalData), 1 seconds) shouldEqual false
      there was one(mockedS3Client.getObjectMetadata("test-bucket","/path/to/test/key"))
    }

    "call out to S3 for remote metadata and return false if sizes fail but checksums match" in {
      implicit val mockedS3Client = mock[AmazonS3]
      val fakeMeta = mock[ObjectMetadata]
      when(fakeMeta.getETag()).thenReturn("da8186debb05658c72e0b58cfddf14ad")
      when(fakeMeta.getContentLength).thenReturn(2222222)

      when(mockedS3Client.getObjectMetadata(anyString,anyString)).thenReturn(fakeMeta)

      val fakeLocalData = LocalFileProperties("da8186debb05658c72e0b58cfddf14ad", 1234567)
      Await.result(FileChecker.canDelete("test-bucket","/path/to/test/key", fakeLocalData), 1 seconds) shouldEqual false
      there was one(mockedS3Client.getObjectMetadata("test-bucket","/path/to/test/key"))
    }
  }
}
