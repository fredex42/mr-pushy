import java.io.File

import com.amazonaws.services.s3.{AmazonS3, AmazonS3ClientBuilder}

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

object Main extends App {
  override def main(args: Array[String]): Unit = {
    //implicit val s3conn:AmazonS3 = AmazonS3ClientBuilder.defaultClient()

    val lp = new ListParser("to_flush.lst")
    lp.foreach {(projectId:String,filePath:String)=>
      val md5 = Await.result(EtagCalculator.eTagForFile(new File(filePath), 8*1024*1024), 10 seconds)
      println(s"$filePath: $md5")
      return
    }
  }
}
