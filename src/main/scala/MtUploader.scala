import java.io.File
import org.slf4j.LoggerFactory
import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.model._
import com.amazonaws.{AmazonClientException, AmazonServiceException}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success}

class MtUploader (bucketName: String, removePathSegments: Int){
  val CHUNK_SIZE:Long = 8*1024*1024
  val logger = LoggerFactory.getLogger(getClass)

  def getUploadPath(str: String):String = {
    str.split("/").drop(removePathSegments).mkString("/")
  }

  def kickoff_single_upload(toUpload:File)(implicit client:AmazonS3):Future[PutObjectResult] = Future {
    val putRequest = new PutObjectRequest(bucketName, getUploadPath(toUpload.getAbsolutePath), toUpload)
    client.putObject(putRequest)
  }

  def mt_upload_part(toUpload:File, partNumber:Int, fileOffset:Long, uploadId: String)(implicit client:AmazonS3):Future[UploadPartResult] = Future {
    val rq = new UploadPartRequest()
      .withUploadId(uploadId)
      .withFile(toUpload)
      .withFileOffset(fileOffset)
      .withPartNumber(partNumber)
      .withPartSize(CHUNK_SIZE)
    client.uploadPart(rq)
  }

  /**
    * intiates a multipart upload request and kicks off Futures to upload each part
    * @param toUpload File reference to upload
    * @param client AmazonS3 client
    * @return Sequence of Futures of UploadPartReasult, one for each chunk.
    */
  def kickoff_mt_upload(toUpload:File)(implicit client:AmazonS3):Future[CompleteMultipartUploadResult] = {
    val mpRequest = new InitiateMultipartUploadRequest(bucketName, getUploadPath(toUpload.getAbsolutePath))
    val mpResponse = client.initiateMultipartUpload(mpRequest)

    val chunks = math.ceil(toUpload.length()/CHUNK_SIZE).toInt
    def nextChunkPart(currentChunk:Int, lastChunk:Int, parts:Seq[Future[UploadPartResult]]):Seq[Future[UploadPartResult]] = {
      val updatedParts:Seq[Future[UploadPartResult]] = parts :+ mt_upload_part(toUpload, currentChunk, currentChunk * CHUNK_SIZE, mpResponse.getUploadId)
      if(currentChunk<lastChunk)
        nextChunkPart(currentChunk+1, lastChunk, updatedParts)
      else
        updatedParts
    }
    val uploadPartsFutures = nextChunkPart(0,chunks-1,Seq())

    Future.sequence(uploadPartsFutures).map(uploadPartSequence=>{
      val completeRq = new CompleteMultipartUploadRequest().withUploadId(mpResponse.getUploadId)
      client.completeMultipartUpload(completeRq)
    })
  }

  def kickoff_upload(filePath: String)(implicit client:AmazonS3):Unit = {
    val f:File = new File(filePath)
    if(f.length()<CHUNK_SIZE){
      kickoff_single_upload(f).onComplete({
        case Failure(err)=>
          logger.error(s"Could not upload $filePath", err)
        case Success(result)=>
          logger.info(s"Successfully uploaded $filePath: ${result.getETag}")
      })
    } else {
      kickoff_mt_upload(f).onComplete({
        case Failure(err)=>
          logger.error(s"Could not upload $filePath", err)
        case Success(seq)=>
          logger.info(s"Successfully uploaded $filePath")
      })
    }
  }
}
