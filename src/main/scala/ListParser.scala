import scala.io.Source

class ListParser(listFileName:String) {
  val lineExtractor = "^(\d+),(.*)$".r

  /**
    * iterates through the provided file and calls the provided block for each entry
    * @param func Block that takes (projectId, filePath) as arguments
    * @return number of items finally processed
    */
  def foreach(func: (String, String)=>Unit):Int = {
    var n=0
    for(line<-Source.fromFile(listFileName).getLines) {
      //syntax is weird but it should work - https://alvinalexander.com/scala/how-to-extract-parts-strings-match-regular-expression-regex-scala
      val lineExtractor(projectId, filePath) = line
      func(projectId, filePath)
      n+=1
    }
    n
  }
}
