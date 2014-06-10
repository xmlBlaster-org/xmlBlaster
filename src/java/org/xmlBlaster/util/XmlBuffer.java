/*------------------------------------------------------------------------------
Name:      XmlBuffer.java
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.util;

import java.awt.event.KeyEvent;

import org.xmlBlaster.util.qos.MsgQosData;
import org.xmlBlaster.util.qos.MsgQosSaxFactory;

/**
 * Same as StringBuilder but has the additional method appendEscaped() which
 * escapes predefined XML identities.
 * @author mr@marcelruff.info
 */
public class XmlBuffer {
        private StringBuilder buf;
        public XmlBuffer(int len) {
                this.buf = new StringBuilder(len);
        }

    /**
     * Escape predefined xml entities (&, <, >, ', ").
     * Additionally the '\0' is escaped.
     * @param text
     * @return The escaped text is appended to the StringBuilder.
     */
        public XmlBuffer appendEscaped(String text) {
                append(this.buf, text);
                return this;
        }

        /**
         * Escape predefined xml entities (', ", \r) for attributes.
         * Additionally the '\0' is escaped.
         * @param text
         * @return The escaped text is appended to the StringBuilder.
         */
            public XmlBuffer appendAttributeEscaped(String text) {
                    appendAttr(this.buf, text);
                    return this;
            }

        /**
         * Appends a tag name (e.g. "bla" of a tag called <bla>).
         * Currently is a normal append()
         * @param tagName Could in future escape invalid tokens  '<' and '&' in a tag name.
         * @return
         */
        public XmlBuffer appendTag(String tagName) {
                this.buf.append(tagName);
                return this;
        }

        /**
         * Sorround string with CDATA
         * @param str
         * @return
         */
        public XmlBuffer appendCdataEscaped(String str) {
        	this.buf.append("<![CDATA[").append(str).append("]]>");
        	return this;
        }

        /**
         * Supports only "," -> "&comma;" and ";" -> "%3B" 
         * @param text "bla,bla"
         * @param separator ','
         * @return "bla&comma;bla"
         */
        public XmlBuffer appendCsvEscaped(String text, char separator) {
            if (text == null) return this;
            int length = text.length();
            for (int i = 0; i < length; i++) {
                char c = text.charAt(i);
                if (c == separator) {
	                switch (separator) {
	                    case ';':
	            			// ';' is escaped as %3B (as in URL) and HTML as &#59; (which unfortunately contains again a ;)
	                        buf.append("%3B"); // What about &semicolon;
	                        break;
	                    case ',':
                            buf.append("&comma;");
	                        break;
	                    default:
                            buf.append(c);
	                }
                }
                else {
                    buf.append(c);
                }
            }
        	return this;
        }

        /**
         * Aquivalent to a StringBuilder.append().
         */
        public XmlBuffer append(String str) {
        	try {
                this.buf.append(str);
                return this;
        	}
        	catch (java.lang.OutOfMemoryError e) {
        		// XmlBuffer.append size=41232896 extend with 1: java.lang.OutOfMemoryError: Java heap space
        		System.err.println("XmlBuffer.append size=" + this.buf.length() + " extend with " + str.length() + ": " + e.toString());
        		throw e;
        	}
        }

        /**
         * Aquivalent to a StringBuilder.append().
         */
        public XmlBuffer append(long ln) {
                this.buf.append(ln);
                return this;
        }

        /**
         * Aquivalent to a StringBuilder.append().
         */
        public XmlBuffer append(float ln) {
                this.buf.append(ln);
                return this;
        }

        /**
         * Aquivalent to a StringBuilder.append().
         */
        public XmlBuffer append(double ln) {
                this.buf.append(ln);
                return this;
        }

        /**
         * Aquivalent to a StringBuilder.append().
         */
        public XmlBuffer append(boolean b){
                this.buf.append(b);
                return this;
        }
        
        public StringBuilder getRawBuffer() {
        	return this.buf;
        }
        
        public int length() {
        	return this.buf.length();
        }
        
        /**
         * Removes all buffer entries.
         * Calling append fills new data to the beginning of the buffer. 
         */
        public void reset() {
        	this.buf.setLength(0);
        }

        public String toString() {
                return this.buf.toString();
        }

        // 5 predefined XML entities plus some extra escapers
    private static final char[] AMP = "&amp;".toCharArray();
    private static final char[] LT = "&lt;".toCharArray();
    private static final char[] GT = "&gt;".toCharArray();
    private static final char[] QUOT = "&quot;".toCharArray();
    private static final char[] APOS = "&apos;".toCharArray();

    private static final char[] SLASH_R = "&#x0D;".toCharArray();
    private static final char[] NULL = "&#x0;".toCharArray();

    /**
     * Escape predefined xml entities (&, <, >, ', ").
     * Additionally the '\0' is escaped.
     * @param text e.g. "Hello < and &"
     * @return "Hello &lt; and &amp;"
     */
    public static String escape(String text) {
        if (text == null || text.length() < 1)
                return text;
        StringBuilder buf = new StringBuilder((int)(text.length()*1.2));
        append(buf, text);
        return buf.toString();
    }

