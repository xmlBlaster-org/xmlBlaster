/*------------------------------------------------------------------------------
Name:      FileInfo.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

package org.xmlBlaster.client.filepoller;

import java.io.File;
import java.sql.Timestamp;

import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * FileInfo is a placeholder for the information necessary to the poller about
 * each file.
 * 
 * @author <a href="mailto:michele@laghi.eu">Michele Laghi</a>
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
   public FileInfo(File file, Logger log) {
      this();
      update(file, log);
      //log.info("Found new file '"+this.name+"' timestamp=" + getTimestampStr() + " size=" + this.size + " lastChange=" + getLastChangeStr());
   }
   
   /**
    * updates this info object with the data contained in file. If file is 
    * null, then the method silently returns.
    * @param file
    */
   public void update(File file, Logger log) {
      if (file == null)
         return;
      if (this.name == null) {
         try {
            this.name = file.getCanonicalPath();
         }
         catch (java.io.IOException ex) {
            log.warning("could not set the absolute name for file '" + file.getName() + "' " + ex.getMessage());
         }
      }
      long newTimestamp = file.lastModified();
      long newSize = file.length();
      if (this.size != newSize) {
         this.lastChange = System.currentTimeMillis();
         if (log.isLoggable(Level.FINEST))
            log.finest("'" + this.name + "' changed: size='" + this.size + "' new size='" + newSize + "'");
         this.size = newSize;
      }
      if (this.timestamp != newTimestamp) {
         this.lastChange = System.currentTimeMillis();
         if (log.isLoggable(Level.FINEST))
            log.finest("'" + this.name + "' changed: time='" + getTimestampStr() + "' new time='" + (new Timestamp(newTimestamp).toString()) + "'");
         this.timestamp = newTimestamp;
      }
   }
   
   /**
    * @return Returns the lastChange.
    */
   public long getLastChange() {
      return this.lastChange;
   }

   public String getTimestampStr() {
      return new Timestamp(this.timestamp).toString();
   }

   public String getLastChangeStr() {
      return new Timestamp(this.lastChange).toString();
   }

   /**
    * @return Returns the name.
    */
   public String getName() {
      return this.name;
   }
   
   public static String getRelativeName(String name) {
      int pos = name.lastIndexOf(File.separatorChar);
      if (pos < 0) return name;
      return name.substring(pos+1);
   }
   
   public String getRelativeName() {
      return getRelativeName(this.name);
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
