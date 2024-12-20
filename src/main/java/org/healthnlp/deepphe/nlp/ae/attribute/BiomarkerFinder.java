package org.healthnlp.deepphe.nlp.ae.attribute;

import org.apache.ctakes.core.pipeline.PipeBitInfo;
import org.apache.ctakes.core.util.Pair;
import org.apache.ctakes.core.util.regex.RegexSpanFinder;
import org.apache.ctakes.ner.creator.AnnotationCreator;
import org.apache.ctakes.ner.creator.DpheAnnotationCreator;
import org.apache.ctakes.ner.group.dphe.DpheGroup;
import org.apache.ctakes.typesystem.type.textsem.AnatomicalSiteMention;
import org.apache.ctakes.typesystem.type.textsem.EventMention;
import org.apache.ctakes.typesystem.type.textsem.IdentifiedAnnotation;
import org.apache.ctakes.typesystem.type.textspan.Sentence;
import org.apache.log4j.Logger;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.fit.component.JCasAnnotator_ImplBase;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.apache.ctakes.ner.group.dphe.DpheGroup.GENE_PRODUCT;
import static org.apache.ctakes.ner.group.dphe.DpheGroup.TEST_RESULT;

/**
 * @author SPF , chip-nlp
 * @since {4/2/2021}
 */
@PipeBitInfo(
      name = "BiomarkerFinder",
      description = "Finds Biomarker values.",
      products = { PipeBitInfo.TypeProduct.IDENTIFIED_ANNOTATION, PipeBitInfo.TypeProduct.GENERIC_RELATION },
      role = PipeBitInfo.Role.ANNOTATOR )
final public class BiomarkerFinder extends JCasAnnotator_ImplBase {

   static private final Logger LOGGER = Logger.getLogger( "BiomarkerFinder" );


   static private final String REGEX_METHOD
         = "IHC|Immunohistochemistry|ISH|(?:IN SITU HYBRIDIZATION)|(?:DUAL ISH)"
           + "|FISH|(?:Fluorecent IN SITU HYBRIDIZATION)|(?:Nuclear Staining)";
   // for

   static private final String REGEX_TEST = "Test|Method";

   static private final String REGEX_LEVEL = "Level|status|expression|result|results|score";

//   static private final String REGEX_IS = "is|are|was";

   static private final String REGEX_STRONGLY = "weakly|strongly|greatly";
   static private final String REGEX_ELEVATED = "rising|increasing|elevated|elvtd|raised|increased|strong|amplified";
   static private final String REGEX_FALLING = "falling|decreasing|low|lowered|decreased|weak";
   static private final String REGEX_STABLE = "stable";


   static private final String REGEX_GT_LT = "(?:(?:Greater|>|Higher|Less|<|Lower)(?: than ?)?)?"
                                             + "(?: or )?(?:Greater|>|Higher|Less|<|Lower|Equal|=)(?: than|to "
                                             + "?)?";

//   static private final String REGEX_POSITIVE = "\\+?pos(?:itive|itivity)?|\\+(?:pos)?|overexpression";
//   static private final String REGEX_NEGATIVE = "\\-?neg(?:ative)?|\\-(?:neg)?|(?:not amplified)|(?:no [a-z] detected)";
   static private final String REGEX_POSITIVE = "\\+?pos(?:itive|itivity)?|overexpression|express";
   static private final String REGEX_NUM_POSITIVE = "3\\+";

   static private final String REGEX_NEGATIVE = "-?neg(?:ative)?|unamplified|(?:not amplified)|(?:no [a-z] detected)|"
                                                + "(?:non-? ?detected)|(?:not express)";
   static private final String REGEX_NUM_NEGATIVE = "0|1\\+";

   static private final String REGEX_UNKNOWN
//         = "unknown|indeterminate|equivocal|borderline|(?:not assessed|requested|applicable)|\\sN\\/?A\\s";
         = "unknown|indeterminate|equivocal|borderline";
   static private final String REGEX_NUM_BORDERLINE = "2\\+";

   static private final String REGEX_NOT_ASSESSED
         = "(?:not assessed|requested|applicable)|insufficient|pending|\\sN\\/?A";


