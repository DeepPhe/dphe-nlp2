package org.healthnlp.deepphe.nlp.util;

import org.apache.ctakes.core.util.StringUtil;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * @author SPF , chip-nlp
 * @since {4/11/2024}
 */
final public class VumcPatientSorter {

   //  R200011990-19960826-AP-Surgical_Pathology-1-234587023459782349.txt
   //  R200011990-19960826-HP-OPERATIVE_REPORT-1-707234572345897203.txt

   //  PatientID-YYYYMMDD-Source-Doc_Type-1-EncounterNum??.txt
   //  !!!!  Sometimes Date also has time?:  "20160121_101600"
   //  !!!!  Sometimes files have the same encounterNum, but a different preceding number (index?)
   //  R200011990-19960826-HP-OPERATIVE_REPORT-1-707234572345897203.txt
   //  R200011990-19960826-HP-OPERATIVE_REPORT-2-707234572345897203.txt
   //  R200011990-19960826-HP-OPERATIVE_REPORT-3-707234572345897203.txt
   //  !!!!  There are some problematic Doc_Types:
   //  !!!!  Physician_Provider_SIGNED_orders_-__strong_Cancer_Chemotherapy__strong__[All_Orders_Unsigned]
   //  !!!!  ...Pre-Admission...
   //  !!!!  PID-Date-Source-MyHealthTeam@Vanderbilt_Plan_of_Care-1-EncounterNum.txt   always garbage!  Just a PID?
   //  !!  "-Braden_Assessment-" useless.
   //  !!!!  Some F'ups like ending with "1--345782378562385.txt"    (multiple dash)
   //  !!!!  Some Spaces:  PID-Date-Social History-Doc_Type-1-EncounterNum??.txt
   //  !!!!  Just use positions 0 (PID), 1 (Date), 2 (Source), n-2 (index?), n-1 (encounterNum?)
   //  !!!!    If n-2 (index?) is empty, use n-3.  This can happen because of their F'up.
   //  !!!!    Replace spaces, '&', ',', '(', '[', etc. with underscores.  Their naming sucks.

   //  !!!!  Note texts have garbage like "1. javascript:top.vois_link_to_plan('ID_NUM','sp27');"
   //  !! De-ID:
   //  **ID-NUM             [ID***]
   //  **NAME[AAA,BBB]      **NAME[ZZZ]    **NAME[AAA's M]   **NAME[NNN, OOO M]  **NAME[ZZZ, YYY XXX]  **NAME[M&M]
   //  **DATE[Dec 16 62]    **DATE[Jul 01 2016]     **DATE[Sep 30 15]:30:00 CST 2016   **DATE[Sep 30 15]:30:00 CST 2016 03:30 PM
   //  **AGE[in 50s]
   //  **PHONE              **PHONEarno8fy         ***PHONE       **EMAIL
   //  **INSTITUTION        **INSTITUTION1211
   //  **PLACE              **ZIP-CODE             **ROOM      **STREET-ADDRESS

   //  !! Sometimes the above are in parentheses or braces:  (**NAME[zzz, yyy])  {**NAME[AAA]}
   //  !! There are valid multi-* terms:  ***Change:     ***Note:
   //  !! Replace:
   //  **ID-NUM , [ID***] : " ID123456789 "
   //  **NAME[AAA,BBB] : " Josephine Claudwell "
   //  **DATE[1 2 3] : " 1 2 3 "
   //  **AGE[in 50s] : " in 50s "
   //  **PHONE , ***PHONE , **EMAIL , **INSTITUTION , **PLACE , **Zip-CODE , **ROOM : " "

   //  !! There are useless docs, such as those with "(fuel_reimbursement_form)" in the filename.
   //  !! Check things like "-Nurse's_note-" "-port_draw-" "-PORT_DRAW-"
   //  "-Core_Measures-" "-Research_Lab_Draw-" "-Clinical_communication_(Renewal_Approved)-"\
   //  "-Provider_Communications-" "-parish_nurse_note-" "-Surgical_Teaching-"
   //  "-Dentistry_Return_Clinic_Visit-" "-Dentistry_Procedure_Note-" "-20LABS-"
   //  for valid information.
   //  "-Psychiatry_Progress_Note-" does sometimes have cancer in patient history.

