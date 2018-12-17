import java.io.File

import org.slf4j.LoggerFactory
import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.model._
import com.amazonaws.{AmazonClientException, AmazonServiceException}

import collection.JavaConverters._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.{Failure, Success, Try}
import scala.concurrent.duration._

class MtUploader (bucketName: String, removePathSegments: Int, chunkSize:Long = 8*1024*1024, maxRetries:Int = 25){
  val logger = LoggerFactory.getLogger(getClass)

  def getUploadPath(str: String):String = {
    str.split("/").drop(removePathSegments).mkString("/")
  }

  def kickoff_single_upload(toUpload:File, uploadPath:String)(implicit client:AmazonS3, exec:ExecutionContext):Future[PutObjectResult] = Future {
    logger.info(s"${toUpload.getCanonicalPath}: Starting single-hit upload")
    val putRequest = new PutObjectRequest(bucketName, uploadPath, toUpload)
    val result=client.putObject(putRequest)
    logger.info(s"${toUpload.getCanonicalPath}: Finished single-hit upload")
    result
  }

  def mt_upload_part(toUpload:File, partNumber:Int, fileOffset:Long, uploadPath:String, uploadId: String, thisChunkSize: Long, retryCount:Int=0)(implicit client:AmazonS3,  exec:ExecutionContext):Future[UploadPartResult] = Future {
      logger.debug(s"${toUpload.getCanonicalPath}: uploading part $partNumber")
      val rq = new UploadPartRequest()
        .withUploadId(uploadId)
        .withBucketName(bucketName)
        .withKey(uploadPath)
        .withFile(toUpload)
        .withFileOffset(fileOffset)
        .withPartNumber(partNumber + 1)
        .withPartSize(thisChunkSize)
      client.uploadPart(rq)
    }.recoverWith({
      case ex:Throwable=>
        logger.warn(s"${toUpload.getCanonicalPath} part $partNumber: Caught exception on retry $retryCount", ex)
        if(retryCount<maxRetries) {
          Thread.sleep(1000)
          mt_upload_part(toUpload, partNumber, fileOffset, uploadPath, uploadId, thisChunkSize, retryCount+1)
        } else {
          throw ex  //this causes the whole upload future to fail and is caught after Future.sequence below
        }
    })

  /**
    * intiates a multipart upload request and kicks off Futures to upload each part
    * @param toUpload File reference to upload
    * @param client AmazonS3 client
    * @return Sequence of Futures of UploadPartReasult, one for each chunk.
    */
  def kickoff_mt_upload(toUpload:File,uploadPath:String, uploadId:String)(implicit client:AmazonS3, exec:ExecutionContext):Future[Seq[UploadPartResult]] = {
    def nextChunkPart(currentChunk:Int, lastChunk:Int, parts:Seq[Future[UploadPartResult]]):Seq[Future[UploadPartResult]] = {
      val updatedParts:Seq[Future[UploadPartResult]] = parts :+ mt_upload_part(toUpload, currentChunk, currentChunk * chunkSize, uploadPath, uploadId, chunkSize)
      if(currentChunk<lastChunk)
        nextChunkPart(currentChunk+1, lastChunk, updatedParts)
      else
        updatedParts
    }

    val chunks = math.ceil(toUpload.length()/chunkSize).toInt
    val finalChunkSize = toUpload.length - (chunks * chunkSize)

    logger.info(s"${toUpload.getCanonicalPath}: uploading in $chunks chunks")
    if(chunks>10000) {
      Future.failed(new RuntimeException(s"$uploadPath: would have more than 10,000 parts ($chunks)"))
    } else {
      val uploadPartsFutures = nextChunkPart(0, chunks - 1, Seq()) :+ mt_upload_part(toUpload, chunks + 1, chunks * chunkSize, uploadPath, uploadId, finalChunkSize)
      Future.sequence(uploadPartsFutures)
    }
  }

  private def internal_do_upload(f:File, uploadPath:String, uploadExecContext:ExecutionContext)(implicit client:AmazonS3, exec:ExecutionContext):Future[UploadResult] = {
    if(f.length()<chunkSize){
      val uploadFuture = kickoff_single_upload(f, uploadPath)(client, uploadExecContext)
      uploadFuture.onComplete({
        case Failure(err)=>
          logger.error(s"Could not upload ${f.getCanonicalPath}", err)
        case Success(result)=>
          logger.info(s"Successfully uploaded ${f.getCanonicalPath}: ${result.getETag}")
      })
      uploadFuture.map(result=>UploadResult(UploadResultType.Single,uploadPath, Some(result),None))
    } else {
      logger.info(s"${f.getCanonicalPath}: Starting multipart upload")
      val mpRequest = new InitiateMultipartUploadRequest(bucketName, uploadPath)
      val mpResponse = client.initiateMultipartUpload(mpRequest)

      val uploadFuture = kickoff_mt_upload(f, uploadPath, mpResponse.getUploadId)(client, uploadExecContext)

      /* these will pick up the default execution context not the special one for uploading */
      val completionFuture= uploadFuture.map(uploadPartSequence=>{
        val partEtags = uploadPartSequence.map(_.getPartETag)

        val completeRq = new CompleteMultipartUploadRequest()
          .withUploadId(mpResponse.getUploadId)
          .withBucketName(bucketName)
          .withKey(uploadPath)
          .withPartETags(partEtags.asJava)
        logger.info(s"${f.getCanonicalPath}: Finished multipart upload")
        client.completeMultipartUpload(completeRq)
      })

      completionFuture.onComplete({
        case Failure(err)=>
          logger.error(s"$uploadPath: Unable to upload, cancelling multipart upload", err)
          try {
            val rq = new AbortMultipartUploadRequest(bucketName, uploadPath, mpResponse.getUploadId)
            client.abortMultipartUpload(rq)
          } catch {
            case ex:Throwable=>
              logger.error(s"$uploadPath: Could not abort multipart upload", err)
          }
        case Success(seq)=>
          logger.debug(s"$uploadPath: parts were successfully")
      })

      val finalResult = completionFuture.map(result=>{
        logger.debug("upload completed, returning information")
        UploadResult(UploadResultType.Multipart,uploadPath, None,Some(result))
      })

      finalResult
    }

  }

  def kickoff_upload(filePath: String, uploadExecContext: ExecutionContext)(implicit client:AmazonS3,  exec:ExecutionContext):Future[UploadResult] = {
    val f:File = new File(filePath)
    val uploadPath = getUploadPath(f.getAbsolutePath)
    logger.debug(s"$filePath: kickoff to $uploadPath")
    try {
      client.getObjectMetadata(bucketName, uploadPath)
      //if this doesn't throw an exception, then file already exists. Assume a previous upload; this will get validated elsewhere
      Future(UploadResult(UploadResultType.AlreadyThere, uploadPath, None, None))
    } catch {
      case ex:AmazonS3Exception=>
        if(ex.getMessage.contains("404 Not Found")) {
          logger.debug(s"s3://$bucketName/$uploadPath does not currently exist, proceeding to upload")
          internal_do_upload(f, uploadPath, uploadExecContext)
        } else {
          logger.error(s"S3 error ${ex.getErrorCode}: ${ex.getMessage} ${ex.getAdditionalDetails} ${ex.getErrorResponseXml}")
          throw ex
        }
    }
  }

  def delete_failed_upload(filePath:String)(implicit  client:AmazonS3, exec: ExecutionContext):Try[Unit] = {
    Try { client.deleteObject(bucketName, getUploadPath(filePath)) }
  }
}