   static private final String REGEX_POS_NEG = "(?:" + REGEX_POSITIVE + ")|(?:" + REGEX_NEGATIVE + ")";

   static private final String REGEX_POS_NEG_UNK
         = "(?:" + REGEX_POSITIVE + ")|(?:" + REGEX_NEGATIVE + ")|(?:" + REGEX_UNKNOWN + ")";

   static private final String REGEX_POS_NEG_UNK_NA
         = "(?:" + REGEX_POSITIVE
         + ")|(?:" + REGEX_NEGATIVE
         + ")|(?:" + REGEX_UNKNOWN
         + ")|(?:" + REGEX_NOT_ASSESSED + ")";
   static private final String REGEX_POS_NEG_UNK_NA_NUM
         = "(?:" + REGEX_POSITIVE
           + ")|(?:" + REGEX_NUM_POSITIVE
           + ")|(?:" + REGEX_NEGATIVE
           + ")|(?:" + REGEX_NUM_NEGATIVE
           + ")|(?:" + REGEX_UNKNOWN
           + ")|(?:" + REGEX_NUM_BORDERLINE
           + ")|(?:" + REGEX_NOT_ASSESSED + ")";

   static private final String REGEX_0_9
         = "[0-9]|zero|one|two|three|four|five|six|seven|eight|nine";

   static private final String REGEX_NUMTEEN
         = "(?:1[0-9])|ten|eleven|twelve|thirteen|fourteen|fifteen|sixteen|seventeen|eighteen|nineteen";
   static private final String REGEX_0_19 = REGEX_0_9 + "|" + REGEX_NUMTEEN;

   static private final String REGEX_NUMTY
         = "(?:[2-9]0)|twenty|thirty|forty|fifty|sixty|seventy|eighty|ninety";
   static private final String REGEX_0_99
         = "(?:" + REGEX_0_19 + ")|(?:(?:" + REGEX_NUMTY + ")(?: ?-? ?" + REGEX_0_9 + ")?)";

   static private final String REGEX_HUNDREDS
         = "(?:[1-9]00)|(?:(?:" + REGEX_0_9 + " ?-? )?hundred)";
   static private final String REGEX_0_999
         = "(?:" + REGEX_0_99 + ")|(?:" + REGEX_HUNDREDS + ")(: ?-? ?" + REGEX_0_99 + ")?)";

   static private final String REGEX_DECIMAL = "\\.[0-9]{1,4}";



   private enum Biomarker {

      ER_( "EstrogenReceptorStatus", "Estrogen Receptor Status", TEST_RESULT,
            "(?:Estrogen|ER(?!B)A?(?:-IHC)?\\+?-?|ER:(\\s*DCIS)?(\\s*IS)?)",
           "",
           REGEX_POS_NEG_UNK_NA,
           true ),

      PR_( "ProgesteroneReceptorStatus", "Progesterone Receptor Status", TEST_RESULT,
            "(?:Progesterone|Pg?RA?(?:-IHC)?\\+?-?|PR:(\\s*DCIS)?(\\s*IS)?)",
           "",
           REGEX_POS_NEG_UNK_NA,
           true ),

      HER2( "HER2_sl_NeuStatus", "HER2/Neu Status", TEST_RESULT,
            "(?:HER-? ?2(?: ?\\/?-? ?neu)?(?:-IHC)?\\+?-?(?:\\s*ONCOGENE)?(?:\\s*\\(?ERBB2\\)?)?)",
            "",
            REGEX_POS_NEG_UNK_NA_NUM ),

      KI67( "AntigenKI_sub_67", "Antigen KI-67", GENE_PRODUCT,
            "M?KI ?-? ?67(?: Antigen)?",
            "",
            "(?:>|< ?)?[0-9]{1,2}(?:\\.[0-9]{1,2} ?)? ?%(?: positive)?",
            true ),

