/*------------------------------------------------------------------------------
 Name:      FilenameFilter.java
 Project:   xmlBlaster.org
 Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
 ------------------------------------------------------------------------------*/
package org.xmlBlaster.client.filepoller;

import java.io.File;
import java.io.FileFilter;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.ErrorCode;

import gnu.regexp.RE;
import gnu.regexp.REException;
//import java.util.regex.Pattern;

/**
 * FilenameFilter. This code is based on the BasicFileChooserUI swing code. The
 * difference is that id returns false if the found file is a directory.
 * 
 * @author <a href="mailto:laghi@swissinfo.org">Michele Laghi </a>
 */
public class FilenameFilter implements FileFilter {

   //private Pattern pattern;
   private RE pattern;
   
   public FilenameFilter() {
   }

   public FilenameFilter(Global global, String pattern, boolean trueRegex) throws XmlBlasterException {
      this();
      setPattern(global, pattern, trueRegex);
   }

   public void setPattern(Global global, String globPattern, boolean trueRegex) throws XmlBlasterException {
      LogChannel log = global.getLog("filepoller");
      char[] gPat = globPattern.toCharArray();
      char[] rPat = new char[gPat.length * 2];
      boolean isWin32 = (File.separatorChar == '\\');
      boolean inBrackets = false;
      StringBuffer buf = new StringBuffer();
      int j = 0;
      if (isWin32) {
         //    On windows, a pattern ending with *.* is equal to ending with *
         int len = gPat.length;
         if (globPattern.endsWith("*.*")) {
            len -= 2;
         }
         for (int i = 0; i < len; i++) {
            if (gPat[i] == '*') {
               rPat[j++] = '.';
            }
            rPat[j++] = gPat[i];
         }
      }
      else {
         for (int i = 0; i < gPat.length; i++) {
            switch (gPat[i]) {
               case '*':
                  if (!inBrackets) {
                     rPat[j++] = '.';
                  }
                  rPat[j++] = '*';
                  break;
               case '?':
                  rPat[j++] = inBrackets ? '?' : '.';
                  break;
               case '[':
                  inBrackets = true;
                  rPat[j++] = gPat[i];
                  if (i < gPat.length - 1) {
                     switch (gPat[i + 1]) {
                        case '!':
                        case '^':
                           rPat[j++] = '^';
                           i++;
                           break;
                        case ']':
                           rPat[j++] = gPat[++i];
                           break;
                     }
                  }
                  break;
               case ']':
                  rPat[j++] = gPat[i];
                  inBrackets = false;
                  break;
               case '\\':
                  if (i == 0 && gPat.length > 1 && gPat[1] == '~') {
                     rPat[j++] = gPat[++i];
                  }
                  else {
                     rPat[j++] = '\\';
                     if (i < gPat.length - 1 && "*?[]".indexOf(gPat[i + 1]) >= 0) {
                        rPat[j++] = gPat[++i];
                     }
                     else {
                        rPat[j++] = '\\';
                     }
                  }
                  break;
               default:
                  //if ("+()|^$.{}<>".indexOf(gPat[i]) >= 0) {
                  if (!Character.isLetterOrDigit(gPat[i])) {
                     rPat[j++] = '\\';
                  }
                  rPat[j++] = gPat[i];
                  break;
            }
         }
      }
      try {
         if (trueRegex)
            this.pattern = new RE(globPattern, RE.REG_ICASE);
         else
            this.pattern = new RE(new String(rPat, 0, j), RE.REG_ICASE);
      }
      catch (REException ex) {
         throw new XmlBlasterException(global, ErrorCode.USER_CONFIGURATION, "FilenameFilter", "wrong regex expression for filter '" + new String(rPat, 0, j) + "'", ex);
      }
      //this.pattern = Pattern.compile(new String(rPat, 0, j), Pattern.CASE_INSENSITIVE);
   }

   /**
    * @see java.io.FileFilter#accept(java.io.File)
    */
   public boolean accept(File f) {
      if (f == null) {
         return false;
      }
      if (f.isDirectory()) {
         return false;
      }
      return pattern.isMatch(f.getName());
      // return pattern.matcher(f.getName()).matches();
   }
}
