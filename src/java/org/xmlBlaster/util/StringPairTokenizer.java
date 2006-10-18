/*------------------------------------------------------------------------------
Name:      StringPairTokenizer.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

package org.xmlBlaster.util;

import java.util.Iterator;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.StringTokenizer;
//import java.util.regex.Matcher;
//import java.util.regex.Pattern;

import org.xmlBlaster.util.qos.ClientProperty;

/**
 * StringPairTokenizer is a utility class used to parse a string giving
 * back a map containing pairs of key/value strings. 
 * <br />
 * The method parseLine respects quoted '"' tokens and ignores the separator inside the quotes.
 * 
 * @author <a href="mailto:laghi@swissinfo.org">Michele Laghi</a>
 * @author <a href="mailto:mr@marcelruff.info">Marcel Ruff</a>
 */
public class StringPairTokenizer {
   public static final char DEFAULT_QUOTE_CHARACTER = '"';
   public static final char ESCAPE_CHARACTER = '\\';
   public static final char DEFAULT_SEPARATOR = ',';
   public static final char DEFAULT_INNER_SEPARATOR = '=';

   /**
    * @see #parseLine(String[] nextLines, char separator, char quotechar, boolean trimEmpty)
    */
   public static String[] parseLine(String nextLine) {
      return parseLine(nextLine, DEFAULT_SEPARATOR, DEFAULT_QUOTE_CHARACTER, true);
   }

   /**
    * @see #parseLine(String[] nextLines, char separator, char quotechar, boolean trimEmpty)
    */
   public static String[] parseLine(String nextLine, char separator) {
      return parseLine(nextLine, separator, DEFAULT_QUOTE_CHARACTER, true);
   }

   /**
    * @see #parseLine(String[] nextLines, char separator, char quotechar, boolean trimEmpty, boolean)
    */
   public static String[] parseLine(String nextLine, char separator,
         char quotechar, boolean trimEmpty) {
      return parseLine(nextLine, separator, quotechar, trimEmpty, false);
   }
   /**
    * @see #parseLine(String[] nextLines, char separator, char quotechar, boolean trimEmpty, boolean)
    */
   public static String[] parseLine(String nextLine, char separator,
                                    char quotechar, boolean trimEmpty,
                                    boolean preserveInsideQuoteChar) {
      if (nextLine == null || nextLine.length()==0) return new String[0];
      String[] nextLines = new String[1];
      nextLines[0] = nextLine;
      return parseLine(nextLines, separator, quotechar, trimEmpty, preserveInsideQuoteChar);
   }
   
   /**
    * Split string to tokens and respect quotes. 
    * 
    *<pre>  
    * /node/heron/client/\"joe/the/great\"
    * 
    *  'node'
    *  'heron'
    *  'client'
    *  'joe/the/great'
    *</pre>  
    *
    * Thanks to http://opencsv.sourceforge.net/ (under Apache license)
    * 
    * @param nextLines An array of lines, followup lines will only be parsed if an open quotechar exists
    * @param separator Defaults to StringPairTokenizer.DEFAULT_SEPARATOR=','
    * @param quotechar Defaults to StringPairTokenizer.DEFAULT_QUOTE_CHARACTER='"'
    * @param trimEmpty if true removes silently empy tokens
    * @param preserveInsideQuoteChar true: Preserve the  inside quotes of "bla, bla, "blu blu", bli"  
    * @return Never null, if nextLines is null or empty we return an empty array
    */
   public static String[] parseLine(String[] nextLines, char separator,
                char quotechar, boolean trimEmpty, boolean preserveInsideQuoteChar) {
      List tokensOnThisLine = new ArrayList();
      StringBuffer sb = new StringBuffer();
      boolean inQuotes = false;
      int jj=0;
      String nextLine = nextLines[jj];
      do {
         if (sb.length() > 0) {
            // continuing a quoted section, reappend newline
            sb.append("\n");
            jj++;
            if (jj >= nextLines.length)
               break;
            nextLine = nextLines[jj];
         }
         for (int i = 0; i < nextLine.length(); i++) {
            char c = nextLine.charAt(i);
            if (c == quotechar) {
               inQuotes = !inQuotes;
               if (preserveInsideQuoteChar && i>0 && i<(nextLine.length()-1))
                  sb.append(c);
            } else if (c == separator && !inQuotes) {
               if (trimEmpty && sb.toString().trim().length() == 0) {
                  ;
               }
               else {
                  tokensOnThisLine.add(sb.toString());
               }
               sb = new StringBuffer(); // start work on next token
            } else {
               sb.append(c);
            }
         }
      } while (inQuotes);
      tokensOnThisLine.add(sb.toString());
      return (String[]) tokensOnThisLine.toArray(new String[0]);
   }
   
