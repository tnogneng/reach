package edu.arizona.sista.odin.domains.bigmechanism.summer2015

import java.io._

import scala.io.Source
import scala.collection.mutable.Map
import scala.collection.JavaConverters._
import scala.compat.Platform

import org.biopax.paxtools.io._
import org.biopax.paxtools.controller._
import org.biopax.paxtools.model._
import org.biopax.paxtools.model.level3._

/**
  * Program to lookup/check incoming BioPax model entities against local knowledge bases.
  *   Author: by Tom Hicks. 5/14/2015.
  *   Last Modified: Correct phrase reduction to test all sublists.
  */
object EntityChecker extends App with KnowledgeBaseConstants {

  private val idCntr = new IncrementingCounter() // counter sequence class

  // Search classes for resolving entities:
  val StaticProteinFamilyKBLookup = new StaticProteinFamilyKBLookup
  val StaticProteinKBLookup = new StaticProteinKBLookup
  val StaticChemicalKBLookup = new StaticChemicalKBLookup
  val StaticMetaboliteKBLookup = new StaticMetaboliteKBLookup
  val StaticCellLocationKBLookup = new StaticCellLocationKBLookup
  // val StaticTissueTypeKBLookup = new StaticTissueTypeKBLookup

  /** Search sequence for resolving proteins. */
  protected val proteinSearcher = Seq( StaticProteinKBLookup,
                                       StaticProteinFamilyKBLookup )

  /** Search sequence for small molecules. */
  protected val chemSearcher = Seq( StaticChemicalKBLookup,
                                    StaticMetaboliteKBLookup )

  /** Search sequence for sub cellular locations terms. */
  protected val cellLocationSearcher = Seq( StaticCellLocationKBLookup )


  /** Read the BioPAX model from the given input stream and check the entities. */
  def readAndCheckBioPax (fis:InputStream) = {
    val bpIOH:BioPAXIOHandler = new SimpleIOHandler()
    val model:Model = bpIOH.convertFromOWL(fis)
    checkProteins(model)
    checkCellLocations(model)
    checkChemicals(model)
  }


  private def checkChemicals (model:Model) = {
    val instances:collection.mutable.Set[SmallMolecule] =
      (model.getObjects(classOf[SmallMolecule])).asScala
    val molecules = instances.toSeq.map(_.getDisplayName()).sorted.distinct
    println(s"FOUND: ${molecules.size} small molecules in input model")
    val resolved = molecules.map(resolveKey(_, chemSearcher))
    val missing = molecules.zip(resolved).filter(_._2.isEmpty).map(_._1)
    outputMissing(missing, GendChemicalFilename, GendChemicalPrefix)
  }

  private def checkCellLocations (model:Model) = {
    val instances:collection.mutable.Set[CellularLocationVocabulary] =
      (model.getObjects(classOf[CellularLocationVocabulary])).asScala
    val cellLocs = instances.toSeq.flatMap(_.getTerm().asScala).sorted.distinct
    println(s"FOUND: ${cellLocs.size} cellular location terms in input model")
    val resolved = cellLocs.map(resolveKey(_, cellLocationSearcher))
    val missing = cellLocs.zip(resolved).filter(_._2.isEmpty).map(_._1)
    outputMissing(missing, GendCellLocationFilename, GendCellLocationPrefix)
  }

