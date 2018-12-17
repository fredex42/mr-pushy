import java.io.File

import com.amazonaws.services.s3.AmazonS3
import org.slf4j.{Logger, LoggerFactory}

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.{Failure, Success, Try}

trait MainUploadFunctions {
  val doUploadLogger:Logger = LoggerFactory.getLogger(getClass)

  def checkDeletable(filePath:String,destBucket:String, fileref:File, uploadResult: UploadResult, uploader:MtUploader, chunkSize:Int, reallyDelete:Boolean)
                    (implicit exec:ExecutionContext, s3Client:AmazonS3)  = {
    EtagCalculator.propertiesForFile(new File(filePath), chunkSize)(exec).map(localFileProperties => {
      doUploadLogger.debug(s"$filePath: etag calculated, checking if deletable")
      FileChecker.canDelete(destBucket, uploadResult.uploadedPath, localFileProperties) match {
        case true =>
          if(reallyDelete) {
            doUploadLogger.info(s"$filePath: will delete")
            Try(fileref.delete()).flatMap({
              case true=>Success("deleted local file")
              case false=>Failure(new RuntimeException("could not delete local file"))
            })
          } else {
            doUploadLogger.info(s"$filePath: can be deleted. Set reallyDelete property to 'true'")
            Success("would be deleted")
          }
        case false =>
          doUploadLogger.warn(s"$filePath: UPLOAD FAILED, removing remote path")
          uploader.delete_failed_upload(filePath) match {
            case Success(u)=>
              doUploadLogger.debug(s"$filePath: remote file deleted")
            case Failure(err)=>
              doUploadLogger.error(s"$filePath: could not delete remote file", err)
          }
          Failure(new RuntimeException("Checksum or size did not match, removing remote file"))
      }
    })
  }

  /**
    * asynchronously perform an upload and verify, deleting the local or remote file as necessary
    * @param uploader
    * @param filePath
    * @param destBucket
    * @param fileref
    * @param chunkSize
    * @param reallyDelete
    * @param uploadExecContext
    * @param s3Client
    * @param exec
    * @return a Tuple of two promises; the first, which contains an [[UploadResult]], resolves once the upload has completed
    *         and the second , which contains a message String, resolves once verification has completed
    */
  def doUpload(uploader:MtUploader, filePath:String, destBucket:String, fileref:File, chunkSize:Int, reallyDelete:Boolean, uploadExecContext:ExecutionContext, genericUploadContext:ExecutionContext)
              (implicit s3Client:AmazonS3, exec:ExecutionContext):(Promise[Try[UploadResult]],Promise[String]) = {
    val uploadCompletionPromise:Promise[Try[UploadResult]] = Promise()
    val verifyCompletionPromise:Promise[String] = Promise()

    val actualUploadFuture = uploader.kickoff_upload(filePath, uploadExecContext).map(uploadResult => {
      doUploadLogger.info(s"$filePath: upload completed successfully (${uploadResult.uploadType.toString}), calculating etag")

      uploadCompletionPromise.complete(Success(Success(uploadResult))) //this lets the next upload start

      checkDeletable(filePath, destBucket,fileref,uploadResult,uploader, chunkSize,reallyDelete).onComplete({
        case Success(Success(msg))=>verifyCompletionPromise.complete(Success(msg))
        case Success(Failure(msg))=>verifyCompletionPromise.complete(Failure(msg))
        case Failure(err)=>verifyCompletionPromise.complete(Failure(err))
      })
    })(genericUploadContext)

    actualUploadFuture.recoverWith({
      case err:Throwable=>
        uploadCompletionPromise.complete(Success(Failure(err)))
        Future(Tuple2(uploadCompletionPromise, verifyCompletionPromise))
    })(genericUploadContext)
    Tuple2(uploadCompletionPromise, verifyCompletionPromise)
  }

}