      BRCA1( "BreastCancerType1SusceptibilityProtein", "Breast Cancer Type 1 Susceptibility Protein",
            GENE_PRODUCT,
            "(?:BRCA1|BROVCA1|(?:Breast Cancer Type 1))"
                  + "(?: Susceptibility)?(?: Gene)?(?: Polymorphism)?",
            "",
            REGEX_POS_NEG_UNK_NA ),

      BRCA2( "BreastCancerType2SusceptibilityProtein", "Breast Cancer Type 2 Susceptibility Protein",
            GENE_PRODUCT,
            "(?:BRCA2|BROVCA2|FANCD1|(?:Breast Cancer Type 2))"
                  + "(?: Susceptibility)?(?: Gene)?(?: Polymorphism)?",
            "",
            REGEX_POS_NEG_UNK_NA ),

      PIK3CA( "Phosphatidylinositol4_cma_5_sub_Bisphosphate3_sub_KinaseCatalyticSubunitAlphaIsoform",
            "Phosphatidylinositol 4,5-Bisphosphate 3-Kinase Catalytic Subunit Alpha Isoform",
            GENE_PRODUCT,
            "(?:PIK3CA|PI3K|p110|PI3 ?-? ?Kinase)", " ?-?(?: Subunit)?(?: Alpha)?",
            REGEX_POS_NEG_UNK_NA, true ),

      TP53( "CellularTumorAntigenP53",
            "Cellular Tumor Antigen p53",
            GENE_PRODUCT,
            "(?:TP53|p53|(?:protein 53)|(?:Phosphoprotein p53))",
            "(?: protein)?(?: tumor suppressor)?",
            REGEX_POS_NEG_UNK_NA, true ),

      ALK( "ALKTyrosineKinaseReceptor", "ALK Tyrosine Kinase Receptor", GENE_PRODUCT,
            "(?:ALK\\+?-?|CD246\\+?-?|(?:Anaplastic Lymphoma (?:Receptor Tyrosine )?Kinase))"
                  + "(?: Fusion)?(?: Gene|Oncogene)?(?: Alteration)?",
            "",
            "(?:" + REGEX_POS_NEG_UNK_NA + ")|(?:no rearrangement)",

            true ),

      EGFR( "EpidermalGrowthFactorReceptor", "Epidermal Growth Factor Receptor", GENE_PRODUCT,
            "EGFR\\+?-?|HER1\\+?-?|ERBB\\+?-?|C-ERBB1\\+?-?|(?:Epidermal Growth Factor)(?: Receptor)?",
            "",
            "(?:" + REGEX_POS_NEG_UNK_NA + ")|(?:not mutant)|(?:no mutations?)|",
            true ),

      BRAF( "Serine_sl_Threonine_sub_ProteinKinaseB_sub_Raf", "Serine/Threonine-Protein Kinase B-Raf",
            GENE_PRODUCT,
            "(?:Serine\\/Threonine-Protein Kinase )?B-?RAF1?(?: Fusion)?",
            "",
            REGEX_POS_NEG_UNK_NA ),

      ROS1( "Proto_sub_OncogeneTyrosine_sub_ProteinKinaseROS", "Proto-Oncogene Tyrosine-Protein Kinase ROS",
            GENE_PRODUCT,
            "(?:Proto-Oncogene )?(?:ROS1\\+?-?|MCF3\\+?-?|C-ROS-1\\+?-?"
            + "|(?:ROS Proto-Oncogene 1)"
            + "|(?:Tyrosine-Protein Kinase ROS)"
            + "|(?:Receptor Tyrosine Kinase c-ROS Oncogene 1))"
            + "(?: Gene)?(?: Fusion|Alteration|Rearrangement)?",
            "",
            REGEX_POS_NEG_UNK_NA,
            true ),

      PDL1( "ProgrammedCellDeathProtein1", "Programmed Cell Death Protein 1", GENE_PRODUCT,
            "(?:PDL1|PD-L1|CD247|B7|B7-H|B7H1|PDCD1L1|PDCD1LG1|(?:Programmed Cell Death 1 Ligand 1))"
            + "(?: Antigen)?(?: Molecule)?",
            "",
            "[0-9]{1,2} ?%(?: high expression)?" ),

