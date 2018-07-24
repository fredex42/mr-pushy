import java.io.File

import com.amazonaws.services.s3.{AmazonS3, AmazonS3ClientBuilder}
import org.slf4j.LoggerFactory

import scala.concurrent.{Await, ExecutionContext, Future}
import java.util.concurrent.Executors

import scala.concurrent.duration._

object Main extends App {
  override def main(args: Array[String]): Unit = {
    /*
    the default thread pool uses daemon threads which don't hold us open until they complete.
    we want regular threads that do.
     */
    implicit val exec:ExecutionContext = ExecutionContext.fromExecutor(Executors.newCachedThreadPool())

    val logger = LoggerFactory.getLogger(getClass)
    lazy implicit val s3conn: AmazonS3 = AmazonS3ClientBuilder.defaultClient()

    lazy val destBucket = System.getProperty("destBucket")
    if (destBucket == null) {
      logger.error("You need to set the destBucket property")
      System.exit(1)
    }

    lazy val pathSegments = System.getProperty("stripPathSegments") match {
      case null =>
        5
      case str: String =>
        str.toInt
    }

    logger.info("========================================================================")
    logger.info("New run starting")
    logger.info("========================================================================")
    logger.info(s"Removing $pathSegments from paths for upload")
    logger.info(s"Uploading to $destBucket")

    val lp = new ListParser("to_flush.lst")

    val uploader = new MtUploader(destBucket, pathSegments)

    lp.foreach { (projectId: String, filePath: String) =>
      if(! new File(filePath).exists()){
        logger.warn(s"$filePath does not exist, skipping")
      } else {
        val f = uploader.kickoff_upload(filePath).map(uploadResult => {
          logger.debug("upload completed successfully, calculating etag")
          EtagCalculator.propertiesForFile(new File(filePath), 8 * 1024 * 1024).map(localFileProperties => {
            logger.debug("etag calculated, checking if deletable")
            FileChecker.canDelete(destBucket, uploadResult.uploadedPath, localFileProperties).map({
              case true =>
                logger.info(s"Can delete $filePath")
              case false =>
                logger.warn(s"$filePath: UPLOAD FAILED, removing remote path")
            })
          }
          )
        })
        return
      }
    }
  }
}
