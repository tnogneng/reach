package org.clulab.reach

import java.io.File
import scala.collection.mutable.StringBuilder
import org.clulab.odin._
import org.clulab.processors.{ Document, Sentence }
import org.clulab.processors.bionlp.BioNLPProcessor
import org.clulab.reach.mentions._
import org.clulab.reach.grounding._
import org.clulab.reach.darpa.{ DarpaActions, MentionFilter, NegationHandler }
import ai.lum.common.FileUtils._
import RuleReader.readResource

object BioNlp2013 {

  def main(args: Array[String]): Unit = {

    // initializing all our stuff
    val bionlpSystem = new BioNlp2013System

    // read bionlp 2013 dataset
    val dataDir = new File("/home/marco/data/reach/BioNLP-ST-2013_GE_train_data_rev3")
    for (txtFile <- dataDir.listFilesByWildcard("*.txt")) {
      val doc = bionlpSystem.readBioNlpAnnotations(txtFile)
      val results = bionlpSystem.extractFrom(doc)
      // TODO dump event mentions (preferably as brat)
    }

  }

}

// a brat textbound mention
case class BratTBM(id: String, label: String, start: Int, end: Int, text: String)

class BioNlp2013System {

  // initialize processor
  val processor = new BioNLPProcessor
  processor.annotate("something")

  // initialize reach
  val reachSystem = new ReachSystem

  def extractFrom(doc: Document): Seq[BioMention] = {
    reachSystem.extractFrom(doc)
  }

  def mkDoc(text: String, docId: String): Document = {
    val doc = processor.annotate(text, keepText = true)
    doc.id = Some(docId)
    doc
  }

  // returns annotated document corresponding to bionlp2013 file
  // with entities provided in the corpus
  def readBioNlpAnnotations(txtFile: File): Document = {
    val docId = txtFile.getBaseName() // the docId is the filename minus path and extension
    val text = txtFile.readString()
    val doc = mkDoc(text, docId)
    val a1File = new File("""\.txt$""".r.replaceAllIn(txtFile.getCanonicalPath(), ".a1"))
    val tbms = readA1Annotations(a1File)
    replaceNer(doc, tbms)
    // FIXME the following prints are for debugging only and should be removed
    tbms.foreach(println)
    for (s <- doc.sentences) {
      for (i <- s.words.indices) {
        print(s"(${s.words(i)}, ${s.tags.get(i)}, ${s.startOffsets(i)}, ${s.endOffsets(i)})")
      }
      println()
    }
    // val a2File = new File("""\.txt$""".r.replaceAllIn(txtFile.getCanonicalName(), ".a2"))
    doc
  }

  def readA1Annotations(a1File: File): Vector[BratTBM] = {
    val pattern = """(T\d+)\t(\w+) (\d+) (\d+)\t(.+)""".r
    val text = a1File.readString()
    pattern
      .findAllMatchIn(text)
      .map(m => BratTBM(m.group(1), m.group(2), m.group(3).toInt, m.group(4).toInt, m.group(5)))
      .toVector
  }

  // replaces ner in-place
  def replaceNer(doc: Document, tbms: Vector[BratTBM]): Unit = {
    for (sent <- doc.sentences) {
      sent.tags = mkTags(sent, tbms)
    }
  }

  def mkTags(sent: Sentence, tbms: Vector[BratTBM]): Option[Array[String]] = tbms match {
    case Vector() => Some(Array.fill(sent.words.size)("O"))
    case tbms =>
      val tags = Array.fill(sent.words.size)("O")
      // only consider mentions in the current sentence
      for (m <- tbms if m.start >= sent.startOffsets.head && m.end <= sent.endOffsets.last) {
        var first = true
        for (i <- tags.indices) {
          if (m.start >= sent.startOffsets(i) && m.end <= sent.endOffsets(i)) {
            val tag = if (first) {
              first = false
              s"B-${m.label}"
            } else {
              s"I-${m.label}"
            }
            tags(i) = tag
          }
        }
      }
      Some(tags)
  }

  def dumpA2Annotations(mentions: Seq[BioMention], entities: Seq[BratTBM]): String = {
    // start standoff
    val standoff = new StringBuilder
    // retrieve all textbound mentions
    val tbms = mentions.flatMap {
      case m: BioTextBoundMention => Some(m)
      case _ => None
    }
    // map textbound mentions to brat id
    val tbmToId = tbms.flatMap { m =>
      val start = m.startOffset
      val end = m.endOffset
      entities.find(e => e.start <= start && e.end >= end).map(m -> _.id)
    }.toMap
    // retrieve all event mentions
    val ems = mentions.flatMap {
      case m: BioEventMention => Some(m)
      case _ => None
    }
    // TODO select events to report
    // TODO make textbound mentions for triggers
    // TODO make event mentions for events
    // TODO return brat standoff
    standoff.mkString
  }

}
