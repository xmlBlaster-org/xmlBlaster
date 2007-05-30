/*------------------------------------------------------------------------------
Name:      XmlBuffer.java
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.util;

/**
 * Same as StringBuffer but has the additional method appendEscaped() which
 * escapes predefined XML identities.
 * @author mr@marcelruff.info
 */
public class XmlBuffer {
        private StringBuffer buf;
        public XmlBuffer(int len) {
                this.buf = new StringBuffer(len);
        }

    /**
     * Escape predefined xml entities (&, <, >, ', ").
     * Additionally the '\0' is escaped.
     * @param text
     * @return The escaped text is appended to the StringBuffer.
     */
        public XmlBuffer appendEscaped(String text) {
                append(this.buf, text);
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
         * Aquivalent to a StringBuffer.append().
         */
        public XmlBuffer append(String str) {
                this.buf.append(str);
                return this;
        }

        /**
         * Aquivalent to a StringBuffer.append().
         */
        public XmlBuffer append(long ln) {
                this.buf.append(ln);
                return this;
        }

        /**
         * Aquivalent to a StringBuffer.append().
         */
        public XmlBuffer append(boolean b){
                this.buf.append(b);
                return this;
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
        StringBuffer buf = new StringBuffer((int)(text.length()*1.2));
        append(buf, text);
        return buf.toString();
    }

    /**
     * Escape predefined xml entities (&, <, >, ', ").
     * Additionally the '\0' is escaped.
     * @param text
     * @return The escaped text is appended to the given StringBuffer.
     */
    public static void append(StringBuffer buf, String text) {
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
                        buf.append(c);
            }
        }
        }
}
