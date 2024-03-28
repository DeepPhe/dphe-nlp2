package org.healthnlp.deepphe.nlp.patient;

import org.apache.ctakes.core.patient.PatientNoteStore;
import org.apache.log4j.Logger;
import org.apache.uima.cas.CAS;
import org.apache.uima.cas.CASException;
import org.apache.uima.jcas.JCas;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * @author SPF , chip-nlp
 * @since {9/20/2023}
 */
final public class PatientCasUtil {

   static private final Logger LOGGER = Logger.getLogger( "PatientCasUtil" );

   private PatientCasUtil() {}


   static public List<JCas> getDocCases( final String patientId ) {
      final JCas patient = PatientCasStore.getInstance().get( patientId );
      if ( patient == null ) {
         LOGGER.warn( "No Cas stored for patient " + patientId );
         return Collections.emptyList();
      }
      final List<JCas> docs = new ArrayList<>();
      try {
         for ( Iterator<JCas> it = patient.getViewIterator(); it.hasNext(); ) {
            final JCas view = it.next();
            if ( view.getViewName().equals( CAS.NAME_DEFAULT_SOFA ) ) {
               continue;
            }
            docs.add( view );
         }
      } catch ( CASException casE ) {
         LOGGER.error( casE.getMessage() );
         // This isn't a great return, but false may go on forever.
      }
      return Collections.unmodifiableList( docs );
   }

   static public boolean isPatientFull( final String patientId ) {
      // Even though the ctakes PatientNoteStore isn't being used to store any patient jcas,
      // it still has note counts as they should be set by a collection reader.
      final int patientDocCount = PatientNoteStore.getInstance()
                                                  .getWantedDocCount( patientId );
      return getDocCases( patientId ).size() >= patientDocCount;
   }
}