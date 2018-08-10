import scala.io.Source
import org.slf4j.LoggerFactory

class ListParser(listFileName:String, noProjects:Boolean) {
  val lineExtractor = "^(\\d+),(.*)$".r
  val lineExtractorNoProjects = "^(\\/.*)".r
  val logger = LoggerFactory.getLogger(getClass)

  /**
    * iterates through the provided file and calls the provided block for each entry
    * @param func Block that takes (projectId, filePath) as arguments
    * @return number of items finally processed
    */
  def foreach(func: (Option[String], String)=>Boolean):Int = {
    var n=0
    for(line<-Source.fromFile(listFileName).getLines) {
      try {
        //syntax is weird but it should work - https://alvinalexander.com/scala/how-to-extract-parts-strings-match-regular-expression-regex-scala
        if(noProjects) {
          val lineExtractorNoProjects(filePath) = line
          if(func(None, filePath)){
            logger.info(s"Terminating list checking at line $n on program request")
            return n
          }
        } else {
          val lineExtractor(projectId, filePath) = line
          if(func(Some(projectId), filePath)){
            logger.info(s"Terminating list checking at line $n on program request")
            return n
          }
        }
        n += 1
      } catch {
        case e:MatchError=>
          logger.warn(s"Line $n of $listFileName is invalid: $line")
          n+=1
      }
    }
    n
  }
}
