import java.io.File
import java.util.concurrent.Executors

import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.model.CompleteMultipartUploadResult

import scala.concurrent._
import org.slf4j.Logger
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success, Try}
import scala.concurrent.duration._

class TestMainUploadFunctions extends Specification with Mockito {
  "MainUploadFunctions.doUpload" should {
    "fail the first future if upload fails, and not call checkDeletable" in {
      val mockLogger = mock[Logger]
      val mockUploader = mock[MtUploader]
      val mockCheckDeletable = mock[Function0[Unit]]
      implicit val mockS3Client:AmazonS3 = mock[AmazonS3]
      val mockFileRef = mock[File]

      val uploadExecContext = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(2))

      mockUploader.kickoff_upload(anyString,any[ExecutionContext])(any[AmazonS3], any[ExecutionContext]) returns Future.failed(new RuntimeException("my hovercraft is full of eels"))

      val testClass = new MainUploadFunctions {
        override val logger: Logger = mockLogger

        override def checkDeletable(filePath:String,destBucket:String, fileref:File, uploadResult: UploadResult, uploader:MtUploader, chunkSize:Int, reallyDelete:Boolean)
                                   (implicit exec:ExecutionContext, s3Client:AmazonS3):Future[Try[String]] = {
          mockCheckDeletable()
          Future(Success("test"))(uploadExecContext)
        }
      }

      val results = testClass.doUpload(mockUploader, "/path/to/testfile", "destbucket", mockFileRef, 20, true, uploadExecContext)
      there was no(mockCheckDeletable).apply()

      val finalresult = Await.result(results._1.future, 1 second)
      finalresult must beFailedTry
      finalresult.failed.get must beAnInstanceOf[RuntimeException]
      finalresult.failed.get.getMessage mustEqual "my hovercraft is full of eels"
    }

    "complete the first future when upload completes regardless of whether verification succeeds" in {
      val mockLogger = mock[Logger]
      val mockUploader = mock[MtUploader]
      val mockCheckDeletable = mock[Function3[String, String, UploadResult, Unit]]
      implicit val mockS3Client:AmazonS3 = mock[AmazonS3]
      val mockFileRef = mock[File]
      val mockedS3Result = mock[CompleteMultipartUploadResult]
      val uploadExecContext = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(2))

      val mockResult = UploadResult(UploadResultType.Multipart,"path/to/testfile", None,Some(mockedS3Result))
      mockUploader.kickoff_upload(anyString,any[ExecutionContext])(any[AmazonS3], any[ExecutionContext]) returns Future(mockResult)

      val testClass = new MainUploadFunctions {
        override val logger: Logger = mockLogger

        override def checkDeletable(filePath:String,destBucket:String, fileref:File, uploadResult: UploadResult, uploader:MtUploader, chunkSize:Int, reallyDelete:Boolean)
                                   (implicit exec:ExecutionContext, s3Client:AmazonS3):Future[Try[String]] = {
          mockCheckDeletable(filePath, destBucket, uploadResult)
          Future(Failure(new RuntimeException("error")))(uploadExecContext)
        }
      }

      val results = testClass.doUpload(mockUploader, "/path/to/testfile", "destbucket", mockFileRef, 20, true, uploadExecContext)

      val finalresult = Await.result(results._1.future, 1 second)
      finalresult must beSuccessfulTry

      Await.result(results._2.future, 1 second) must throwA[RuntimeException]("error")
      there was one(mockCheckDeletable).apply("/path/to/testfile","destbucket", mockResult)
    }

    "complete the both futures when verification succeeds" in {
      val mockLogger = mock[Logger]
      val mockUploader = mock[MtUploader]
      val mockCheckDeletable = mock[Function3[String, String, UploadResult, Unit]]
      implicit val mockS3Client:AmazonS3 = mock[AmazonS3]
      val mockFileRef = mock[File]
      val mockedS3Result = mock[CompleteMultipartUploadResult]
      val uploadExecContext = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(2))

      val mockResult = UploadResult(UploadResultType.Multipart,"path/to/testfile", None,Some(mockedS3Result))
      mockUploader.kickoff_upload(anyString,any[ExecutionContext])(any[AmazonS3], any[ExecutionContext]) returns Future(mockResult)

      val testClass = new MainUploadFunctions {
        override val logger: Logger = mockLogger

        override def checkDeletable(filePath:String,destBucket:String, fileref:File, uploadResult: UploadResult, uploader:MtUploader, chunkSize:Int, reallyDelete:Boolean)
                                   (implicit exec:ExecutionContext, s3Client:AmazonS3):Future[Try[String]] = {
          mockCheckDeletable(filePath, destBucket, uploadResult)
          Future(Success("it worked"))(uploadExecContext)
        }
      }

      val results = testClass.doUpload(mockUploader, "/path/to/testfile", "destbucket", mockFileRef, 20, true, uploadExecContext)

      val finalresult = Await.result(results._1.future, 1 second)
      finalresult must beSuccessfulTry

      val verifyResult = Await.result(results._2.future, 1 second)
      verifyResult mustEqual "it worked"
      there was one(mockCheckDeletable).apply("/path/to/testfile","destbucket", mockResult)
    }
  }
}
