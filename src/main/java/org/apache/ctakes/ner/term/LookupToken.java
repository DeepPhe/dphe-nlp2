package org.apache.ctakes.ner.term;


import org.apache.ctakes.core.util.Pair;
import org.apache.ctakes.typesystem.type.syntax.BaseToken;


/**
 * @author SPF , chip-nlp
 * @version %I%
 * @since 8/13/2020
 */
final public class LookupToken {

   final private Pair<Integer> _textSpan;
   final private String _text;
   final private String _pos;
   final private boolean _isValidIndexToken;

   public LookupToken( final BaseToken baseToken, final boolean isValidIndexToken ) {
      _textSpan = new Pair<>( baseToken.getBegin(), baseToken.getEnd() );
      _text = baseToken.getCoveredText();
      _pos = baseToken.getPartOfSpeech();
      _isValidIndexToken = isValidIndexToken;
   }

   /**
    * @return a span with the start and end indices used for this lookup token
    */
   public Pair<Integer> getTextSpan() {
      return _textSpan;
   }

   /**
    * @return the start index used for this lookup token
    */
   public int getBegin() {
      return _textSpan.getValue1();
   }

   /**
    * @return the end index used for this lookup token
    */
   public int getEnd() {
      return _textSpan.getValue2();
   }

   /**
    * @return the length of the text span in characters
    */
   public int getLength() {
      return _text.length();
   }

   /**
    * @return the lowercase text in the document for the lookup token, regardless of case.
    */
   public String getText() {
      return _text;
   }

   public String getPOS() {
      return _pos;
   }

   public boolean isValidIndexToken() {
      return _isValidIndexToken;
   }

   /**
    * Two lookup tokens are equal iff the spans are equal.
    *
    * @param value -
    * @return true if {@code value} is a {@code FastLookupToken} and has a span equal to this token's span
    */
   public boolean equals( final Object value ) {
      return value instanceof LookupToken
             && _textSpan.equals( ((LookupToken)value).getTextSpan() );
   }

   /**
    * @return hashCode created from the Span
    */
   public int hashCode() {
      return _textSpan.hashCode();
   }

}