   /**
    * Split string to tokens and respect quotes, then parse key/values into the returned map. 
    *  
    * If a value is missing then a null object will be put into the map as value.
    * The map returns pairs 'String,ClientProperty' if wantClientProperties is true,
    * otherwise it returns 'String,String' pairs.
    *   
    * @param nextLines e.g.
    * <code>String[] nextLines = { "org.xmlBlaster.protocol.soap.SoapDriver,\"classpath=xerces.jar:soap.jar,all\",MAXSIZE=100,a=10,\"b=", "20\",c=30" };</code>
    *                  Followup lines will only be parsed if an open quotechar exists
    * @param innerSeparator is for example StringPairTokenizer.DEFAULT_INNER_SEPARATOR "=" or " "
    * @param wantClientProperties if set to <code>true</code> returns pairs 'String,ClientProperty', returns 'String,String' pairs otherwise.
    * @param trimValue If true the value is trimmed (removed white spaces in front and back)
    * @return Never null,
    * <pre>
    * classpath=xerces.jar:soap.jar,all
    * org.xmlBlaster.protocol.soap.SoapDriver=null
    * MAXSIZE=100
    * a=10
    * c=30
    * b=20
    * </pre>
    * @see #parseLine(String[] nextLines, char separator, char quotechar, boolean trimEmpty)
    */
   public static Map parseLine(String[] nextLines, char separator, char quotechar, char innerSeparator, boolean trimEmpty, boolean wantClientProperties, boolean trimValue) {
      String[] toks = parseLine(nextLines, separator, quotechar, trimEmpty, false);
      Map ret = new HashMap();
      for (int i=0; i<toks.length; i++) {
         String tok = toks[i];
         int pos = tok.indexOf(innerSeparator);
         if (pos < 0) {
            ret.put(tok.trim(), null);
         }
         else {
            String key = tok.substring(0,pos).trim();
            String value = tok.substring(pos+1);
            if (trimValue) value = value.trim(); 
            if (wantClientProperties) 
               ret.put(key, new ClientProperty(key, null, null, value));
            else
               ret.put(key, value);
         }
      }
      return ret;
   }

   /**
    * @see #parseLine(String[] nextLines, char separator, char quotechar, char innerSeparator, boolean trimEmpty, boolean wantClientProperties, boolean trimEmpty)
    */
   public static Map parseLine(String nextLine, char separator, char quotechar, char innerSeparator, boolean trimEmpty, boolean wantClientProperties) {
      if (nextLine == null || nextLine.length()==0) return new HashMap();
      String[] nextLines = new String[1];
      nextLines[0] = nextLine;
      return parseLine(nextLines, separator, quotechar, innerSeparator, trimEmpty, wantClientProperties, true);
   }
   
   /**
    * Parsing for example >org.xmlBlaster.protocol.soap.SoapDriver,"classpath=xerces.jar:soap.jar,all",MAXSIZE=100,a=10<. 
    * <p>
    * Using default separator chars and quote chars:
    *  <code>return parseLine(nextLines, DEFAULT_SEPARATOR, DEFAULT_QUOTE_CHARACTER, DEFAULT_INNER_SEPARATOR, true, false, false);</code>
    * @see #parseLine(String[] nextLines, char separator, char quotechar, char innerSeparator, boolean trimEmpty, boolean wantClientProperties, boolean trimEmpty)
    */
   public static Map parseLineToProperties(String nextLine) {
      if (nextLine == null || nextLine.length()==0) return new HashMap();
      String[] nextLines = new String[1];
      nextLines[0] = nextLine;
      return parseLine(nextLines, DEFAULT_SEPARATOR, DEFAULT_QUOTE_CHARACTER, DEFAULT_INNER_SEPARATOR, true, false, false);
   }