  private def checkProteins (model:Model) = {
    val instances:collection.mutable.Set[Protein] = (model.getObjects(classOf[Protein])).asScala
    var proteins = instances.toSeq.map(_.getDisplayName()) ++ findComplexProteinNames(model)
    proteins = proteins.sorted.distinct     // sort and remove duplicate names
    println(s"FOUND: ${proteins.size} distinct proteins in input model")

    // try to resolve protein names with canonical name lookup:
    val tried = proteins.map(resolveKey(_, proteinSearcher))
    val missing = proteins.zip(tried).filter(_._2.isEmpty).map(_._1) // find unresolved names

    // try to find remaining unresolved protein names using alternate, transformed keys:
    val tried2 = missing.map(tryAlternateKeys(_, LocalKeyTransforms.proteinKeyTransforms, proteinSearcher))
    val missing2 = missing.zip(tried2).filter(_._2.isEmpty).map(_._1) // find unresolved names

    // try to find remaining unresolved protein names using left-to-right phrase reduction
    val tried3 = missing2.map(tryAdjectivalPhraseReduction(_, proteinSearcher))
    val stillMissing = missing2.zip(tried3).filter(_._2.isEmpty).map(_._1).sorted.distinct

    outputMissing(stillMissing, GendProteinFilename, GendProteinPrefix)
  }


  /** Return a list of the (unsorted and non-unique) names of proteins contained in complexes. */
  private def findComplexProteinNames (model:Model): Seq[String] = {
    val complexes = model.getObjects(classOf[Complex]).asScala
    val proteins = complexes.map(_.getComponent().asScala.filter(_.isInstanceOf[Protein])).flatten
    val protNames = proteins.map(_.getDisplayName()).toSeq
    return protNames
  }


  /** Output the missing entity names and generated IDs to the given file. */
  private def outputMissing (missing:Seq[String], filename:String, prefix:String) = {
    val outFile:File = LocalKBUtils.makeFileInKBDir(filename)
    val out:PrintWriter = new PrintWriter(new BufferedWriter(new FileWriter(outFile)))
    // val now = Platform.currentTime.toString  // make ID unique per program run
    missing.foreach { entName =>
      val nid = "%s%05d".format(prefix, idCntr.next)
      out.println(s"${entName}\t${nid}")
    }
    out.flush()
    out.close()
  }


  /** Search the KB lookups in sequence for the given key. Return the result from the
    * first lookup which resolves the given key, or None otherwise. */
  private def resolveKey (key:String, searchSequence:Seq[LocalKBLookup]): Option[String] = {
    searchSequence.foreach { kb =>
      val resInfo = kb.resolve(key)
      if (resInfo.isDefined)
        return resInfo
    }
    return None
  }

  /** For each of the given transforms, try the given KB lookups in sequence. Return the
    * result from the first lookup which resolves a transformed key, or None otherwise. */
  private def tryAlternateKeys (key:String,
                                transformFns:Seq[(String) => String],
                                searchSequence:Seq[LocalKBLookup]): Option[String] = {
    transformFns.foreach { xFormFN =>
      val xfKey = xFormFN.apply(key)        // create new, transformed key
      if (xfKey != key) {                   // if new key is really different
        searchSequence.foreach { kb =>      // search each KB for the new key
          val resInfo = kb.resolve(xfKey)
          if (resInfo.isDefined)
            return resInfo                  // return info for first matching key/KB
        }
      }
    }
    return None
  }

  /** If mention text is a multi-word phrase, repeatedly shorten the phrase from
    * the left (by whitespace-separated words) and lookup the remainder until
    * reaching the NP head.
    */
  def tryAdjectivalPhraseReduction (key:String,
                                    searchSequence:Seq[LocalKBLookup]): Option[String] = {
    var wordList = key.split("\\s+").toList
    while (!wordList.isEmpty) {             // test all sublists
      val text = wordList.mkString("")      // turn back into contiguous text
      val storageKey = LocalKBUtils.makeKBCanonKey(text) // make canonical storage key
      searchSequence.foreach { kb =>        // search each KB for the new key
        val resInfo = kb.resolve(storageKey)
        if (resInfo.isDefined)
          return resInfo                    // return info for first matching key/KB
      }
      wordList = wordList.tail              // else shorten word list from left
    }
    return None                             // else signal lookup failure
  }


  //
  // Top-level Main of script:
  //
  val filepath:String = if (!args.isEmpty) args(0) else ""
  val fis = new FileInputStream(filepath)
  readAndCheckBioPax(fis)
}
