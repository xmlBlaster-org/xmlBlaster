package org.xmlBlaster.util;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;
import java.util.logging.Logger;

import org.xmlBlaster.util.def.Constants;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.util.key.KeyData;
import org.xmlBlaster.util.qos.QosData;

/**
 * Dump message to file. 
 * The XML is in XmlScript format and can be refed.
 * <pre>
 * Configuration:
 * xmlBlaster/FileDumper/directoryName  -> ${user.home}/FileDumper
 * xmlBlaster/FileDumper/forceBase64    -> false
 * </pre>
 * @author <a href="mailto:xmlBlaster@marcelruff.info">Marcel Ruff</a>
 */
public class FileDumper {
   private static String ME = "FileDumper";
   private static Logger log = Logger.getLogger(FileDumper.class.getName());
   private Global glob;
   private String directoryName;
   /** forceBase64==false: ASCII dump for content if possible (XML embedable) */
   private boolean forceBase64 = false;
   
   public FileDumper(Global glob) throws XmlBlasterException {
	  this(glob, null, glob.getProperty().get("xmlBlaster/FileDumper/forceBase64", false));
   }
   
   public FileDumper(Global glob, String dirName, boolean forceBase64) throws XmlBlasterException {
      this.glob = glob;
      this.forceBase64 = forceBase64;
      if (dirName == null) {
    	  this.directoryName = glob.getProperty().get("xmlBlaster/FileDumper/directoryName", System.getProperty("user.home") + System.getProperty("file.separator") + "FileDumper");
          log.info("Created FileDumper instance forceBase64=" + this.forceBase64 + " to dump messages to directory xmlBlaster/FileDumper/directoryName=" + this.directoryName);
          //log.info("Dumping occurrences of topic '" + Constants.OID_DEAD_LETTER + "' forceBase64=" + this.forceBase64 + " to directory " + this.directoryName);
      }
      else {
    	  this.directoryName = dirName;
          log.info("Created FileDumper instance forceBase64=" + this.forceBase64 + " to dump messages to directory " + this.directoryName);
      }
      initDirectory(null, "directoryName", this.directoryName);
   }

   public String dumpMessage(KeyData keyData, byte[] content, QosData qosData) {
	   return dumpMessage(keyData, content, qosData, true);
   }

   /**
    * Dump dead message to hard disk. The file name is the receive timestamp of
    * the message, for example
    * <tt>/home/xmlblast/tmp/2004-10-23_18_52_39_87.xml</tt>
    * 
    * @param qosData
    *           may not be null
    * @return fileName
    */                     
   public String dumpMessage(KeyData keyData, byte[] content, QosData qosData, boolean verbose) {
      String fnStr = "";
      try {
         if (content == null)
            content = new byte[0];
         String fn = (qosData == null) ? new Timestamp().toString() : qosData.getRcvTimestampNotNull().toString();
         String key = (keyData == null) ? "" : keyData.toXml();
         Properties props = new Properties();
         if (!forceBase64)
        	 props.put(Constants.TOXML_FORCEREADABLE, ""+true);
         String qos = (qosData == null) ? "" : qosData.toXml("", props);
         String oid = (keyData == null) ? "" : keyData.getOid();

         fn = Global.getStrippedString(fn); // Strip chars like ":" so that fn is usable as a file name
         fn = fn + ".xml";

         initDirectory(null, "directoryName", this.directoryName); // In case somebody has removed it
         File to_file = new File(this.directoryName, fn);

         fnStr = to_file.getAbsolutePath();
         
         FileOutputStream to = new FileOutputStream(to_file);
         if (verbose)
        	 log.info("Dumping message to  '" + to_file.toString() + "'" );

         StringBuffer sb = new StringBuffer(qos.length() + key.length() + 1024);
         //sb.append("<?xml version='1.0' encoding='iso-8859-1'?>");
         //sb.append("<?xml version='1.0' encoding='utf-8' ?>");

         sb.append("\n  <!-- Dump of topic '").append(oid).append("' -->");
         sb.append("\n<xmlBlaster>");
         sb.append("\n <publish>");
         to.write(sb.toString().getBytes());
         sb.setLength(0);

         {
            sb.append(qos);
            sb.append(key);
            to.write(sb.toString().getBytes());
            sb.setLength(0);

            // TODO: Potential charset problem when not Base64 protected
            boolean doEncode = forceBase64;
            if (!forceBase64) {
               int len = content.length - 2;
               for (int i=0; i<len; i++) {
                  if (content[i] == (byte)']' && content[i+1] == (byte)']' && content[i+2] == (byte)'>') {
                     doEncode = true;
                     break;
                  }
               }
            }

            if (doEncode) {
               EncodableData data = new EncodableData("content", null, Constants.TYPE_BLOB, Constants.ENCODING_BASE64);
               data.setValue(content);
               data.setSize(content.length);
               to.write(data.toXml(" ").getBytes());
            }
            else {
               EncodableData data = new EncodableData("content", null, null, null);
               //String charSet = "UTF-8"; // "ISO-8859-1", "US-ASCII"
               //data.setValue(new String(content, charSet), null);
               data.setValueRaw(new String(content));
               data.forceCdata(true);
               data.setSize(content.length);
               to.write(data.toXml(" ").getBytes());
            }
         }
         {
            //MsgUnitRaw msg = new MsgUnitRaw(key, content, qos);
            //msg.toXml(" ", to);
         }

         sb.append("\n </publish>");
         sb.append("\n</xmlBlaster>");
         to.write(sb.toString().getBytes());
         to.close();
      }
      catch (Throwable e) {
         log.severe("Dumping of message failed: " + (qosData == null ? "" : qosData.toXml())
               + (keyData == null ? "" : keyData.toXml()) + new String(content));
      }
      return fnStr;
   }

   /**
    * Returns the specified directory or null or if needed it will create one
    * @param parent
    * @param propName For logging only
    * @param dirName
    * @return
    * @throws XmlBlasterException
    */
   private File initDirectory(File parent, String propName, String dirName) throws XmlBlasterException {
      File dir = null;
      if (dirName != null) {
         File tmp = new File(dirName);
         if (tmp.isAbsolute() || parent == null) {
            dir = new File(dirName);
         }
         else {
            dir = new File(parent, dirName);
         }
         if (!dir.exists()) {
            String absDirName  = null; 
            try {
               absDirName = dir.getCanonicalPath();
            }
            catch (IOException ex) {
               absDirName = dir.getAbsolutePath();
            }
            log.info("Constructor: directory '" + absDirName + "' does not yet exist. I will create it");
            boolean ret = dir.mkdir();
            if (!ret)
               throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_FILEIO, ME, "could not create directory '" + absDirName + "'");
         }
         if (!dir.isDirectory()) {
            throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_FILEIO, ME, "'" + dir.getAbsolutePath() + "' is not a directory");
         }
         if (!dir.canRead())
            throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_FILEIO, ME + ".constructor", "no rights to read from the directory '" + dir.getAbsolutePath() + "'");
         if (!dir.canWrite())
            throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_FILEIO, ME + ".constructor", "no rights to write to the directory '" + dir.getAbsolutePath() + "'");
      }
      else {
         log.info("Constructor: the '" + propName + "' property is not set. Instead of moving concerned entries they will be deleted");
      }
      return dir;
   }

public boolean isForceBase64() {
	return forceBase64;
}

public void setForceBase64(boolean forceBase64) {
	this.forceBase64 = forceBase64;
}

}
