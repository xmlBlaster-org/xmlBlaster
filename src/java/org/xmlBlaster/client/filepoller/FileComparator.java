/*------------------------------------------------------------------------------
Name:      FileComparator.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

package org.xmlBlaster.client.filepoller;

import java.util.Comparator;


/**
 * FileComparator used to compare two FileInfo objects
 * 
 * @author <a href="mailto:michele@laghi.eu">Michele Laghi</a>
 */
public class FileComparator implements Comparator {

   public FileComparator() {
   }
   
   /**
    * @see java.util.Comparator#compare(java.lang.Object, java.lang.Object)
    */
   public int compare(Object o1, Object o2) {
      if (o1 == o2 || o1.equals(o2))
         return 0;
      if (o1 instanceof FileInfo && o2 instanceof FileInfo) {
         FileInfo info1 = (FileInfo)o1;
         FileInfo info2 = (FileInfo)o2;
         if (info1.getTimestamp() > info2.getTimestamp()) {
            return 1;
         }
         else if (info1.getTimestamp() < info2.getTimestamp()) {
            return -1;
         }
         else { // then same timestamp
            return info1.getName().compareTo(info2.getName());
         }
      }
      return o1.toString().compareTo(o2.toString());
   }
}
