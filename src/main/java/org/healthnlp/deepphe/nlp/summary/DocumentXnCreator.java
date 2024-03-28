package org.healthnlp.deepphe.nlp.summary;

import org.apache.ctakes.core.util.IdCounter;
import org.apache.ctakes.core.util.doc.NoteSpecs;
import org.apache.ctakes.typesystem.type.textspan.Episode;
import org.apache.ctakes.typesystem.type.textspan.Segment;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.healthnlp.deepphe.neo4j.node.Section;
import org.healthnlp.deepphe.neo4j.node.xn.DocumentXn;
import org.healthnlp.deepphe.nlp.util.IdCreator;
import org.neo4j.kernel.impl.store.id.IdContainer;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author SPF , chip-nlp
 * @since {10/20/2023}
 * id = fileName _ docText.hashCode
 */
final public class DocumentXnCreator {

    private DocumentXnCreator() {}

    static private final IdCounter ID_COUNTER = new IdCounter();
    static private final IdCounter SECTION_COUNTER = new IdCounter();

    public static final String NO_CATEGORY = "unknown";

    static public void resetCounter() {
        ID_COUNTER.reset();
        SECTION_COUNTER.reset();
    }

    static public DocumentXn createDocumentXn( final JCas jCas, final String patientId, final String patientTime ) {
        final DocumentXn doc = new DocumentXn();
        final NoteSpecs noteSpecs = new NoteSpecs( jCas );
        final String docId = noteSpecs.getDocumentId();
        final String fullDocId = IdCreator.createId( patientId, patientTime, "D", ID_COUNTER );
        doc.setId( fullDocId );
        doc.setName( docId );
        doc.setDate( noteSpecs.getNoteTime() );
        doc.setType( getDocType( noteSpecs.getDocumentType() ) );
        doc.setText( noteSpecs.getDocumentText() );
        final String episodeType = JCasUtil.select( jCas, Episode.class ).stream()
                .map( Episode::getEpisodeType )
                .distinct()
                .sorted()
                .findFirst()
                .orElse( NO_CATEGORY );
        doc.setEpisode( episodeType );

        final List<Section> sectionList
                = createSectionList( JCasUtil.select( jCas, Segment.class ), patientId, patientTime );
        doc.setSections( sectionList );
        return doc;
    }

    static private String getDocType( final String docType ) {
        switch ( docType ) {
            case "RAD":
                return "Radiology Report";
            case "PATH":
                return "Pathology Report";
            case "SP":
                return "Surgical Pathology Report";
            case "DS":
                return "Discharge Summary";
            case "PGN":
                return "Progress Note";
            case "NOTE":
                return "Clinical Note";
            case NoteSpecs.ID_NAME_CLINICAL_NOTE:
                return "Clinical Note";
        }
        return docType.replace( '_', ' ' );
    }


    static private List<Section> createSectionList( final Collection<Segment> segments, final String patientId,
                                                    final String patientTime ) {
        return segments.stream()
                .map( s -> DocumentXnCreator.createSection( s, patientId, patientTime ) )
                .sorted( Comparator.comparingInt( Section::getBegin ) )
                .collect( Collectors.toList() );
    }

    static private Section createSection( final Segment segment, final String patientId, final String patientTime ) {
        final Section section = new Section();
        final String sectionId = IdCreator.createId( patientId, patientTime, "S", SECTION_COUNTER );
        section.setId( sectionId );
        section.setType( segment.getPreferredText() );
        section.setBegin( segment.getBegin() );
        section.setEnd( segment.getEnd() );
        return section;
    }


}
