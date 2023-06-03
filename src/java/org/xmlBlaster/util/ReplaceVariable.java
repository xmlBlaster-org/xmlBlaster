/*------------------------------------------------------------------------------
Name:      ReplaceVariable.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.util;

import java.util.StringTokenizer;

/**
 * @author xmlBlaster@marcelruff.info
 * @see org.xmlBlaster.test.classtest.ReplaceVariableTest
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/util.property.env.html">The util.property.env requirement</a>
 */
public final class ReplaceVariable
{
   private int maxNest = 2000;
   private String startToken = "${";
   private String endToken = "}";
   private String escapeToken = null; // to be backward compatible "\\";
   /**
    * the member escapeToken is default null, but if to set, prefer following value 
    * (Backslash: '\', e.g. \${var}): 
    */
   public static final String PREFERRED_ESCAPE_TOKEN = "\\"; 
   private boolean throwException = true;
   private boolean allowRecursive;

   public ReplaceVariable() {
   }

   public ReplaceVariable(String startToken, String endToken) {
      this.startToken = startToken;
      this.endToken = endToken;
   }

   public void setMaxNest(int maxNest) {
      this.maxNest = maxNest;
   }
   
   public void setAllowRecursive(boolean allowRecursive) {
	   this.allowRecursive = allowRecursive;
   }

   public void setThrowException(boolean throwException) {
      this.throwException = throwException;
   }
   
   /** 
    * Can be set, to be able to escape the start token, 
    * so if e.g. set with backslash following pattern \${var} 
    * leads to ${var} and does not expose the 
    * value of var. Default it is not set, to due to 
    * backward compatibility reasons. 
    * @param escapeToken
    */
   public void setEscapeToken(String escapeToken) {
	   this.escapeToken = escapeToken;
   }

   /**
    * returns a String that contains <tt>count</tt> time the passed character
    * @author Michael Winkler, doubleSlash GmbH
    *
    * @return java.lang.String
    * @param oneChar char
    * @param count int
    */
   public String charChain(char oneChar, int count) {
     StringBuilder returnValue = new StringBuilder();
     if (count > 0)
       for (int index = 0; index < count; index++) {
         returnValue.append(oneChar);
       }
     return returnValue.toString();
   }

    /**
    * Replace dynamic variables, e.g. ${XY} with their values.
    * <p />
    * The maximum replacement (nesting) depth is 50.
    * @param text The value string which may contain zero to many ${...} variables
    * @param cb The callback supplied by you which replaces the found keys (from ${key})
    * @return The new value where all resolvable ${} are replaced.
    * @throws IllegalArgumentException if matching "}" is missing
    */
   public final String replace(String text, I_ReplaceVariable cb) {
	  if (text == null) return null;
	  if (cb == null) return text;
      int minIndex = 0;
      for (int ii = 0;; ii++) {
         int fromIndex = text.indexOf(this.startToken, minIndex);
         boolean isEscaped = false; // true = do not change value
         if (escapeToken != null) {
             int fromIndexEscaped = text.indexOf(this.escapeToken+this.startToken, minIndex);
             isEscaped = fromIndexEscaped != -1 && fromIndexEscaped == fromIndex - this.escapeToken.length();
         }
         if (fromIndex == -1 || isEscaped) {
        	 return (escapeToken == null) ? text : text.replace(this.escapeToken+this.startToken, this.startToken);
         }
         minIndex = 0;
         
         if (fromIndex+1 >= text.length()) {
            if (this.throwException) {
                throw new IllegalArgumentException("Invalid variable '" + text.substring(fromIndex) +
                         "', expecting " + this.startToken + this.endToken + " syntax.");
             }
            return (escapeToken == null) ? text : text.replace(this.escapeToken+this.startToken, this.startToken);
         }

         int to = text.indexOf(this.endToken, fromIndex+1);
         //System.out.println("ReplaceVariable: Trying fromIndex=" + fromIndex + " toIndex=" + to + " '" + text.substring(fromIndex,to+1) + "'");

         if (allowRecursive) {  // to support "${A${B}}"
            int fromTmp = text.indexOf(this.startToken, fromIndex+1);
            boolean isNotEscaped = true; // true, we ignore possible escape token, eg. \${var} -> \5, if var is 5. 
            if (escapeToken != null) {
                int fromTmpEscaped = text.indexOf(this.escapeToken+this.startToken, fromIndex+1);
                isNotEscaped = fromTmpEscaped == -1 || fromTmpEscaped != fromTmp - this.escapeToken.length();
            }
            if (fromTmp != -1 
            		&& isNotEscaped
            		&& to != -1 
            		&& fromTmp < to) {
               fromIndex = fromTmp;
               to = text.indexOf(this.endToken, fromTmp);
            }
         }

         if (to == -1) {
            if (this.throwException) {
               throw new IllegalArgumentException("Invalid variable '" + text.substring(fromIndex) +
                        "', expecting " + this.startToken + this.endToken + " syntax.");
            }
            return (escapeToken == null) ? text : text.replace(this.escapeToken+this.startToken, this.startToken);
         }
         String sub = text.substring(fromIndex, to + this.endToken.length()); // "${XY}"
         String subKey = sub.substring(this.startToken.length(), sub.length() - this.endToken.length()); // "XY"
         String subValue = cb.get(subKey);
         if (subValue != null) {
            //System.out.println("ReplaceVariable: fromIndex=" + fromIndex + " sub=" + sub + " subValue=" + subValue);
        	if (escapeToken == null) {
        		// different legacy behaviour, e.g. all subsequent tokens of same var name will be replaced by its value, 
        		// eg. "1: ${XY), 2: ${XY}" -> "1: 100, 2: 100", even if to ends after the first ${XY}
                text = replaceAll(text, fromIndex, sub, subValue);
        	} else {
        		// with introduction of escape Token, ensure only values in current interval are replaced, 
        		// e.g. correct: "1: ${XY), 2: \${XY}" -> "1: 100, 2: ${XY}" vs. 
        		//    incorrect: "1: ${XY), 2: \${XY}" -> "1: 100, 2: \100" (if case)
                text = replaceAll(text, fromIndex, to, sub, subValue);
        	}
         }
         else {
            minIndex = fromIndex+1;  // to support all recursions
         }

         if (ii > this.maxNest) {
            if (this.throwException) {
               throw new IllegalArgumentException("ReplaceVariable: Maximum nested depth of " + this.maxNest + " reached for '" + text + "'.");
            }
            System.out.println("ReplaceVariable: Maximum nested depth of " + this.maxNest + " reached for '" + text + "'.");
            return (escapeToken == null) ? text : text.replace(this.escapeToken+this.startToken, this.startToken);
         }
      }
   }