      MSI( "MicrosatelliteStable", "Microsatellite Stable", TEST_RESULT,
            "MSI|MSS|Microsatellite",
           "",
           "stable" ),

      KRAS( "GTPaseKRas", "GTPase KRas", GENE_PRODUCT,
            "(?:KRAS\\+?-?|C-K-RAS\\+?-?|KRAS2\\+?-?|KRAS-2\\+?-?|V-KI-RAS2\\+?-?|(?:Kirsten Rat Sarcoma Viral Oncogene Homolog))"
            + "(?: Wild ?-?type|wt)?(?: Gene Mutation)?",
            "",
            "(?:" + REGEX_POS_NEG_UNK_NA +")|(?:not mutant)|(?:no mutations?)|",
            true ),
      //  GTPaseNras  'GTPase Nras'    'Gene Product'

      //  Prostate_sub_SpecificAntigen  'Prostate-Specific Antigen'    'Gene Product'
      PSA( "Prostate_sub_SpecificAntigen", "Prostate-Specific Antigen", GENE_PRODUCT,
            "PSA(?: Prostate Specific Antigen)?|(?:Prostate Specific Antigen(?: [PSA])?)",
           "",
           "[0-9]{1,2}\\.[0-9]{1,4}" ),

     PSA_EL( "Prostate_sub_SpecificAntigenEl", "Prostate-Specific Antigen", GENE_PRODUCT,
           "PSA(?: Prostate Specific Antigen)?|(?:Prostate Specific Antigen(?: [PSA])?)",
          "",
             "(?:" + REGEX_ELEVATED + ")|(?:" + REGEX_POSITIVE + ")|(?:" + REGEX_NEGATIVE + ")",
             true );

      final String _uri;
      final String _prefText;
      final DpheGroup _group;
      final Pattern _typePattern;
      final int _windowSize;
      final boolean _checkSkip;
      final Pattern _skipPattern;
      final Pattern _valuePattern;
      final boolean _canPrecede;
      final boolean _plusMinus;
      static private final AnnotationCreator _annotationCreator = new DpheAnnotationCreator();

      Biomarker( final String uri, final String prefText, final DpheGroup group,
                 final String typeRegex, final String skipRegex, final String valueRegex ) {
         this( uri, prefText, group, typeRegex, 20, skipRegex, valueRegex, false );
      }
      Biomarker( final String uri, final String prefText, final DpheGroup group,
                 final String typeRegex, final String skipRegex, final String valueRegex,
                 final boolean canPrecede ) {
         this( uri, prefText, group, typeRegex, 20, skipRegex, valueRegex, canPrecede );
      }
      Biomarker( final String uri, final String prefText, final DpheGroup group,
                 final String typeRegex, final int windowSize, final String skipRegex,
                 final String valueRegex, final boolean canPrecede ) {
         _uri = uri;
         _prefText = prefText;
         _group = group;
         _typePattern = Pattern.compile( typeRegex, Pattern.CASE_INSENSITIVE );
         _windowSize = windowSize;
         if ( skipRegex.isEmpty() ) {
            _checkSkip = false;
            _skipPattern = null;
         } else {
            _checkSkip = true;
            _skipPattern = Pattern.compile( skipRegex, Pattern.CASE_INSENSITIVE );
         }
         _valuePattern = Pattern.compile( valueRegex, Pattern.CASE_INSENSITIVE );
         _canPrecede = canPrecede;
         _plusMinus = REGEX_POS_NEG_UNK.equals( valueRegex );
      }
   }

   final private class BiomarkerFound {
      final Biomarker _biomarker;
      final String _value;
      private BiomarkerFound( final Biomarker biomarker, final String value ) {
         _biomarker = biomarker;
         _value = value;
      }
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void process( final JCas jCas ) throws AnalysisEngineProcessException {
      LOGGER.info( "Finding Biomarkers and Values ..." );

      findBiomarkers( jCas );

   }