   //  !!!!  A bunch of notes do not use period characters or even have a space at the end of many sentences!
   //  !!!!  "She has not vomited todayDiarrhea todayPt is still requesting MRI, chemo today. Also wants " ...
   //  !! Some notes have "<br>" codes.  Replace with newline.
   //  !! Some notes have long strings of "----------------" and "+---------------+" without spaces.  Replace with NL.
   //  !! Some notes have "|" and "||" (sometimes with dashes above) without spaces.  Replace with newline.
   //  !! Lots of notes have no space before or after parentheses '(' ')'.  Add space.


   static private int _badSplitCount = 0;
   //  PatientID-YYYYMMDD-Source-Doc_Type-Index-EncounterNum??.txt
   //  !!!!  Sometimes Date also has time?:  "20160121_101600"

   static private String[] splitFilename( final String oldFilename ) {
      final String[] splits = StringUtil.fastSplit( oldFilename, '-' );
      final int splitCount = splits.length;
      if ( splitCount != 6 ) {
         // Inappropriate number of splits.
         _badSplitCount++;
      }

      // Only return filled splits 0, 1, 4, 5
      final String[] wantedSplits = new String[ 4 ];
      wantedSplits[ 0 ] = splits[ 0 ];
      wantedSplits[ 1 ] = splits[ 1 ];
      wantedSplits[ 2 ] = splits[ splitCount - 2 ];
      wantedSplits[ 3 ] = splits[ splitCount - 1 ];
      return wantedSplits;
   }

   static private String getPatientId( final String[] splits ) {
      return splits[ 0 ];
   }

   static private String getDate( final String[] splits ) {
      final String dateTime = splits[ 1 ];
      final int timeSplit = dateTime.indexOf( '_' );
      if ( timeSplit < 8 ) {
         return dateTime;
      }
      return dateTime.substring( 0, 8 );
   }

   static private String getNewFilename( final String[] splits ) {
      // Sometimes no index is given.
      final String index = splits[ 2 ].isEmpty() ? "0" : splits[ 2 ];
      // Remove the .txt filename extension;
      final String encounter = splits[ 3 ].substring( 0, splits[ 3 ].length() - 4 );
      return encounter + "_" + index + ".txt";
   }


   static private void zipFiles( final File inputDir, final List<String> filenames,
                                 final String outputZipPath ) {
      try ( FileOutputStream outputStream = new FileOutputStream( outputZipPath );
            ZipOutputStream zipStream = new ZipOutputStream( outputStream ) ) {
         for ( String filename : filenames ) {
            final String[] splits = splitFilename( filename );
            final String patientId = getPatientId( splits );
            final String newFilename = getNewFilename( splits );
            final String zipEntryname = patientId + "/" + newFilename;
            final File fileToZip = new File( inputDir, filename );
            final FileInputStream inputStream = new FileInputStream( fileToZip );
            final ZipEntry zipEntry = new ZipEntry( zipEntryname );
            zipStream.putNextEntry( zipEntry );
            byte[] bytes = new byte[ 1024 ];
            int length;
            while ( (length = inputStream.read( bytes )) >= 0 ) {
               zipStream.write( bytes, 0, length );
            }
            inputStream.close();
         }
      } catch ( IOException ioE ) {
         System.err.println( ioE.getMessage() );
      }
   }

   public static void main( final String... args ) {
      if ( args.length < 3 ) {
         System.out.println( "Please enter inputPath , outputPath and zip filename as arguments." );
         System.exit( 0 );
      }
      final String inputPath = args[ 0 ];
      final String outputPath = args[ 1 ];
      final String zipFileName = args[ 2 ];
      final File inputDir = new File( inputPath );
      if ( !inputDir.isDirectory() ) {
         System.out.println( inputPath + " does not exist." );
         System.exit( 0 );
      }
      final File outputDir = new File( outputPath );
      outputDir.mkdirs();
      final List<String> filenames = Arrays.stream( Objects.requireNonNull( inputDir.listFiles() ) )
                                           .map( File::getName )
                                           .collect( Collectors.toList() );
      if ( filenames.isEmpty() ) {
         System.out.println( "No files in " + outputPath );
      }
      final String outputZipPath = new File( outputDir, zipFileName ).getAbsolutePath();
      zipFiles( inputDir, filenames, outputZipPath );
   }

}
