/*------------------------------------------------------------------------------
Name:      FileInfo.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

package org.xmlBlaster.client.filesystem;

import java.io.File;

import org.jutils.log.LogChannel;

/**
 * FileInfo is a placeholder for the information necessary to the poller about
 * each file.
 * 
 * @author <a href="mailto:laghi@swissinfo.org">Michele Laghi</a>
 */
public class FileInfo {
   
   private String name;
   private long timestamp;
   private long size;
   private long lastChange;

   /**
    * Default Constructor
    *
    */
   public FileInfo() {
   }

   /**
    * Convenience constructor
    * @param file
    */
   public FileInfo(File file, LogChannel log) {
      this();
      update(file, log);
   }
   
   /**
    * updates this info object with the data contained in file. If file is 
    * null, then the method silently returns.
    * @param file
    */
   public void update(File file, LogChannel log) {
      if (file == null)
         return;
      if (this.name == null) {
         try {
            this.name = file.getCanonicalPath();
         }
         catch (java.io.IOException ex) {
            log.warn("FileInfo", "could not set the absolute name for file '" + file.getName() + "' " + ex.getMessage());
         }
      }
      long newTimestamp = file.lastModified();
      long newSize = file.length();
      if (this.size != newSize) {
         this.lastChange = System.currentTimeMillis();
         if (log.DUMP)
            log.dump("FileInfo", "'" + this.name + "' changed: size='" + this.size + "' new size='" + newSize + "'");
         this.size = newSize;
      }
      if (this.timestamp != newTimestamp) {
         this.lastChange = System.currentTimeMillis();
         if (log.DUMP)
            log.dump("FileInfo", "'" + this.name + "' changed: time='" + this.timestamp + "' new time='" + newTimestamp + "'");
         this.timestamp = newTimestamp;
      }
   }
   
   /**
    * @return Returns the lastChange.
    */
   public long getLastChange() {
      return lastChange;
   }
   /**
    * @return Returns the name.
    */
   public String getName() {
      return name;
   }
   /**
    * @return Returns the size.
    */
   public long getSize() {
      return size;
   }
   /**
    * @return Returns the timestamp.
    */
   public long getTimestamp() {
      return timestamp;
   }
   
   
   
   public String toXml(String offset) {
      StringBuffer buf = new StringBuffer();
      buf.append(offset).append("<file");
      if (this.name != null)
         buf.append("name='").append(this.name).append("' ");
      buf.append("time='" + this.timestamp + "' size='").append(this.size).append("' lastChanged='").append(this.lastChange).append("'/>\n");
      return buf.toString();
   }
   
   /**
    * @see java.lang.Object#equals(java.lang.Object)
    */
   public boolean equals(Object obj) {
      if (obj instanceof FileInfo) {
         FileInfo other = (FileInfo)obj;
         if (other.name == null && this.name == null)
            return true;
         if (other.name != null && this.name != null) {
            return this.name.equals(other.name);
         }
      }
      return false;
   }
}