   /**
    * @param template 
    * @returns true if contains "${"
    */
   public boolean hasReplacementToken(final String template) {
     if (template == null || template.length() == 0) {
       return false;
     }
     return template.contains(this.startToken) && template.contains(this.endToken); // "${" "}"
   }
   

   /**
   * Replace all occurrences of "from" with to "to" in the range from fromIndex to toIndex.
   */
   private final static String replaceAll(String str, int fromIndex, int toIndex, String from, String to) {
      if (str == null || str.length() < 1 || from == null || to == null)
         return str;
      return str.substring(0, fromIndex) + str.substring(fromIndex, toIndex+1).replace(from, to) + str.substring(toIndex+1);
   }

   /**
   * Replace all occurrences of "from" with to "to" in the range from fromIndex to the end of string.
   */
   public final static String replaceAll(String str, int fromIndex, String from, String to) {
      if (str == null || str.length() < 1 || from == null || to == null)
         return str;
      return str.substring(0, fromIndex) + str.substring(fromIndex).replace(from, to);
   }

   /**
   * Replace all occurrences of "from" with to "to".
   */
   public final static String replaceAll(String str, String from, String to) {
      if (str == null || str.length() < 1 || from == null || to == null)
         return str;
      return str.replace(from, to);
   }

   /**
    * Replace exactly one occurrence of "from" with to "to"
    */
    public final static String replaceFirst(String str, String from, String to) {
      if (str == null || str.length() < 1 || from == null || to == null)
        return str;
      int index = str.indexOf(from);
      if (index >= 0) {
        StringBuilder tmp = new StringBuilder(str.length() + to.length());
        if (index > 0)
          tmp.append(str.substring(0, index));
        tmp.append(to);
        tmp.append(str.substring(index + from.length()));
        return tmp.toString();
      }
      else
        return str;
    }

    /**
     * Convert a separator based string to an array of strings. 
     * <p />
     * Example:<br />
     * NameList=Josua,David,Ken,Abel<br />
     * Will return each name separately in the array.
     * @param key the key to look for
     * @param defaultVal The default value to return if key is not found
     * @param separator  The separator, typically ","
     * @return The String array for the given key, the elements are trimmed (no leading/following white spaces), is never null
     */
     public static final String[] toArray(String str, String separator) {
        if (str == null) {
           return new String[0];
        }
        if (separator == null) {
           String[] a = new String[1];
           a[0] = str;
           return a;
        }
        StringTokenizer st = new StringTokenizer(str, separator);
        int num = st.countTokens();
        String[] arr = new String[num];
        int ii=0;
        while (st.hasMoreTokens()) {
          arr[ii++] = st.nextToken().trim();
        }
        return arr;
     }
    /**
    * Find the given tag from the given xml string and return its value.  
    * Does not work if the tag exists multiple time or occures somewhere else in the text 
    * @param tag For example "nodeId" for a tag &lt;nodeId>value&lt;/nodeId>
    * @return null if none is found
    */
   public static String extract(String xml, String tag) {
      if (xml == null || tag == null) return null;
      final String startToken = "<" + tag + ">";
      final String endToken = "</" + tag + ">";
      int start = xml.indexOf(startToken);
      int end = xml.indexOf(endToken);
      if (start != -1 && end != -1) {
         return xml.substring(start + startToken.length(), end);
      }
      return null;
   }