    /**
     * Escape predefined xml entities (&, <, >, ', ").
     * Additionally the '\0' is escaped.
     * @param text
     * @return The escaped text is appended to the given StringBuilder.
     */
    public final static void append(StringBuilder buf, String text) {
        if (text == null) return;
        int length = text.length();
        for (int i = 0; i < length; i++) {
            final char c = text.charAt(i);
            switch (c) {
                case '\0':
                    buf.append(NULL);
                    break;
                case '&':
                        buf.append(AMP);
                    break;
                case '<':
                        buf.append(LT);
                    break;
                case '>':
                        buf.append(GT);
                    break;
                case '"':
                        buf.append(QUOT);
                    break;
                case '\'':
                        buf.append(APOS);
                    break;
                case '\r':
                        buf.append(SLASH_R);
                    break;
                default:
                	if (isPrintableCharExtended(c)) {
                        buf.append(c);
                	}
                	else {
                    	boolean isControl = Character.isISOControl(c);
                    	boolean isUndefined = c == KeyEvent.CHAR_UNDEFINED;
                    	Character.UnicodeBlock block = Character.UnicodeBlock.of( c );
                    	boolean isSpecial = block != null &&
                    			block != Character.UnicodeBlock.SPECIALS;
                    	boolean isAsciiControl = (int)c < 20;
                		System.out.println("XmlBuffer.append caution: Dropping none UTF-8 character '" + c + "' isControl=" + isControl + " isUndefined=" + isUndefined + " isSpecial=" + isSpecial + " isAsciiControl=" + isAsciiControl);
                	}
            }
        }
    }
    
    /**
     * Detect strange characters
     * i=rk�9<?9,id=7807049
     * @param c
     * @return false for above or eg "\n"
     */
    public final static boolean isPrintableChar( char c ) {
    	Character.UnicodeBlock block = Character.UnicodeBlock.of( c );
    	// isIsoControl finds: 
    	// � is found by SPECIALS
    	return (!Character.isISOControl(c)) &&
    			c != KeyEvent.CHAR_UNDEFINED &&
    			block != null &&
    			block != Character.UnicodeBlock.SPECIALS;
    }

    /**
     * @param c
     * @return \n etc are valid
     */
    public final static boolean isPrintableCharExtended( char c ) {
    	Character.UnicodeBlock block = Character.UnicodeBlock.of( c );
    	// isIsoControl finds: 
    	// � is found by SPECIALS
    	return ((c == '\n' || c == '\t' || c == 0x0d )
    			|| !Character.isISOControl(c)) &&
    			c != KeyEvent.CHAR_UNDEFINED &&
    			block != null &&
    			block != Character.UnicodeBlock.SPECIALS;
    }

    /**
     * Escape predefined xml entities (\0, ', ", \r). for attribute notation
     * Additionally the '\0' is escaped.
     * @param text
     * @return The escaped text is appended to the given StringBuilder.
     */
    public static void appendAttr(StringBuilder buf, String text) {
        if (text == null) return;
        int length = text.length();
        for (int i = 0; i < length; i++) {
            char c = text.charAt(i);
            switch (c) {
                case '\0':
                    buf.append(NULL);
                    break;
                case '&':
                        buf.append(AMP);
                    break;
                case '<':
                        buf.append(LT);
                    break;
                /*
                case '>':
                        buf.append(GT);
                    break;
                */
                case '"':
                        buf.append(QUOT);
                    break;
                case '\'':
                        buf.append(APOS);
                    break;
                case '\r':
                        buf.append(SLASH_R);
                    break;
                default:
                        buf.append(c);
            }
        }
    }
    
    private final static boolean startsWith(String xml, char[] ch, int pos) {
       return xml.indexOf(new String(ch), pos) == pos;
    }
    
    public final static String unEscapeXml(String xml) {
       StringBuilder buf = new StringBuilder();
       int i, len;
       if (xml == null)
          return null;

       len = xml.length();
       
       for (i = 0; i < len; i++) {
          char ch = xml.charAt(i); 
          if (ch != '&') {
             buf.append(ch);
             continue;
          }
          if (startsWith(xml, AMP, i)) {
             buf.append('&');
             i += AMP.length - 1;
          } 
          else if (startsWith(xml, LT, i)) {
             buf.append('<');
             i += LT.length - 1;
          } 
          else if (startsWith(xml, GT, i)) {
             buf.append('>');
             i +=  GT.length - 1;
          } 
          else if (startsWith(xml, QUOT, i)) {
             buf.append('"');
             i += QUOT.length - 1;
          } 
          else if (startsWith(xml, APOS, i)) {
             buf.append('\'');
             i += APOS.length - 1;
          } 
          else if (startsWith(xml, SLASH_R, i)) {
             buf.append('\r');
             i += SLASH_R.length - 1;
          } 
          else if (startsWith(xml, NULL, i)) {
             buf.append('\0');
             i += NULL.length - 1;
          }
       }
       return buf.toString();
    }

    
    
    public static void main(String[] args) throws XmlBlasterException {
       XmlBuffer buf = new XmlBuffer(256);
       String txt = "__subId:client/NtmService/session/1-xpath://key[contains (@oid,'com.xml.notam')]";
       buf.append("<qos>");
       buf.append("<subscribe id='");
       buf.appendAttributeEscaped(txt);
       buf.append("'/>");
       buf.append("</qos>");
       String xml = buf.toString();
       System.out.println(xml);
       MsgQosSaxFactory f = new MsgQosSaxFactory(Global.instance());
       MsgQosData data = f.readObject(xml);
       System.out.println(data.getSubscriptionId());
       
       buf = new XmlBuffer(256);
       buf.appendAttributeEscaped(txt);
       String tmp = buf.toString();
       System.out.print("Unescaped " + XmlBuffer.unEscapeXml(tmp));
       
    }
}
