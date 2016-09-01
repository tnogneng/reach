package org.clulab.reach.context.dataset

import java.io.File
import io.Source
import ai.lum.common.Interval
import ai.lum.nxmlreader.standoff.Tree

object ContextLabel extends Enumeration{
  val Species, CellLine, CellType, Organ = Value
}

case class ContextType(val contextType:ContextLabel.Value, val id:String)

object ContextType{
  def parse(annotationId:String) = {
    val tokens = annotationId.split(":", 2)
    val (namespace, gid) = (tokens(0), tokens(1))

    namespace match {
      case "taxonomy" => this(ContextLabel.Species, annotationId)
      case "cellosaurus" => this(ContextLabel.CellLine, annotationId)
      case "cellontology" => this(ContextLabel.CellType, annotationId)
      case "uberon" => this(ContextLabel.Organ, annotationId)
      case "tissuelist" => this(ContextLabel.Organ, annotationId)
      case "uaz" =>
       val x = gid.split("-")(1)
       x match {
         case "org" => this(ContextLabel.Organ, annotationId)
         case "cline" => this(ContextLabel.CellLine, annotationId)
         case "ct" => this(ContextLabel.CellType, annotationId)
       }
    }
  }
}


case class EventAnnotation(val sentenceId:Int,
   val interval:Interval,
   val annotatedContexts:Option[Seq[ContextType]] = None){
     override def equals(o: Any) = o match {
       case other:EventAnnotation =>
        if(this.sentenceId == other.sentenceId &&
          this.interval == other.interval)
          true
        else
          false
       case _ => false
     }
   }
   
case class ContextAnnotation(val sentenceId: Int,
   val interval:Interval,
   val contextType:ContextType){
     override def equals(o: Any) = o match {
       case other:EventAnnotation =>
        if(this.sentenceId == other.sentenceId &&
          this.interval == other.interval)
          true
        else
          false
       case _ => false
     }
   }

case class ArticleAnnotations(val name:String,
   val sentences:Map[Int, String],
   val eventAnnotations:Seq[EventAnnotation],
   val contextAnnotations:Seq[ContextAnnotation],
   val standoff:Option[Tree] = None)

object ArticleAnnotations{
  def readPaperAnnotations(directory:String):ArticleAnnotations = {
    // Read the tsv annotations from a paper
    val rawSentences = Source.fromFile(new File(directory, "sentences.tsv")).getLines
    val sentences:Map[Int, String] = rawSentences.map{
      s =>
        val tokens = s.split("\t")
        (tokens(0).toInt, tokens(1))
    }.toMap

    val rawEvents = Source.fromFile(new File(directory, "events.tsv")).getLines
    val events = rawEvents.map{
      s =>
        val tokens = s.split("\t")
        val sentenceId = tokens(0).toInt

        val bounds = tokens(1).split("-").map(_.toInt)
        val (start, end) = (bounds(0), bounds(1))
        val interval = if(start == end) Interval.singleton(start) else Interval.closed(start, end)
        val contexts = tokens(2).split(",").map(ContextType.parse(_))

        EventAnnotation(sentenceId, interval, Some(contexts))
    }.toSeq

    val rawContext = Source.fromFile(new File(directory, "context.tsv")).getLines
    val context = rawContext.map{
      s =>
        val tokens = s.split("\t")

        val sentenceId = tokens(0).toInt

        val bounds = tokens(1).split("-").map(_.toInt)
        val (start, end) = (bounds(0), bounds(1))
        val interval = if(start == end) Interval.singleton(start) else Interval.closed(start, end)
        val context = ContextType.parse(tokens(2))

        ContextAnnotation(sentenceId, interval, context)
    }.toSeq

    val soffFile = new File(directory, "standoff.json")
    val standoff = if(soffFile.exists) Some(Tree.readJson(soffFile.getPath)) else None

    ArticleAnnotations(directory, sentences, events, context, standoff)
  }
}
