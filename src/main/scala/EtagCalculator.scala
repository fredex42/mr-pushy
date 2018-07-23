import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

import org.apache.commons.codec.binary
import org.apache.commons.codec.binary.Hex
import org.slf4j.LoggerFactory

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success}

object EtagCalculator {
  val logger = LoggerFactory.getLogger(getClass)

  def md5Of(str:Array[Byte]):Future[Array[Byte]] = Future {
    MessageDigest.getInstance("md5").digest(str)
  }

  def md5NextChunk(stream:FileInputStream, chunkNum:Int, chunkSize:Int, lastChunkSize:Int, totalChunks:Int, md5List:Seq[Future[Array[Byte]]]):Seq[Future[Array[Byte]]] = {
    if(chunkNum>totalChunks) return md5List

    logger.debug(s"reading chunk $chunkNum of $totalChunks")
    val bytes = if(chunkNum<totalChunks){
      val bytes = Array.fill[Byte](chunkSize)(0)
      stream.read(bytes,0,chunkSize)
      bytes
    } else {
      val bytes = Array.fill[Byte](lastChunkSize)(0)
      stream.read(bytes,0, lastChunkSize)
      bytes
    }

    val updatedList = md5List :+ md5Of(bytes)
    md5NextChunk(stream, chunkNum+1, chunkSize,lastChunkSize, totalChunks, updatedList)
  }

  def eTagForFile(file:File, chunkSize:Int):Future[String] = {
    val stream = new FileInputStream(file)
    logger.debug(s"calculating local etag for file ${file.getAbsolutePath}")
    logger.debug(s"file size is ${file.length} chunk size is ${chunkSize}")
    val totalChunks = math.ceil(file.length()/chunkSize).toInt
    logger.debug(s"total chunks: $totalChunks")
    val lastChunkSize=(file.length() - (totalChunks)*chunkSize).toInt
    logger.debug(s"last chunk size: $lastChunkSize")
    val md5Seq = Future.sequence(md5NextChunk(stream,0,chunkSize,lastChunkSize, totalChunks,Seq()))

    /* ensure that the FileInputStream is closed */
    md5Seq.onComplete({
      case Failure(err)=>
        logger.error(s"${file.getAbsolutePath}: Could not calculate md5 chain", err)
        stream.close()
      case Success(seq)=>
        logger.debug(s"${file.getAbsolutePath}: Calculated md5 chain")
        stream.close()
    })

    /* reduce the list of portions back to a single one */
    md5Seq.flatMap(md5Parts=>{
      val finalByteString:Array[Byte] = md5Parts.reduce((acc,item)=>acc++item)
      md5Of(finalByteString).map(bs=>Hex.encodeHexString(bs) + s"-${totalChunks+1}")
    })
  }
}
