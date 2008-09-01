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
import java.text.DecimalFormat;
import java.text.ParseException;
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
import org.xmlBlaster.util.Execute;
import org.xmlBlaster.util.I_Timeout;
import org.xmlBlaster.util.ReplaceVariable;
import org.xmlBlaster.util.Timeout;
import org.xmlBlaster.util.qos.ClientProperty;

public class ReplicationDumper implements I_Writer, ReplicationConstants, I_Timeout {
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
   private long changeDumpFrequency = 21600000L; // default: every 6 Hours
   private long nextChangeDate;
   private long startDate;
   private String closeCmd;
   private Timeout timeout;
   private DecimalFormat format;
   private String formatTxt = "000000";
   
   public ReplicationDumper() {
      format = new DecimalFormat(formatTxt);
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
      
      
      changeDumpFrequency = info_.getLong("dumper.changeDumpFrequency", 21600000L);
      startDate = System.currentTimeMillis();
      closeCmd = info.get("replication.player.closeCmd", null);
      timeout = new Timeout("replication.streamFeeder");
      count = getInitialCount();
      if (changeDumpFrequency > 0L)
         timeout(null);
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
   
   private int getInitialCount() {
      if (dumperFilename == null)
         return -1;
      File tmpFile = new File(dumperFilename);
      File parent = tmpFile.getParentFile();
      if (parent == null) {
         log.severe("The file '" + dumperFilename + "' does not have a valid parent");
         return -1;
      }
      
      if (!parent.exists()) {
         log.severe("The file '" + parent.getAbsolutePath() + File.pathSeparator + parent.getName() + "' does not exist");
         return -1;
      }
      if (!parent.isDirectory()) {
         log.severe("The file '" + parent.getAbsolutePath() + File.pathSeparator + parent.getName() + "' is not a directory");
         return -1;
      }
      File[] childs = parent.listFiles();
      int maxVal = 0;
      for (int i=0; i < childs.length; i++) {
         int val = getIndex(childs[i].getName());
         if (val > maxVal)
            maxVal = val;
      }
      return maxVal;
   }
   
   private int getIndex(String filename) {
      if (filename == null || dumperFilename == null)
         return -1;
      int pos = filename.indexOf(dumperFilename);
      if (pos < 0)
         return -1;
      String tmp = filename.substring(pos + dumperFilename.length());
      tmp = tmp.trim();
      int length = formatTxt.length();
      if (tmp.length() > length)
         tmp = tmp.substring(0, tmp.length());
      try {
         Number num = format.parse(tmp);
         return num.intValue();
      }
      catch (NumberFormatException ex) {
         log.severe("Could not parse '" + filename + "' since '" + tmp + "' is not an allowed number " + ex.getMessage());
         return -1;
      }
      catch (ParseException ex) {
         log.severe("Could not parse '" + filename + "' since '" + tmp + "' is not an allowed number " + ex.getMessage());
         return -1;
      }
   }
   
   private synchronized void changeDumpFile() throws Exception {
      try {
         if (dumper != null) {
            // close file
            dumper.close();
            dumper = null;
            // if (Execute.isWindows()) cmd = "cmd " + cmd;
            if (closeCmd != null) {
               String tmpFilename = dumperFilename + format.format(count);
               String cmd = closeCmd + " " + tmpFilename; 
               String[] args = ReplaceVariable.toArray(cmd, " ");
               Execute execute = new Execute(args, null, 10L);
               execute.run(); // blocks until finished
               if (execute.getExitValue() != 0) {
                  throw new Exception("Exception occured on executing '" + cmd + "");
               }
            }
            // make a backup
            // copyFile(dumperFilename, dumperFilename + ".bak");
         }
         // open the stream for writing again.
         final boolean append = false;
         count++;
         String tmpFilename = dumperFilename + format.format(count);
         nextChangeDate = startDate + (changeDumpFrequency*count);
         dumper = new FileWriter(tmpFilename, append);
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
      synchronized(this) {
         if (dumper != null)
            dumper.close();
         dumper = null;
      }
   }

   public void store(SqlInfo dbInfo) throws Exception {
      // TODO STORE THE ENTRY HERE
      final String extraOffset = "";
      final boolean doTruncate = false;
      final boolean forceReadable = true;
      final boolean omitDecl = true;
      if (dbInfo == null)
         return;
      StringBuffer buf = new StringBuffer(512);
      buf.append("\n<!-- currentTimestamp ").append(System.currentTimeMillis()).append(" -->\n");
      synchronized(this) {
         dumper.write(buf.toString());
         dumper.write(dbInfo.toXml(extraOffset, doTruncate, forceReadable, omitDecl));
         dumper.flush();
         if (changeTimeReached())
            changeDumpFile();
      }
   }

   private boolean changeTimeReached() {
      return nextChangeDate < System.currentTimeMillis();
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
   
   
   /**
    * @see org.xmlBlaster.util.I_Timeout#timeout(java.lang.Object)
    */
   public void timeout(Object userData) {
      try {
         changeDumpFile();
      }
      catch (Exception ex) {
         log.severe("Exception occured: " + ex.getMessage());
         ex.printStackTrace();
      }
      finally {
         if (timeout != null)
            timeout.addTimeoutListener(this, changeDumpFrequency, null);
      }
   }
   
   
}