   /**
    * If the tag can have attributes
    * @param xml
    * @param tag For example "nodeId" for a tag &lt;nodeId something='ignored'>value&lt;/nodeId>
    * @return null if none is found, else 'value'
    */
   public static String extractIgnoreAttr(String xml, String tag) {
      if (xml == null || tag == null) return null;
      final String startToken = "<" + tag; // + ">";
      final String endToken = "</" + tag + ">";
      int start = xml.indexOf(startToken);
      start = xml.indexOf(">", start);
      int end = xml.indexOf(endToken);
      if (start != -1 && end != -1) {
         return xml.substring(start + 1, end);
      }
      return null;
   }
   
   /**
    * Find the given tag from the given xml string and return its value.  
    * Does not work if the tag exists multiple time or occures somewhere else in the text 
    * @param tag For example "nodeId" for a tag &lt;nodeId>value&lt;/nodeId>
    * @param attrToMatch. If you want to parse &lt;nodeId name='dummy' x='3'>value&lt;/nodeId> then 
    * you need to pass " name='dummy' x='3'" here.
    * @return null if none is found
    */
   public static String extractWithMatchingAttrs(String xml, String tag, String attrString) {
      if (xml == null || tag == null) return null;
      final String startToken = "<" + tag + attrString + ">";
      final String endToken = "</" + tag + ">";
      int start = xml.indexOf(startToken);
      int end = xml.indexOf(endToken, start);
      if (start != -1 && end != -1) {
         return xml.substring(start + startToken.length(), end);
      }
      return null;
   }
   
   /**
    * Find the given attribute from the given tag from the given xml string and return its value.  
    * Does not work if the tag exists multiple time or occures somewhere else in the text
    * @param xml The xml string to parse 
    * @param tag For example "node" for a tag &lt;node id='heron'>
    * @param attributeName "id"
    * @return null if none is found
    */
   public static String extract(String xml, String tag, String attributeName) {
      if (xml == null || tag == null || attributeName == null) return null;
      final String startToken = "<" + tag;
      final String endToken = ">";
      int start = xml.indexOf(startToken);
      int end = xml.indexOf(endToken);
      if (start != -1 && end != -1) {
         String tok = attributeName+"=";
         int attrStart = xml.indexOf(tok, start);
         if (attrStart == -1) return null;
         char apo = xml.charAt(attrStart+tok.length());
         int attrEnd = xml.indexOf(apo, attrStart+tok.length()+1);
         if (attrEnd == -1) return xml.substring(attrStart+tok.length()+1);
         return xml.substring(attrStart+tok.length()+1, attrEnd);
      }
      return null;
   }

   /**
    * Method for testing only. 
    * <br />
    * Invoke:
    * java org.xmlBlaster.util.ReplaceVariable -template 'Hello ${A} and ${B}, ${A${B}}' -A ZZXX -B OO -AOO OK
    * java org.xmlBlaster.util.ReplaceVariable -template 'Hello ${A} and ${B}, ${A${B}}' -A ZZXX -B OO -AOO OK
    * java org.xmlBlaster.util.ReplaceVariable -startToken @ -endToken @ -template '@A@' -A aa -B bb -Abb WRONG -aabb OK
    */
   public static void main(String args[]) {
      String template = "Hello ${A} and ${B}, ${A${B}}, \\${A} and \\${B}, \\${A${B}}, \\${A\\${B}} ";
      String startToken = "${";
      String endToken = "}";
      for (int i=0; i<args.length-1; i++) { // Add all "-key value" command line
         String key = args[i].substring(1);
         if (key.equals("template"))
            template = args[i+1];
         else if (key.equals("startToken"))
        	 startToken = args[i+1];
         else if (key.equals("endToken"))
        	 endToken = args[i+1];
         else
            System.setProperty(key, args[i+1]);
         i++;
      }
      System.out.println("Using startToken=" + startToken + " and endToken=" + endToken);

      ReplaceVariable r = new ReplaceVariable(startToken, endToken);
      r.setEscapeToken("\\");
      String result = r.replace(template,
         new I_ReplaceVariable() {
            public String get(String key) {
               return System.getProperty(key);
            }
         });
      System.out.println("INPUT : '" + template + "'");
      System.out.println("OUTPUT: '" + result + "'");
   }
}