   static public void findBiomarkers( final JCas jCas ) {
      final String docText = jCas.getDocumentText();
      final Collection<Integer> annotationBegins = JCasUtil.select( jCas, IdentifiedAnnotation.class )
                                                           .stream()
                                                           .filter( a -> ( a instanceof EventMention
                                                                           || a instanceof AnatomicalSiteMention ) )
                                                           .map( IdentifiedAnnotation::getBegin )
                                                           .collect( Collectors.toList() );
      final Collection<Pair<Integer>> sentenceSpans
            = JCasUtil.select( jCas, Sentence.class )
                      .stream()
                      .map( s -> new Pair<>( s.getBegin(), s.getEnd() ) )
                      .collect( Collectors.toList() );
      for ( Biomarker biomarker : Biomarker.values() ) {
         final List<Pair<Integer>> biomarkerSpans = findBiomarkerSpans( biomarker, docText );
         addBiomarkers( jCas, biomarker, docText, biomarkerSpans, sentenceSpans, annotationBegins );
      }
   }


   static private List<Pair<Integer>> findBiomarkerSpans( final Biomarker biomarker, final String text ) {
      try ( RegexSpanFinder finder = new RegexSpanFinder( biomarker._typePattern ) ) {
         return finder.findSpans( text )
                      .stream()
                      .filter( s -> isWholeWord( text, s ) )
                      .collect( Collectors.toList() );
      } catch ( IllegalArgumentException iaE ) {
         LOGGER.warn( iaE.getMessage() );
         return Collections.emptyList();
      }
   }

   // Why the heck don't word boundaries ever work in java?!
   static private boolean isWholeWord( final String text, final Pair<Integer> span ) {
      return isWholeWord( text, span.getValue1(), span.getValue2() );
   }

   // Why the heck don't word boundaries ever work in java?!
   static private boolean isWholeWord( final String text, final int begin, final int end ) {
      if ( begin > 0 ) {
         if ( Character.isLetterOrDigit( text.charAt( begin-1 ) ) ) {
            return false;
         }
      }
      if ( end == text.length() ) {
         return true;
      }
      return !Character.isLetterOrDigit( text.charAt( end ) );
   }


   static private void addBiomarkers( final JCas jCas,
                                      final Biomarker biomarker,
                                       final String text,
                                       final List<Pair<Integer>> biomarkerSpans,
                                      final Collection<Pair<Integer>> sentenceSpans,
                                      final Collection<Integer> annotationBegins ) {
      if ( biomarkerSpans.isEmpty() ) {
         return;
      }
      for ( Pair<Integer> biomarkerSpan : biomarkerSpans ) {
         addBiomarker( jCas, biomarker, text, biomarkerSpan, sentenceSpans, annotationBegins );
      }
   }


   static private void addBiomarker( final JCas jCas,
                                      final Biomarker biomarker,
                                      final String text,
                                      final Pair<Integer> biomarkerSpan,
                                     final Collection<Pair<Integer>> sentenceSpans,
                                     final Collection<Integer> annotationBegins ) {
//      LOGGER.info( "Adding Biomarker " + biomarker.name() );
      final Pair<Integer> sentenceSpan = getSentenceSpan( biomarkerSpan, sentenceSpans );
      final int followingAnnotation = getFollowingAnnotation( biomarkerSpan, text.length(), annotationBegins );
      if ( addBioMarkerFollowed( jCas, biomarker, text, biomarkerSpan, sentenceSpan, followingAnnotation ) ) {
         return;
      }
      if ( biomarker._canPrecede ) {
         final int precedingAnnotation = getPrecedingAnnotation( biomarkerSpan, annotationBegins );
         addBioMarkerPreceded( jCas, biomarker, text, biomarkerSpan, sentenceSpan, precedingAnnotation );
      }
   }

