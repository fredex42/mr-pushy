import java.io.File
import java.io.FileInputStream
import java.nio.channels.FileChannel
import java.security.MessageDigest
import java.nio.{ByteBuffer, MappedByteBuffer}

import org.apache.commons.codec.binary.Hex
import org.slf4j.LoggerFactory

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

object EtagCalculator {
  val logger = LoggerFactory.getLogger(getClass)

  def md5Of(str:Array[Byte])(implicit  exec:ExecutionContext):Array[Byte] = {
    MessageDigest.getInstance("md5").digest(str)
  }

  def md5Of(buffer:ByteBuffer):Array[Byte] = {
    val instance = MessageDigest.getInstance("md5")
    instance.update(buffer)
    instance.digest()
  }

  def md5NextChunk(file:File, chunkNum:Int, chunkSize:Int, lastChunkSize:Int, totalChunks:Int, md5List:Seq[Future[Array[Byte]]])(implicit  exec:ExecutionContext):Seq[Future[Array[Byte]]] = {
    if(chunkNum>totalChunks) return md5List

    val updatedList = md5List :+ Future {
      val stream = new FileInputStream(file)

      logger.debug(s"${file.getPath}: reading chunk $chunkNum of $totalChunks")
      var buffer = if (chunkNum < totalChunks) {
        stream.getChannel.map(FileChannel.MapMode.READ_ONLY, chunkNum.toLong * chunkSize.toLong, chunkSize)
      } else {
        stream.getChannel.map(FileChannel.MapMode.READ_ONLY, chunkNum.toLong * chunkSize.toLong, lastChunkSize)
      }
      stream.close()
      md5Of(buffer)
    }

    md5NextChunk(file, chunkNum+1, chunkSize,lastChunkSize, totalChunks, updatedList)
  }

  /**
    * Calculates the expected etag for the file.  This is done in as parallel a manner as possible
    * @param file java.nio.File object representing the file to check
    * @param chunkSize chunk size with which it is uploaded. This is needed for an accurate etag calculation
    * @param exec implicitly provided execution context
    * @return a Future, containing a String of the expected etag
    */
  def eTagForFile(file:File, chunkSize:Int)(implicit  exec:ExecutionContext):Future[String] = {
    logger.debug(s"calculating local etag for file ${file.getAbsolutePath}")
    logger.debug(s"file size is ${file.length} chunk size is $chunkSize")
    val totalChunks = math.ceil(file.length() / chunkSize).toInt
    logger.debug(s"total chunks: $totalChunks")
    if (totalChunks == 0) {
      /*for an unchunked upload then the hash is simply the md5 of the entire file*/
      Future {
        val stream = new FileInputStream(file)
        val bytes = stream.getChannel.map(java.nio.channels.FileChannel.MapMode.READ_ONLY, 0, file.length())
        stream.close()
        Hex.encodeHexString(md5Of(bytes))
      }
    } else {
      val lastChunkSize = (file.length() - totalChunks * chunkSize).toInt
      logger.debug(s"last chunk size: $lastChunkSize")
      val md5Seq = Future.sequence(md5NextChunk(file, 0, chunkSize, lastChunkSize, totalChunks, Seq()))

      /* general logging message */
      md5Seq.onComplete({
        case Failure(err) =>
          logger.error(s"${file.getAbsolutePath}: Could not calculate md5 chain", err)
        case Success(seq) =>
          logger.debug(s"${file.getAbsolutePath}: Calculated md5 chain")
      })

      /* reduce the list of portions back to a single one */
      md5Seq.map(md5Parts => {
        val finalByteString: Array[Byte] = md5Parts.reduce((acc, item) => acc ++ item)
        Hex.encodeHexString(md5Of(finalByteString)) + s"-${totalChunks + 1}"
      })
    }
  }

  def propertiesForFile(file:File, chunkSize:Int)(implicit  exec:ExecutionContext):Future[LocalFileProperties] = {
    val etagFuture = eTagForFile(file, chunkSize)
    val localFileSize = file.length()

    if(file.length()>chunkSize){
      //if we put a lot of chunks onto the queue, then delay returning a little to try to make sure that they get added together
      Thread.sleep(Math.ceil(0.1*(file.length/chunkSize)).toLong)
    }

    etagFuture.map(eTag=>LocalFileProperties(eTag, localFileSize))
  }
}
