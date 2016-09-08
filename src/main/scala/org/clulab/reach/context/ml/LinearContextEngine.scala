package org.clulab.reach.conetxt.ml

import java.io._
import org.clulab.reach.context.ContextEngine
import org.clulab.reach.mentions._
import org.clulab.learning._
import org.clulab.reach.context.dataset.ContextType
import org.clulab.reach.context.dataset._
import org.clulab.learning._

class LinearContextEngine(val parametersFile:File, val normalizersFile:File) extends ContextEngine {

  // Load the trained data
  val classifier = LiblinearClassifier.loadFrom[String, String](parametersFile.getAbsolutePath)
  // val normalizers:ScaleRange[String] = ScaleRange.loadFrom(new FileReader(normalizersFile))
  val ois = new ObjectInputStream(new FileInputStream(normalizersFile)) {
    override def resolveClass(desc: java.io.ObjectStreamClass): Class[_] = {
      try { Class.forName(desc.getName, false, getClass.getClassLoader) }
      catch { case ex: ClassNotFoundException => super.resolveClass(desc) }
    }
  }
  val normalizers:ScaleRange[String] = ois.readObject.asInstanceOf[ScaleRange[String]]

  var paperMentions:Option[Seq[BioTextBoundMention]] = None
  var paperContextTypes:Option[Seq[ContextType]] = None
  var paperContextTypeCounts:Option[Map[String, Int]] = None


  def classify(bioM:BioMention):BioMention = paperMentions match {
    // Classify this event with all the context types in the paper
    case Some(contextMentions) =>
        // Temporarely dissabled context assignment from BioTextBoundMentions (they are too many) for efficiency
        bioM match {
            case bm:BioTextBoundMention => bm
            case bioMention =>
                // Only assign context if this is not a context mention
                if(!ContextEngine.isContextMention(bioMention)){
                    val contextTypes:Seq[ContextType] = paperContextTypes.get.filter{
                        t =>
                            val mentions = contextMentions.filter(m => ContextType.parse(m.nsId) == t)
                            // Create feature pairs
                            val instances = FeatureExtractor.extractFeatures(bioMention.document, Seq(bioMention), mentions)
                            // Get the type frequency
                            val contextTypeCount:Int = paperContextTypeCounts.get.apply(t.id)
                            // Make the datum instance for classification
                            val datum = FeatureExtractor.mkRVFDatum(instances, contextTypeCount, "true") // Label doesn´t matter here
                            // Normalize the datum
                            val scaledFeats =  Datasets.svmScaleDatum(datum.featuresCounter, normalizers)
                            val scaledDatum = new RVFDatum(datum.label, scaledFeats)
                            // val scaledDatum = datum
                            // Classify it
                            val isContext:Boolean = classifier.classOf(scaledDatum) == "true"

                            // If it's context, we keep it :)
                            isContext
                    }

                    // Create the context map
                    val contextMap:Map[String, Seq[String]] = contextTypes.map(t => (t.contextType.toString, t.id)).groupBy(t => t._1).mapValues(v => v.map(_._2)).mapValues(_.toSet.toSeq)

                    // Assign context
                    bioMention.context = Some(contextMap)

                    // Return the mention with context
                    bioMention
                }
                else
                    bioMention // Return a context mention by itself
        }

    case None => throw new RuntimeException("LinearContextEngine hasn't been called to infer")
  }

  // Implementation of the ContextEngine trait
  def assign(mentions: Seq[BioMention]): Seq[BioMention] = paperMentions match {
    case Some(contextMentions) => mentions map classify
    case None => throw new RuntimeException("LinearContextEngine hasn't been called to infer")
  }

  def infer(mentions: Seq[BioMention]) {
    // We store the paper's mentions here to do classification later
    paperMentions = Some(mentions.filter{ case tb:BioTextBoundMention => true; case _ => false}.map(_.asInstanceOf[BioTextBoundMention]))

    // Get the contexttypes in the document
    paperContextTypes = Some(paperMentions.get.map(m => ContextType.parse(m.nsId)))

    // Compute the context type counts
    paperContextTypeCounts = Some(paperMentions.get.map(_.nsId).groupBy(identity).mapValues(_.size))
  }
  def update(mentions: Seq[BioMention]) {
    // Not doing anything here yet
  }
  ////////////////////////////////////////////
}
