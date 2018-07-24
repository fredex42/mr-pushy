import java.io.File

import com.amazonaws.services.s3.{AmazonS3, AmazonS3ClientBuilder}
import org.slf4j.LoggerFactory

import scala.concurrent.ExecutionContext
import java.util.concurrent.Executors

import com.amazonaws.ClientConfiguration
import scala.util.{Failure, Success}

object Main extends App {
  override def main(args: Array[String]): Unit = {
    /*
    the default thread pool uses daemon threads which don't hold us open until they complete.
    we want regular threads that do.
     */
    lazy val maxThreads = System.getProperty("maxThreads") match {
      case null =>
        48
      case str: String =>
        str.toInt
    }

    lazy val reallyDelete = System.getProperty("reallyDelete") match {
      case null =>
        false
      case str: String=>
        str == "true"
    }

    implicit val exec:ExecutionContext = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(maxThreads))

    val logger = LoggerFactory.getLogger(getClass)
    lazy val clientConfg:ClientConfiguration = new ClientConfiguration()
    clientConfg.setMaxConnections((maxThreads*1.5).toInt)
    lazy implicit val s3conn: AmazonS3 = AmazonS3ClientBuilder.standard().withClientConfiguration(clientConfg).build()

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

    lazy val hideNotFound = System.getProperty("hideNotFound") match {
      case null =>
        false
      case str: String =>
        true
    }

    logger.info("========================================================================")
    logger.info("New run starting")
    logger.info("========================================================================")
    logger.info(s"Removing $pathSegments from paths for upload")
    logger.info(s"Uploading to $destBucket")
    logger.info(s"Really delete is $reallyDelete")

    val lp = new ListParser("to_flush.lst")

    val uploader = new MtUploader(destBucket, pathSegments)

    var notFoundCounter=0
    var zeroLengthCounter=0
    var alreadyUploadedCounter=0
    var successfulCounter=0
    var failedCounter=0
    var n=0

    lp.foreach { (projectId: String, filePath: String) =>
      val fileref = new File(filePath)
      if(! fileref.exists()){
        notFoundCounter+=1
        if(!hideNotFound) logger.warn(s"$filePath does not exist, skipping")
      } else if(fileref.length()==0) {
        zeroLengthCounter+=1
        if(!hideNotFound) logger.warn(s"$filePath is zero length, skipping")
      } else {
        n+=1
        uploader.kickoff_upload(filePath).map(uploadResult => {
          if(uploadResult.uploadType==UploadResultType.AlreadyThere) alreadyUploadedCounter+=1
          logger.info(s"$filePath: upload completed successfully (${uploadResult.uploadType.toString}), calculating etag")
          EtagCalculator.propertiesForFile(new File(filePath), 8 * 1024 * 1024).map(localFileProperties => {
            logger.debug(s"$filePath: etag calculated, checking if deletable")
            FileChecker.canDelete(destBucket, uploadResult.uploadedPath, localFileProperties).map({
              case true =>
                successfulCounter+=1
                if(reallyDelete) {
                  logger.info(s"$filePath: will delete")
                  fileref.delete()
                } else {
                  logger.info(s"$filePath: can be deleted. Set reallyDelete property to 'true'")
                }
              case false =>
                failedCounter+=1
                logger.warn(s"$filePath: UPLOAD FAILED, removing remote path")
                uploader.delete_failed_upload(filePath) match {
                  case Success(u)=>
                    logger.debug(s"$filePath: remote file deleted")
                  case Failure(err)=>
                    logger.error(s"$filePath: could not delete remote file", err)
                }
            })
          }
          )
        if(n>50){
          //sleep for 10s to make the upload threads kick in, every 50 items
          n=0
          Thread.sleep(10000)
        }
        })
      }
      }
      logger.info("Completed iterating list")
    }
}
