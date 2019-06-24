package org.clulab.reach.context.context_exec
import com.typesafe.config.ConfigFactory

import scala.io.Source
import scala.util.parsing.json.JSON
import java.io.{File, PrintWriter}
import ai.lum.nxmlreader.NxmlReader
import org.clulab.reach.PaperReader.{contextEngineParams, ignoreSections, preproc, procAnnotator}
import org.clulab.reach.ReachSystem
import org.clulab.reach.context.ContextEngine
import org.clulab.reach.context.ContextEngineFactory.Engine
import org.clulab.reach.context.context_exec.GenerateOutputFiles.{nxmlReader, reachSystem}
import org.clulab.reach.context.context_utils.EventContextPairGenerator
import org.clulab.reach.mentions.{BioEventMention, BioTextBoundMention}
object Polarity extends App {
  println("Hello world from polarity")
  val config = ConfigFactory.load()
  val activSentPath = config.getString("polarityContext.genericFileDir").concat("activation_sentences_in_json.txt")
  val inhibSentPath = config.getString("polarityContext.genericFileDir").concat("inhibition_sentences_in_json.txt")
  val activSentences = Source.fromFile(activSentPath).getLines()
  val inhibSentences = Source.fromFile(inhibSentPath).getLines()
  val typeOfPaper = config.getString("polarityContext.typeOfPaper")
  val dirForType = config.getString("polarityContext.paperTypeResourceDir").concat(typeOfPaper)
  val fileListUnfiltered = new File(dirForType)
  val fileList = fileListUnfiltered.listFiles().filter(x => x.getName.endsWith(".nxml"))
  val reachSystem = new ReachSystem()
  val sentenceFileContentsForIntersect = collection.mutable.ListBuffer[String]()

  println(fileList.size)
  for(file<- fileList) {
    val pmcid = file.getName.slice(0,file.getName.length-5)
    val outPaperDirPath = config.getString("svmContext.contextOutputDir").concat(s"${typeOfPaper}/${pmcid}")
    val pathForPolarity = outPaperDirPath.concat("/sentences.txt")
    val lines = Source.fromFile(pathForPolarity).getLines()
    sentenceFileContentsForIntersect ++= lines
    for(l<-lines) {
      println(l)
    }
  }

  val activeSentenceForIntersect = collection.mutable.ListBuffer[String]()
  for(text<-activSentences) {
    val doc = reachSystem.mkDoc(text, "", "")
    val newText = doc.sentences(0).getSentenceText
    activeSentenceForIntersect += newText
  }


  val intersection = activeSentenceForIntersect.toSet.intersect(sentenceFileContentsForIntersect.toSet)
  println(s" The intersection has size: ${intersection.size}")


  for(a<-activeSentenceForIntersect) {
    if(sentenceFileContentsForIntersect.indexOf(a) == -1)
      println(a)
  }


  val inhibSentenceForIntersect = collection.mutable.ListBuffer[String]()
  for(text<-inhibSentences) {
    val doc = reachSystem.mkDoc(text, "", "")
    val newText = doc.sentences(0).getSentenceText
    inhibSentenceForIntersect += newText
  }

  val inhibInterSection = inhibSentenceForIntersect.toSet.intersect(sentenceFileContentsForIntersect.toSet)
  println(s"The size of inhibition intersection is: ${inhibInterSection.size}")


  for(a<-inhibSentenceForIntersect) {
    if(sentenceFileContentsForIntersect.indexOf(a) == -1)
      println(a)
  }


}
