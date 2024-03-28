package org.healthnlp.deepphe.nlp.attribute.biomarker;

import org.apache.ctakes.core.util.StringUtil;
import org.healthnlp.deepphe.neo4j.node.Mention;
import org.healthnlp.deepphe.neo4j.node.MentionRelation;
import org.healthnlp.deepphe.nlp.attribute.xn.DefaultXnAttributeNormalizer;

import org.healthnlp.deepphe.nlp.concept.UriConcept;
import org.healthnlp.deepphe.nlp.concept.UriConceptRelation;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;


/**
 * @author SPF , chip-nlp
 * @since {3/23/2023}
 */
public class BiomarkerNormalizer extends DefaultXnAttributeNormalizer {

   @Override
   public String getNormalValue( final UriConcept concept ) {
//      final Collection<String> texts = concept.getCodifications().getOrDefault( DPHE_VALUE_SCHEME, Collections.emptyList() );
//      return texts.stream().map( NORMAL::getNormal ).distinct().collect( Collectors.joining( ";" ) );
      final String value = concept.getValue();
      final Collection<String> normals = Arrays.stream( StringUtil.fastSplit( value, ';' ) )
                                               .map( NORMAL::getNormal )
                                               .filter( n -> !n.isEmpty() )
                                               .collect( Collectors.toSet() );
      if ( normals.size() > 1 ) {
         normals.remove( "Unknown" );
      }
      if ( normals.size() > 1 ) {
         normals.remove( "Not Assessed" );
      }
      if ( normals.size() > 1 ) {
         normals.remove( "applicable" );
      }
      return String.join( ";", normals );
   }


//   public String getBestCode( final Collection<CrConceptAggregate> aggregates ) {
//      if ( aggregates.isEmpty() ) {
//         return "";
//      }
////      NeoplasmSummaryCreator.addDebug( "BiomarkerNormalizer Aggregates "
////                                       + aggregates.stream()
////                                                   .map( CrConceptAggregate::getCoveredText )
////                                                   .collect( Collectors.joining(" , ") ) + "\n" );
//      // Map of covered text to the number of times that
//      final ConfidenceGroup<CrConceptAggregate> confidenceGroup = new ConfidenceGroup<>( aggregates );
//      final Map<String,Long> countMap = confidenceGroup.getBest()
//                                                           .stream()
//                                                            .map( this::getCodes )
//                                                           .flatMap( Collection::stream )
//                                                            .collect( Collectors.groupingBy( Function.identity(),
//                                                                                    Collectors.counting() ) );
//      final Collection<String> bestCodes = new HashSet<>();
//      long bestCodesCount = 0;
//      long totalCount = 0;
//      for ( Map.Entry<String,Long> codeCount : countMap.entrySet() ) {
//         final long count = codeCount.getValue();
//         if ( count > bestCodesCount ) {
//            bestCodes.clear();
//            bestCodes.add( codeCount.getKey() );
//            bestCodesCount = count;
//         } else if ( count == bestCodesCount ) {
//            bestCodes.add( codeCount.getKey() );
//            bestCodesCount += count;
//         }
//         totalCount += count;
//      }
//      setBestCodesCount( (int)bestCodesCount );
//      setAllCodesCount( (int)totalCount );
//      setUniqueCodeCount( countMap.size() );
//      NeoplasmSummaryCreator.addDebug( "BiomarkerNormalizer "
//                                       + countMap.entrySet().stream()
//                                                 .map( e -> e.getKey() + ":" + e.getValue() )
//                                                 .collect( Collectors.joining(",") ) + " = "
//                                       + String.join( ";", bestCodes ) +"\n");
//      return String.join( ";", bestCodes );
//   }


   public Map<String,Collection<MentionRelation>> createTextCodeMentionsMap(
         final Collection<UriConceptRelation> relations ) {
      final Map<String,Collection<MentionRelation>> map = new HashMap<>();
      for ( UriConceptRelation relation : relations ) {
         final UriConcept target = relation.getTarget();
         final Map<String, Mention> idMentions = target.getMentions()
                                                       .stream()
                                                       .collect(
                                                             Collectors.toMap( Mention::getId, Function.identity() ) );
         final Collection<MentionRelation> mentionRelations = relation.getMentionRelations();
         for ( MentionRelation mentionRelation : mentionRelations ) {
            final Mention mention = idMentions.get( mentionRelation.getTargetId() );
            if ( mention != null ) {
               final String code = getTextCode( mention );
               if ( !code.isEmpty() ) {
                  map.computeIfAbsent( code, c -> new HashSet<>() ).add( mentionRelation );
               }
            }
         }
      }
      return map;
   }


//   public String getTextCode( final CrConceptAggregate aggregate ) {
//      return String.join( ";", getCodes( aggregate ) );
//   }

   public String getTextCode( final Mention mention ) {
      // TODO - get from PatientCasStore, which means we need to have the patient id here.
//      final Note note = NoteNodeStore.getInstance().get( mention.getNoteId() );
//      if ( note == null ) {
         return "";
//      }
//      return NORMAL.getNormal( note.getText().substring( mention.getBegin(), mention.getEnd() ).toLowerCase() );
   }

//   public List<String> getCodes( final CrConceptAggregate aggregate ) {
//      final String text = aggregate.getCoveredText()
//                                   .replace( '[', ' ' )
//                                   .replace( ']', ' ' )
//                                   .trim()
//                                   .toLowerCase();
//      return Arrays.stream( StringUtil.fastSplit( text, ',' ) )
//                   .map( String::trim )
//                    .map( NORMAL::getNormal )
//                   .distinct()
//                   .filter( t -> !t.isEmpty() )
//                   .collect( Collectors.toList() );
//   }

   private enum NORMAL {
      Positive( "+", "pos", "positive", "positivity", "express", "overexpression", "3+" ),
      Negative( "-", "neg", "negative", "unamplified", "not amplified", "non detected", "non-detected",
                "not express", "0", "1+" ),
      Elevated( "rising", "increasing", "elevated", "elvtd", "raised", "increased", "strong", "amplified" ),
      Unknown( "unknown", "indeterminate" ),
      Equivocal( "equivocal", "borderline", "2+" ),
      Not_Assessed( "not assessed", "not requested", "not applicable", "insufficient", "pending", "n/a" );

      private final Collection<String> _text;
      NORMAL( final String ... text ) {
         _text = new HashSet<>( Arrays.asList( text ) );
      }
      static private String getNormal( final String text ) {
         if ( text.isEmpty() ) {
            return "";
         }
         for ( NORMAL normal : values() ) {
            if ( normal._text.contains( text ) ) {
               return normal.name().replace( '_', ' ' );
            }
         }
         if ( text.startsWith( "no " ) && text.endsWith( " detected" ) ) {
            return Negative.name();
         }
         return text;
      }
   }


}
