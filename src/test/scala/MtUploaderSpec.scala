import com.amazonaws.services.s3.model.StorageClass
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification

class MtUploaderSpec extends Specification with Mockito {
  "MtUploader.getUploadPath" should {
    "return a path with the right number of components stripped" in {
      val toTest = new MtUploader("testbucket",removePathSegments = 2,storageClass=StorageClass.StandardInfrequentAccess)

      val result = toTest.getUploadPath("/path/to/some/files",None)
      result mustEqual "to/some/files"
    }

    "add in a path prefix" in {
      val toTest = new MtUploader("testbucket",removePathSegments = 2,storageClass=StorageClass.StandardInfrequentAccess)

      val result = toTest.getUploadPath("/path/to/some/files",Option("remote"))
      result mustEqual "remote/to/some/files"
    }
  }
}
