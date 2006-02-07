/*------------------------------------------------------------------------------
Name:      ReplaceVariable.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.util;

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
   private boolean throwException = true;

   public ReplaceVariable() {
   }

   public ReplaceVariable(String startToken, String endToken) {
      this.startToken = startToken;
      this.endToken = endToken;
   }

   public void setMaxNest(int maxNest) {
      this.maxNest = maxNest;
   }

   public void setThrowException(boolean throwException) {
      this.throwException = throwException;
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
      int minIndex = 0;
      for (int ii = 0;; ii++) {
         int fromIndex = text.indexOf(this.startToken, minIndex);
         if (fromIndex == -1) return text;
         minIndex = 0;

         int to = text.indexOf(this.endToken, fromIndex);
         //System.out.println("ReplaceVariable: Trying fromIndex=" + fromIndex + " toIndex=" + to + " '" + text.substring(fromIndex,to+1) + "'");

         if (false) {  // to support "${A${B}}"
            int fromTmp = text.indexOf(this.startToken, fromIndex+1);
            if (fromTmp != -1 && to != -1 && fromTmp < to) {
               fromIndex = fromTmp;
               to = text.indexOf(this.endToken, fromTmp);
            }
         }

         if (to == -1) {
            if (this.throwException) {
               throw new IllegalArgumentException("Invalid variable '" + text.substring(fromIndex) +
                        "', expecting " + this.startToken + this.endToken + " syntax.");
            }
            return text;
         }
         String sub = text.substring(fromIndex, to + this.endToken.length()); // "${XY}"
         String subKey = sub.substring(this.startToken.length(), sub.length() - this.endToken.length()); // "XY"
         String subValue = cb.get(subKey);
         if (subValue != null) {
            //System.out.println("ReplaceVariable: fromIndex=" + fromIndex + " sub=" + sub + " subValue=" + subValue);
            text = replaceAll(text, fromIndex, sub, subValue);
         }
         else {
            minIndex = fromIndex+1;  // to support all recursions
         }

         if (ii > this.maxNest) {
            if (this.throwException) {
               throw new IllegalArgumentException("ReplaceVariable: Maximum nested depth of " + this.maxNest + " reached for '" + text + "'.");
            }
            System.out.println("ReplaceVariable: Maximum nested depth of " + this.maxNest + " reached for '" + text + "'.");
            return text;
         }
      }
   }

   /**
   * Replace all occurrences of "from" with to "to".
   */
   public final static String replaceAll(String str, int fromIndex, String from, String to) {
      if (str == null || str.length() < 1 || from == null || to == null)
         return str;
      if (str.indexOf(from) == -1)
         return str;
      StringBuffer buf = new StringBuffer("");
      String tail = str;
      while (true) {
         int index = tail.indexOf(from, fromIndex);
         if (index >= 0) {
            if (index > 0)
               buf.append(tail.substring(0, index));
            buf.append(to);
            tail = tail.substring(index + from.length());
         }
         else
            break;
      }
      buf.append(tail);
      return buf.toString();
   }

   /**
   * Replace all occurrences of "from" with to "to".
   */
   public final static String replaceAll(String str, String from, String to) {
      if (str == null || str.length() < 1 || from == null || to == null)
         return str;
      if (str.indexOf(from) == -1)
         return str;
      StringBuffer buf = new StringBuffer("");
      String tail = str;
      while (true) {
         int index = tail.indexOf(from);
         if (index >= 0) {
            if (index > 0)
               buf.append(tail.substring(0, index));
            buf.append(to);
            tail = tail.substring(index + from.length());
         }
         else
            break;
      }
      buf.append(tail);
      return buf.toString();
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
         int attrStart = xml.indexOf(attributeName, start);
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
    * java org.xmlBlaster.util.ReplaceVariable -template '${A${B}}' -A aa -B bb -Abb WRONG -aabb OK
    */
   public static void main(String args[]) {
      String template = "Hello ${A} and ${B}, ${A${B}}";
      for (int i=0; i<args.length-1; i++) { // Add all "-key value" command line
         String key = args[i].substring(1);
         if (key.equals("template"))
            template = args[i+1];
         else
            System.setProperty(key, args[i+1]);
         i++;
      }

      ReplaceVariable r = new ReplaceVariable();
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
