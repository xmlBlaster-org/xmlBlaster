/*------------------------------------------------------------------------------
Name:      ReplicationDumper.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

package org.xmlBlaster.contrib.replication;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.xmlBlaster.contrib.I_Info;
import org.xmlBlaster.contrib.I_Update;
import org.xmlBlaster.contrib.PropertiesInfo;
import org.xmlBlaster.contrib.dbwriter.I_Parser;
import org.xmlBlaster.contrib.dbwriter.I_Writer;
import org.xmlBlaster.contrib.dbwriter.info.SqlInfo;
import org.xmlBlaster.contrib.dbwriter.info.SqlRow;
import org.xmlBlaster.contrib.filewriter.FileWriterCallback;
import org.xmlBlaster.contrib.replication.impl.SearchableConfig;
import org.xmlBlaster.util.qos.ClientProperty;

public class ReplicationDumper implements I_Writer, ReplicationConstants {
   private static Logger log = Logger.getLogger(ReplicationDumper.class.getName());
   protected I_Info info;
   I_Mapper mapper;
   private String importLocation;
   private I_Update callback;
   private boolean keepDumpFiles;
   private I_Parser parserForOldInUpdates;
   private FileWriter dumper;
   private String dumperFilename;
   private int count = 0;
   private int backupEvery = 500;
   
   public ReplicationDumper() {
   }
   
   /**
    * @see org.xmlBlaster.contrib.I_ContribPlugin#getUsedPropertyKeys()
    */
   public Set getUsedPropertyKeys() {
      Set set = new HashSet();
      set.add(I_DbSpecific.NEEDS_PUBLISHER_KEY);
      set.add("replication.mapper.class");
      set.add("replication.overwriteTables");
      set.add("replication.importLocation");
      set.add("dbWriter.prePostStatement.class");
      PropertiesInfo.addSet(set, this.mapper.getUsedPropertyKeys());
      return set;
   }


   public void init(I_Info info_) throws Exception {
      log.info("init invoked");
      this.info = info_;

      // this avoids the publisher to be instantiated (since we are on the slave side)
      this.info.put(I_DbSpecific.NEEDS_PUBLISHER_KEY, "false");
      
      this.importLocation = this.info.get("replication.importLocation", "${java.io.tmpdir}");
      // clean from ending separators
      if (this.importLocation.endsWith(File.separator) || this.importLocation.endsWith("/"))
         this.importLocation = this.importLocation.substring(0, this.importLocation.length()-1);
      
      String tmpImportLocation = this.info.get("replication.importLocationChunks", this.importLocation + "/chunks");
      boolean overwriteDumpFiles = true;
      String lockExtention =  null;
      this.keepDumpFiles = info_.getBoolean("replication.keepDumpFiles", false);
      this.callback = new FileWriterCallback(this.importLocation, tmpImportLocation, lockExtention, overwriteDumpFiles, this.keepDumpFiles);
      this.info = info_;

      String parserClass = this.info.get("parser.class", "org.xmlBlaster.contrib.dbwriter.SqlInfoParser").trim();
      if (parserClass.length() > 0) {
         ClassLoader cl = this.getClass().getClassLoader();
         this.parserForOldInUpdates = (I_Parser)cl.loadClass(parserClass).newInstance();
         this.parserForOldInUpdates.init(info_);
         if (log.isLoggable(Level.FINE)) 
            log.fine(parserClass + " created and initialized");
      }
      else
         log.severe("Couldn't initialize I_Parser, please configure 'parser.class'");
      dumperFilename = info_.get("dumper.filename", "" + System.currentTimeMillis());
      File tmpFile = new File(dumperFilename);
      if (!tmpFile.isAbsolute())
         dumperFilename = this.importLocation + "/" + dumperFilename;
      backupEvery = info_.getInt("dumper.backupEvery", 1000);
      dumperBackup();
   }

   
   private void copyFile(String inFile, String outFile) throws IOException {
      byte[] buf = new byte[1024*256];
      FileInputStream fis = new FileInputStream(inFile);
      FileOutputStream fos = new FileOutputStream(outFile);
      int ret = 0;
      while ( (ret=fis.read(buf)) > -1) {
         fos.write(buf, 0, ret);
      }
      fis.close();
      fos.close();
   }
   
   private void dumperBackup() {
      try {
         if (dumper != null) {
            // close file
            dumper.close();
            dumper = null;
            // make a backup
            copyFile(dumperFilename, dumperFilename + ".bak");
         }
         // open the stream for writing again.
         final boolean append = true;
         dumper = new FileWriter(dumperFilename, append);
      }
      catch (IOException ex) {
         ex.printStackTrace();
      }
   }
   
   public void shutdown() throws Exception {
      if (this.mapper != null) {
         this.mapper.shutdown();
         this.mapper = null;
      }
      if (this.parserForOldInUpdates != null) {
         this.parserForOldInUpdates.shutdown();
         this.parserForOldInUpdates = null;
      }
      if (dumper != null)
         dumper.close();
      dumper = null;
   }

   /**
    * Checks weather an entry has already been processed, in which case it will not be processed anymore
    * @param dbInfo
    * @return
    */
   private boolean checkIfAlreadyProcessed(SqlInfo dbInfo) {
      ClientProperty prop = dbInfo.getDescription().getAttribute(ReplicationConstants.ALREADY_PROCESSED_ATTR);
      if (prop != null)
         return true;
      List rows = dbInfo.getRows();
      for (int i=0; i < rows.size(); i++) {
         SqlRow row = (SqlRow)rows.get(i);
         prop = row.getAttribute(ReplicationConstants.ALREADY_PROCESSED_ATTR);
         if (prop != null)
            return true;
      }
      return false;
   }
   
   
   public void store(SqlInfo dbInfo) throws Exception {
      if (checkIfAlreadyProcessed(dbInfo)) {
         log.info("Entry '" + dbInfo.toString() + "' already processed, will ignore it");
         return;
      }
      // TODO STORE THE ENTRY HERE
      final String extraOffset = "";
      final boolean doTruncate = false;
      final boolean forceReadable = true;
      final boolean omitDecl = true;
      if (dbInfo == null)
         return;
      StringBuffer buf = new StringBuffer(512);
      buf.append("\n<!-- currentTimestamp ").append(System.currentTimeMillis()).append(" -->\n");
      dumper.write(buf.toString());
      dumper.write(dbInfo.toXml(extraOffset, doTruncate, forceReadable, omitDecl));
      count++;
      if (count == backupEvery) {
         count = 0;
         dumperBackup();
      }
   }

   private final String getCompleteFileName(String filename) {
      return this.importLocation + File.separator + filename;
   }
   
   private void deleteFiles(String filename) {
      String completeFilename = getCompleteFileName(filename);
      if (!this.keepDumpFiles) {
         File fileToDelete = new File(completeFilename);
         boolean del = fileToDelete.delete();
         if (!del)
            log.warning("could not delete the file '" + completeFilename + "' please delete it manually");
      }
   }
   
   private void deleteFiles(Map attrMap) {
      ClientProperty filenameProp =  (ClientProperty)attrMap.get(ReplicationConstants.FILENAME_ATTR);
      String filename = null;
      if (filenameProp != null)
         filename = filenameProp.getStringValue();
      if (filename != null && filename.length() > 0) {
         deleteFiles(filename);
      }
      else
         log.warning("Could not cleanup since the '" + ReplicationConstants.FILENAME_ATTR + "' attribute was not set");
   }
   
   /**
    * This is invoked for dump files
    */
   private void updateDump(String topic, InputStream is, Map attrMap) throws Exception {
      ClientProperty prop = (ClientProperty)attrMap.get(FILENAME_ATTR);
      String filename = null;
      if (prop == null) {
         log.warning("The property '" + FILENAME_ATTR + "' has not been found. Will choose an own temporary one");
         filename = "tmpFilename.dmp";
      }
      else 
         filename = prop.getStringValue();
      log.info("'" + topic + "' dumping file '" + filename + "' on '" + this.importLocation + "'");
      // will now write to the file system
      this.callback.update(topic, is, attrMap);
      // and now perform an import of the DB
      int seqNumber = -1;
      String exTxt = "";
      log.info("'" + topic + "' dumped file '" + filename + "' on '" + this.importLocation + "' seq nr. '" + seqNumber + "' ex='" + exTxt + "'");
   }

   private void updateManualTransfer(String topic, InputStream is, Map attrMap) throws Exception {
   }

   
   public void update(String topic, InputStream is, Map attrMap) throws Exception {
      ClientProperty dumpProp = (ClientProperty)attrMap.get(ReplicationConstants.DUMP_ACTION);
      ClientProperty endToRemoteProp = (ClientProperty)attrMap.get(ReplicationConstants.INITIAL_DATA_END_TO_REMOTE);
      ClientProperty endOfTransition = (ClientProperty)attrMap.get(ReplicationConstants.END_OF_TRANSITION);

      if (endOfTransition != null && endOfTransition.getBooleanValue()) {
         deleteFiles(attrMap);
      }
      else if (dumpProp != null)
         updateDump(topic, is, attrMap);
      else if (endToRemoteProp != null)
         updateManualTransfer(topic, is, attrMap);
      else
         log.severe("Unknown operation");
      
   }
   
}
