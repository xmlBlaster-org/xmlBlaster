/*------------------------------------------------------------------------------
 Name:      FilenameFilter.java
 Project:   xmlBlaster.org
 Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
 ------------------------------------------------------------------------------*/
package org.xmlBlaster.client.filepoller;

import java.io.File;
import java.io.FileFilter;

import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.ErrorCode;

import gnu.regexp.RE;
import gnu.regexp.REException;
//import java.util.regex.Pattern;

/**
 * FilenameFilter. This code is based on the BasicFileChooserUI swing code. The
 * difference is that id returns false if the found file is a directory.
 * 
 * @author <a href="mailto:michele@laghi.eu">Michele Laghi </a>
 * @deprectated it is now replaced by the corresponding class in org.xmlBlaster.contrib.filewatcher
 */
public class FilenameFilter implements FileFilter {

   private RE regex;
   private String pattern;
   
   public FilenameFilter() {
   }

   public FilenameFilter(String pattern, boolean trueRegex) throws XmlBlasterException {
      this();
      setPattern(pattern, trueRegex);
   }

   public void setPattern(String globPattern, boolean trueRegex) throws XmlBlasterException {
      if (trueRegex) {
         this.pattern = globPattern;
      }
      else {
         char[] gPat = globPattern.toCharArray();
         char[] rPat = new char[gPat.length * 2];
         boolean isWin32 = (File.separatorChar == '\\');
         boolean inBrackets = false;
         int j = 0;
         if (isWin32) {
            //    On windows, a regex ending with *.* is equal to ending with *
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
         this.pattern = new String(rPat, 0, j);
      }
      
      try {
         this.regex = new RE(this.pattern, RE.REG_ICASE);
      }
      catch (REException ex) {
         throw new XmlBlasterException(null, ErrorCode.USER_CONFIGURATION, "FilenameFilter", "wrong regex expression for filter '" + this.pattern + "'", ex);
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
      return regex.isMatch(f.getName());
      // return regex.matcher(f.getName()).matches();
   }

   /**
    * @return Returns the pattern.
    */
   public String getPattern() {
      return this.pattern;
   }
}