   /*
    * Split a string (similar to StringTokenizer) but respect escape quotes. 
    * <br />
    *<pre>  
    * String[] arr = split("now,DATE,\"dd,MM,yyyy\"", ",", "\"");
    *  now
    *  DATE
    *  "dd,MM,yyyy"
    *</pre>  
    *<pre>  
    *  ,node,\"h,,eron\",client,\"X,\,[],X,X\"
    *    'node'
    *    '"h,,eron"'
    *    'client'
    *    '"X,,[],X,X"'
    *</pre>  
    *  
    *  Caution: I don't think this is very stable
    *  We should change to something like http://opencsv.sourceforge.net/
    *  
    *  @see http://www.creativyst.com/Doc/Articles/CSV/CSV01.htm#FileFormat
    *  @see http://www.jguru.com/faq/view.jsp?EID=809266
   public static String[] split(String field, String separator, String quotechar) {
      // This can't handle leading ',' see hack below
      //String regex="\\s*\"(.*?)\"\\s*|(?<=^|,)[^,]*"
      StringBuffer sb = new StringBuffer();
      sb.append("\\s*"+quotechar+".*?"+quotechar+"\\s*");  //all matches enclosed in double quotes (\s*".*?"\s*) with leading/trailing white spaces
      sb.append("|");        //or
      sb.append("(?<=^|"+separator+")"); //beginning of line or comma (^|,) via zero-width positive lookbehind ((?<=)) followed by
      sb.append("[^"+separator+"]*");    //any character except comma ([^,]) zero or more times (*)
      String regex = sb.toString();
      while (field.startsWith(separator))
         field = field.substring(1); // hack
      
      //  DOES NOT WORK STABLE, for exampel '/node/heron/client/joe' fails
      //  whereas '/node/heron/client/\"joe/xx\"' works
      //String regex = "(?:[^\",]+?(?=,))|(\".+\")";
      //String regex = "(?:[^\""+separator+"]+?(?="+separator+"))|(\".+\")";
      //String regex = "(?:[^"+quotechar+separator+"]+?(?="+separator+"))|("+quotechar+".+"+quotechar+")";

      Pattern pat = Pattern.compile(regex);
      Matcher mat = pat.matcher(field);
   
      ArrayList list = new ArrayList();
      while (mat.find()) {
         String s = mat.group();
         list.add(s);
       }
      return (String[])list.toArray(new String[list.size()]);
   }
    */

         /*
        public static final char ESCAPE_CHARACTER = '\\';
        
        public static final char DEFAULT_SEPARATOR = ',';
        
        public static final char DEFAULT_QUOTE_CHARACTER = '"';
         * Writes the next line to the file.
         * 
         * @param nextLine a string array with each comma-separated element as a separate
         *         entry.
         * 
         * @throws IOException
         *             if bad things happen during the write
        public void writeNext(String[] nextLine) throws IOException {
                StringBuffer sb = new StringBuffer();
                for (int i = 0; i < nextLine.length; i++) {
                        String nextElement = nextLine[i];
                        sb.append(quotechar);
                        for (int j = 0; j < nextElement.length(); j++) {
                                char nextChar = nextElement.charAt(j);
                                if (nextChar == quotechar) {
                                        sb.append(ESCAPE_CHARACTER).append(nextChar);
                                } else if (nextChar == ESCAPE_CHARACTER) {
                                        sb.append(ESCAPE_CHARACTER).append(nextChar);
                                } else {
                                        sb.append(nextChar);
                                }
                        }
                        sb.append(quotechar);
                        if (i != nextLine.length - 1) {
                                sb.append(separator);
                        }
                }
                sb.append('\n');
                pw.write(sb.toString());

        }
         */


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
     * Dumps the given map to a human readable string. 
     * @param map
     * @return e.g. "key1=15,name=joe"
     */
    public static String dumpMap(Map map) {
       StringBuffer buf = new StringBuffer();
       if (map != null) {
          boolean first = true;
          Iterator it = map.entrySet().iterator();
          while (it.hasNext()) {
             Map.Entry entry = (Map.Entry)it.next();
             buf.append(entry.getKey()).append("=").append(entry.getValue());
             if (!first) buf.append(",");
             first = false;
          }
       }
       else
          buf.append("null");
       return buf.toString();
    }


