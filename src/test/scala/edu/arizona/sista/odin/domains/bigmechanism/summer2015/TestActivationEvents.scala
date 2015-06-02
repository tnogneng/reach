package edu.arizona.sista.odin.domains.bigmechanism.summer2015

import org.scalatest.{Matchers, FlatSpec}

import TestUtils._

/**
 * Unit tests to ensure Activation event rules are matching correctly
 * User: mihais
 * Date: 5/19/15
 */
class TestActivationEvents extends FlatSpec with Matchers {
  val sent1 = "Nucleotide free Ras inhibits PI3KC2Beta activity."
  val sent1b = "Nucleotide free Ras inhibits PI3KC2Beta."
  val sent1c = "Nucleotide free Ras inhibits activation of PI3KC2Beta."
  val sent1d = "Addition of Ras inhibits PI3KC2Beta."
  val sent1e = "Increase of Ras dose inhibits PI3KC2Beta."
  sent1 should "contain negative activation patterns" in {
    var mentions = parseSentence(sent1)
    mentions.filter(_.label == "Negative_activation") should have size (1)

    mentions = parseSentence(sent1b)
    mentions.filter(_.label == "Negative_activation") should have size (1)

    mentions = parseSentence(sent1c)
    mentions.filter(_.label == "Negative_activation") should have size (1)

    mentions = parseSentence(sent1d)
    mentions.filter(_.label == "Negative_activation") should have size (1)

    mentions = parseSentence(sent1e)
    mentions.filter(_.label == "Negative_activation") should have size (1)
  }

  val sent2 = "Ubiquitinated Ras activates Raf and PI3K."
  val sent2b = "Ubiquitinated Ras increases Raf and PI3K activity."
  sent2 should "contain multiple different positive activations" in {
    var mentions = parseSentence(sent2)
    mentions.filter(_.label == "Positive_activation") should have size (2)

    mentions = parseSentence(sent2)
    mentions.filter(_.label == "Positive_activation") should have size (2)
  }

  val sent3 = "the phosphorylation of Ras promotes the ubiquitination of MEK"
  sent3 should "contain NO activation events, and a single positive regulation" in {
    val mentions = parseSentence(sent3)
    mentions.filter(_.label == "Positive_activation") should have size (0)
    mentions.filter(_.label == "Positive_regulation") should have size (1)
  }

  val sent4 = "We observed increased ERBB3 binding to PI3K following MEK inhibition (Figure 1D), and accordingly, MEK inhibition substantially increased tyrosine phosphorylated ERBB3 levels (Figure 1A)."
  sent4 should "contain 1 negative activation and NO positive activation events" in {
    val mentions = parseSentence(sent4)
    hasNegativeActivation("MEK", "ERBB3", mentions) should be (true)
    hasPositiveActivation("MEK", "ERBB3", mentions) should be (false)
    mentions.filter(_.label.contains("Positive_regulation")) should have size (0)
    mentions.filter(_.label.contains("Negative_regulation")) should have size (1)
    hasNegativeRegulationByEntity("MEK", "Binding", List("ERBB3", "PI3K"), mentions) should be (true)
  }

  val sent5 = "the suppression of ASPP1 decreases ASPP2."
  sent5 should "contain 1 positive activation and NO negative activation or regulation events" in {
    val mentions = parseSentence(sent5)
    hasNegativeActivation("ASPP1", "ASPP2", mentions) should be (false)
    hasPositiveActivation("ASPP1", "ASPP2", mentions) should be (true)
    mentions.filter(_.label.contains("regulation")) should have size (0)
  }

  val sent6 = "ASPP1 is an activator of ASPP2"
  sent6 should "contain 1 positive activation event" in {
    val mentions = parseSentence(sent6)
    hasNegativeActivation("ASPP1", "ASPP2", mentions) should be (false)
    hasPositiveActivation("ASPP1", "ASPP2", mentions) should be (true)
    mentions.filter(_.label.contains("regulation")) should have size (0)
  }

  val sent7 = "ASPP1 is an inhibitor of ASPP2"
  sent7 should "contain 1 negative activation event" in {
    val mentions = parseSentence(sent7)
    hasNegativeActivation("ASPP1", "ASPP2", mentions) should be (true)
    hasPositiveActivation("ASPP1", "ASPP2", mentions) should be (false)
    mentions.filter(_.label.contains("regulation")) should have size (0)
  }

  val sent8 = "The ASPP2-binding activity of CREB is, in most cases, constitutive."
  sent8 should "contain a binding but not an activation or regulation event" in {
    val mentions = parseSentence(sent8)
    mentions.filter(_.label.contains("activation")) should have size (0)
    mentions.filter(_.label.contains("regulation")) should have size (0)
    hasEventWithArguments("Binding", List("ASPP2", "CREB"), mentions) should be (true)
  }

  val sent9 = "HOXB7 overexpression induced a decrease of c-FOS"
  sent9 should "contain 1 negative activation and 0 positive ones" in {
    val mentions = parseSentence(sent9)
    mentions.filter(_.label.contains("Transcription")) should have size (1)
    mentions.filter(_.label.contains("Negative_activation")) should have size (1)
  }

  val sent10 = "The suppression of ASPP1 increases the inhibition of ASPP2."
  sent10 should "contain 1 positive activation and 0 negative ones" in {
    val mentions = parseSentence(sent10)
    mentions.filter(_.label.contains("Positive_activation")) should have size (1)
    mentions.filter(_.label.contains("Negative_activation")) should have size (0)
  }

  // Controller and Controlled cannot be the same entity
  val sent11 = "MEK activates MEK."
  sent11 should "not contain a positive activation" in {
    val mentions = parseSentence(sent11)
    mentions.filter(_.label.contains("Positive_activation")) should have size (0)
  }

  val sent12 = "mTOR inhibitor Rapamycin"
  sent12 should "contain a negative activation" in {
    val mentions = parseSentence(sent12)
    hasNegativeActivation("Rapamycin", "mTOR", mentions) should be (true)
  }

  val sent13 = "mTOR activator Rapamycin"
  sent13 should "contain a positive activation" in {
    val mentions = parseSentence(sent13)
    hasPositiveActivation("Rapamycin", "mTOR", mentions) should be (true)
  }

  val sent14 = "Rapamycin, an inhibitor of the mTOR kinase,"
  sent14 should "contain a negative activation" in {
    val mentions = parseSentence(sent14)
    hasNegativeActivation("Rapamycin", "mTOR", mentions) should be (true)
  }

  val sent15 = "Rapamycin, an activator of the mTOR kinase,"
  sent15 should "contain a positive activation" in {
    val mentions = parseSentence(sent15)
    hasPositiveActivation("Rapamycin", "mTOR", mentions) should be (true)
  }
}
