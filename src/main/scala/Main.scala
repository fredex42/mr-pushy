import com.amazonaws.services.s3.{AmazonS3, AmazonS3ClientBuilder}

import scala.concurrent.ExecutionContext.Implicits.global

object Main extends App {
  override def main(args: Array[String]): Unit = {
    implicit val s3conn:AmazonS3 = AmazonS3ClientBuilder.defaultClient()

    val lp = new ListParser("to_flush.lst")
    lp.foreach {(projectId:String,filePath:String)=>

    }
  }
}
