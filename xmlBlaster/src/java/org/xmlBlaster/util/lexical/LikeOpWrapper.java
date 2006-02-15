/*------------------------------------------------------------------------------
Name:      LikeOpWrapper.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

package org.xmlBlaster.util.lexical;

import gnu.regexp.RE;
import gnu.regexp.REException;

import java.util.StringTokenizer;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.ErrorCode;


/**
 * LikeOpWrapper
 * @author <a href="mailto:laghi@swissinfo.org">Michele Laghi</a>
 */
public final class LikeOpWrapper {

   private static final String ME = "LikeOpWrapper";
   private String regexPattern;
   private RE expression;
   private Global global;
   private LogChannel log;

   public LikeOpWrapper(Global global, String pattern) throws XmlBlasterException {
      this(global, pattern, (char)0);
   }

   public LikeOpWrapper(Global global, String pattern, char escape) throws XmlBlasterException {
      this(global, pattern, escape, true);
   }

/*
   public LikeOpWrapper(Global global, String pattern, char escape, boolean doReducedSyntax) 
      throws XmlBlasterException {
      this.global = global;
      this.log = this.global.getLog("lexical");
      
      String tmp = pattern;
      if (doReducedSyntax) {
         tmp = replace(tmp, escape, "_", ".");
         if (this.log.TRACE) this.log.trace(ME, "constructor regexPattern (transitional)='" + tmp + "'");
         tmp = replace(tmp, escape, "%", ".*");
         if (this.log.TRACE) this.log.trace(ME, "constructor regexPattern='" + this.regexPattern + "'");
      }

      try {
         this.regexPattern = tmp;
         this.expression = new RE(tmp);
      }
      catch (REException ex) {
         if (this.log.TRACE) this.log.trace(ME, "constructor " + Global.getStackTraceAsString());
         throw new XmlBlasterException(this.global, ErrorCode.USER_ILLEGALARGUMENT, ME + " constructor: could not generate a Regex from the string '" + pattern + "' reason: " + ex.getMessage());
      }
   }
*/
   public LikeOpWrapper(Global global, String pattern, char escape, boolean doReducedSyntax) 
   throws XmlBlasterException {
   this.global = global;
   this.log = this.global.getLog("lexical");
   
   String tmp = pattern;
   if (doReducedSyntax) {
      StringBuffer buf = new StringBuffer();
      boolean isEscape = false;
      for (int i=0; i < pattern.length(); i++) {
         char ch = pattern.charAt(i);
         switch (ch) {
            case '_' :
               if (isEscape) buf.append(ch);
               else buf.append('.');
               isEscape = false;
               break;
            case '%' : 
               if (isEscape) buf.append(ch);
               else buf.append('.').append('*');
               isEscape = false;
               break;
            default: 
               if (ch == escape) isEscape = true;
               else {
                  isEscape = false;
                  buf.append(ch);
               }
         }
      }
      tmp = buf.toString();
      if (this.log.TRACE) this.log.trace(ME, "constructor regexPattern='" + tmp + "'");
   }

   try {
      this.regexPattern = tmp;
      this.expression = new RE(tmp);
   }
   catch (REException ex) {
      if (this.log.TRACE) this.log.trace(ME, "constructor " + Global.getStackTraceAsString(ex));
      throw new XmlBlasterException(this.global, ErrorCode.USER_ILLEGALARGUMENT, ME + " constructor: could not generate a Regex from the string '" + pattern + "' reason: " + ex.getMessage());
   }
}

   
   public boolean match(String inputString) {
      return this.expression.isMatch(inputString);
   }
   
   /**
    * 
    * @param pattern The input String to modify (the initial pattern used)
    * @param escape The character to use as the escape char. If 0 then it works as no escape was defined
    * @param token the token to be replaced
    * @param replacement the string to use to replace the given token.
    * @return
    */
   public static String replace(String pattern, char escape, String token, String replacement) {
      StringTokenizer tokenizer = new StringTokenizer(pattern, token);
      StringBuffer buf = new StringBuffer();
      while (tokenizer.hasMoreTokens()) {
         String tmp = tokenizer.nextToken();
         buf.append(tmp);
         if (tokenizer.hasMoreTokens()) {
            if (tmp.charAt(tmp.length()-1) == escape) {
               buf.append(token);
            }
            else {
               buf.append(replacement);
            }
         }
      }
      return buf.toString();
   }
   
   public static void main(String[] args) {
      if (args.length != 2) {
         System.err.println("usage: java org.xmlBlaster.util.lexical.LikeOpWrapper pattern inputString");
         System.exit(-1);
      }
      try {
         LikeOpWrapper wrapper = new LikeOpWrapper(new Global((String[])null), args[0], (char)0, false);
         System.out.println("result: " + wrapper.match(args[1]));
      }
      catch (Exception ex) {
         ex.printStackTrace();
      }
   }
}
