import com.amazonaws.services.s3.{AmazonS3, AmazonS3Client}
import org.slf4j.LoggerFactory

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

object FileChecker {
  private val logger = LoggerFactory.getLogger(getClass)

  /**
    * checks the file at `remoteBucket`/`remotePath` and returns True if the etag and size match the local file properties provided
    * @param remoteBucket String, remote bucket to access
    * @param remotePath String, path of file to check in bucket
    * @param localFileProperties [[LocalFileProperties]] object giving the values to check against
    * @param s3Client implicitly provided AmazonS3 client object
    * @return a Future containing either True (remote matches, local can be deleted) or False (remote does not match)
    */
  def canDelete(remoteBucket: String, remotePath:String, localFileProperties: LocalFileProperties)(implicit s3Client: AmazonS3):Future[Boolean] = Future {
    val s3Meta = s3Client.getObject(remoteBucket, remotePath).getObjectMetadata

    logger.debug(s"$remotePath: Local etag is ${localFileProperties.eTag}, remote is ${s3Meta.getETag}")
    logger.debug(s"$remotePath: Local size is ${localFileProperties.fileSize}, remote is ${s3Meta.getContentLength}")

    localFileProperties.eTag==s3Meta.getETag && localFileProperties.fileSize==s3Meta.getContentLength
  }
}