   /**
    * If a value is missing then a null object will be put into the map as value.
    * The map returns pairs 'String,ClientProperty' if wantClientProperties is true,
    * otherwise it returns 'String,String' pairs. 
    *   
    * @param rawString e.g. "org.xmlBlaster.protocol.soap.SoapDriver,classpath=xerces.jar:soap.jar,MAXSIZE=100"
    * @param outerToken is for example ";" or ","
    * @param innterToken is for example "=" or " "
    * @param wantClientProperties if set to <code>true</code> returns pairs 'String,ClientProperty', returns 'String,String' pairs otherwise.
    */
   private static Map parseStringProperties(String rawString, String outerToken, String innerToken, boolean wantClientProperties) {
      if (rawString==null) throw new IllegalArgumentException("SessionInfo.parsePropertyValue(null)");
      Map ret = new HashMap();
      StringTokenizer st = new StringTokenizer(rawString, outerToken);
      while(st.hasMoreTokens()) {
         String tok = st.nextToken().trim();
         int pos = tok.indexOf(innerToken);
         if (pos < 0) {
            ret.put(tok.trim(), null);
         }
         else {
            String key = tok.substring(0,pos).trim();
            String value = tok.substring(pos+1).trim(); 
            if (wantClientProperties) 
               ret.put(key, new ClientProperty(key, null, null, value));
            else
               ret.put(key, value);
         }
      }
      return ret;
   }


   /**
    * @param rawString e.g. "org.xmlBlaster.protocol.soap.SoapDriver,classpath=xerces.jar:soap.jar,MAXSIZE=100"
    * @param outerToken is for example ";" or ","
    * @param innterToken is for example "=" or " "
    * If a value is missing then a null object as value.
    * the map returns pairs 'String,ClientProperty'.
    */
   public static Map parseToStringClientPropertyPairs(String rawString, String outerToken, String innerToken) {
      return parseStringProperties(rawString, outerToken, innerToken, true);
   }


   /**
    * @param rawString e.g. "org.xmlBlaster.protocol.soap.SoapDriver,classpath=xerces.jar:soap.jar,MAXSIZE=100"
    * @param outerToken is for example ";" or ","
    * @param innterToken is for example "=" or " "
    * If a value is missing then a null object as value.
    * the map returns pairs 'String,String'.
    */
   public static Map parseToStringStringPairs(String rawString, String outerToken, String innerToken) {
      return parseStringProperties(rawString, outerToken, innerToken, false);
   }
   
   // java org.xmlBlaster.util.StringPairTokenizer
   public static void main(String[] args) {
      String test = (args.length > 0) ? args[0] : "/node/heron/client/\"joe/the/great\"";
      String separator = (args.length > 1) ? args[1] : "/";
      String quotechar = (args.length > 2) ? args[2] : "\"";
      
      System.out.println("From '" + test + "' we get");
      /*
      {
         System.out.println("with split():");
         String[] result = split(test, separator, quotechar);
         for (int i=0; i<result.length; i++)
            System.out.println("'" + result[i] + "'");
      }
      */

      {
         System.out.println("with parseLine():");
         String[] result = parseLine(test, separator.charAt(0), quotechar.charAt(0), true);
         for (int i=0; i<result.length; i++)
            System.out.println("'" + result[i] + "'");
      }

      {
        System.out.println("\nTesting with quotes:\n");
        String[] nextLines = { "org.xmlBlaster.protocol.soap.SoapDriver,\"classpath=xerces.jar:soap.jar,all\",MAXSIZE=100,a=10,\"b=", "20\",c=30" };
        Map map = parseLine(nextLines, DEFAULT_SEPARATOR, DEFAULT_QUOTE_CHARACTER, DEFAULT_INNER_SEPARATOR, true, false, true);
        java.util.Iterator it = map.entrySet().iterator();
        while (it.hasNext()) {
           Map.Entry entry = (Map.Entry)it.next();
           System.out.println(entry.getKey() + "=" + entry.getValue());
        }
      }
   }
}