   static private boolean addBioMarkerFollowed( final JCas jCas,
                                                final Biomarker biomarker,
                                                final String text,
                                                final Pair<Integer> biomarkerSpan,
                                                final Pair<Integer> sentenceSpan,
                                                final int followingAnnotation ) {
//      LOGGER.info( "Add Biomarker Followed " + biomarker.name() );
      if ( biomarker._plusMinus ) {
         final char c = text.charAt( biomarkerSpan.getValue2()-1 );
         if ( (c == '+' || c == '-') && isWholeWord( text, biomarkerSpan ) ) {
//            addBiomarker( jCas, biomarker, biomarkerSpan.getValue1(), biomarkerSpan.getValue2() );
            addBiomarker( jCas, biomarker, biomarkerSpan.getValue1(), biomarkerSpan.getValue2(),
                  text.substring( biomarkerSpan.getValue1(), biomarkerSpan.getValue2() ) );
            return true;
         }
      }

      final String nextText = getFollowingText( biomarker, biomarkerSpan, text, sentenceSpan, followingAnnotation );
      if ( nextText.isEmpty() ) {
         return false;
      }
      if ( biomarker._checkSkip ) {
         final Matcher skipMatcher = biomarker._skipPattern.matcher( nextText );
         if ( skipMatcher.find() ) {
            return false;
         }
      }
      final Matcher matcher = biomarker._valuePattern.matcher( nextText );
      if ( matcher.find() ) {
         final int matchBegin = biomarkerSpan.getValue2() + matcher.start();
         final int matchEnd = biomarkerSpan.getValue2() + matcher.end();
         if ( isWholeWord( text, matchBegin, matchEnd ) ) {
//            addBiomarker( jCas, biomarker, matchBegin, matchEnd );
            addBiomarker( jCas, biomarker, matchBegin, matchEnd, text.substring( matchBegin, matchEnd ) );
            return true;
         }
      }
      return false;
   }

   static private boolean addBioMarkerPreceded( final JCas jCas,
                                                final Biomarker biomarker,
                                                final String text,
                                                final Pair<Integer> biomarkerSpan,
                                                final Pair<Integer> sentenceSpan,
                                                final int precedingAnnotation ) {
//      LOGGER.info( "Add Biomarker Followed " + biomarker.name() );
      if ( !biomarker._canPrecede ) {
         return false;
      }
      final String prevText = getPrecedingText( biomarker, biomarkerSpan, text, sentenceSpan, precedingAnnotation );
      if ( prevText.isEmpty() ) {
         return false;
      }
      final Matcher matcher = biomarker._valuePattern.matcher( prevText );
      Pair<Integer> lastMatch = null;
      while ( matcher.find() ) {
         lastMatch = new Pair<>( matcher.start(), matcher.end() );
      }
      if ( lastMatch == null ) {
         return false;
      }
      final int matchBegin = biomarkerSpan.getValue1() - prevText.length() + lastMatch.getValue1();
      final int matchEnd = biomarkerSpan.getValue1() - prevText.length() + lastMatch.getValue2();
      if ( isWholeWord( text, matchBegin, matchEnd ) ) {
//         addBiomarker( jCas, biomarker, matchBegin, matchEnd );
         addBiomarker( jCas, biomarker, matchBegin, matchEnd, text.substring( matchBegin, matchEnd ) );
         return true;
      }
      return false;
   }

//   static private void addBiomarker( final JCas jCas,
//                                      final Biomarker biomarker,
//                                        final int begin, final int end ) {
////      LOGGER.info( "Adding Biomarker " + biomarker.name() + " "
////                   + jCas.getDocumentText().substring( valueSpanBegin,
////                                                       valueSpanEnd ) );
//      AnnotationFactory.createAnnotation( jCas, begin, end, DpheGroup.FINDING, biomarker.name(), "", biomarker.name() );
//      // Uses name as URI, value as span.
////      UriAnnotationFactory.createIdentifiedAnnotations( jCas,
////                                                        valueSpanBegin,
////                                                        valueSpanEnd,
////                                                        biomarker.name(),
////                                                        SemanticGroup.FINDING,
////                                                      "T033" );
//   }

   static private void addBiomarker( final JCas jCas, final Biomarker biomarker,
                                     final int begin, final int end, final String value ) {
      AnnotationFactory.createAnnotation( jCas, begin, end, biomarker._group, biomarker._uri, "",
            biomarker._prefText, value );
   }


//         final String cui;
//      final String prefText;
//      final GraphDatabaseService graphDb = EmbeddedConnection.getInstance().getGraph();
//      try ( Transaction tx = graphDb.beginTx() ) {
//         final Node graphNode = SearchUtil.getClassNode( graphDb, uri );
//         if ( graphNode == null ) {
////            LOGGER.warn( "No Class exists for URI " + uri );
//            return Collections.singletonList( createUnknownAnnotation( jcas, beginOffset, endOffset, uri ,
//                                                                       semanticTui ) );
//         }
//         cui = (String)graphNode.getProperty( CUI_KEY );
//         prefText = (String)graphNode.getProperty( PREF_TEXT_KEY );
//         tx.success();
//      } catch ( MultipleFoundException mfE ) {
//         LOGGER.error( mfE.getMessage(), mfE );
//         return Collections.singletonList( createUnknownAnnotation( jcas, beginOffset, endOffset, uri, semanticTui ) );
//      }

   static private Pair<Integer> getSentenceSpan( final Pair<Integer> biomarkerSpan,
                                                 final Collection<Pair<Integer>> sentenceSpans ) {
      return sentenceSpans.stream()
                         .filter( s -> s.getValue1() <= biomarkerSpan.getValue1()
                                       && biomarkerSpan.getValue2() <= s.getValue2() )
                         .findFirst()
                         .orElse( biomarkerSpan );
   }

   static private int getPrecedingAnnotation( final Pair<Integer> biomarkerSpan,
                                               final Collection<Integer> annotationBegins ) {
      return annotationBegins.stream()
                          .filter( b -> b < biomarkerSpan.getValue1() )
                             .mapToInt( b -> b )
                          .max()
                          .orElse( 0 );
   }

   static private int getFollowingAnnotation( final Pair<Integer> biomarkerSpan,
                                              final int textLength,
                                              final Collection<Integer> annotationBegins ) {
      return annotationBegins.stream()
                             .filter( b -> b >= biomarkerSpan.getValue2() )
                             .mapToInt( b -> b )
                             .min()
                             .orElse( textLength );
   }

   static private String getPrecedingText( final Biomarker biomarker,
                                           final Pair<Integer> biomarkerSpan,
                                           final String text,
                                           final Pair<Integer> sentenceSpan,
                                           final int precedingAnnotation ) {
      final int sentenceOrAnnotation = Math.max( precedingAnnotation, sentenceSpan.getValue1() );
//      final int windowSize = Math.max( 0, biomarkerSpan.getValue1() - biomarker._windowSize );
      final String prevText = text.substring( sentenceOrAnnotation, biomarkerSpan.getValue1() );
      // Check for end of paragraph
      final int pIndex = prevText.lastIndexOf( "\n\n" );
      if ( pIndex >= 0 ) {
         return prevText.substring( pIndex+2 );
      }
      return prevText;
   }


   static private String getFollowingText( final Biomarker biomarker,
                                            final Pair<Integer> biomarkerSpan,
                                            final String text,
                                           final Pair<Integer> sentenceSpan,
                                           final int followingAnnotation ) {
      final int sentenceOrAnnotation = Math.min( followingAnnotation, sentenceSpan.getValue2() );
      String nextText = text.substring( biomarkerSpan.getValue2(), sentenceOrAnnotation );
//      final int windowSize = Math.min( text.length(), biomarkerSpan.getValue2() + biomarker._windowSize );
//      String nextText = text.substring( biomarkerSpan.getValue2(), windowSize );
      // Check for end of paragraph
      final int pIndex = nextText.indexOf( "\n\n" );
      if ( pIndex == 0 ) {
         return "";
      }
      // Sometimes value sets are in brackets.  e.g.  "ER: [pos;neg;unk] = neg"
      final int startBracket = nextText.indexOf( '[' );
      if ( startBracket >= 0 ) {
         final int endBracket = nextText.indexOf( ']', startBracket );
         if ( endBracket > 0 ) {
            final char[] chars = nextText.toCharArray();
            for ( int i=startBracket+1; i<endBracket; i++ ) {
               chars[ i ] = 'V';
            }
            nextText = new String( chars );
         }
      }

      if ( pIndex > 0 ) {
         return nextText.substring( 0, pIndex );
      }
      return nextText;
   }



}
