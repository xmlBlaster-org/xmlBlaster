package javaclients.simplereader;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.logging.Logger;

import org.xmlBlaster.client.key.UpdateKey;
import org.xmlBlaster.client.qos.UpdateQos;
import org.xmlBlaster.util.EncodableData;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.Constants;
import org.xmlBlaster.util.def.ErrorCode;

public class FileDumper {
   private static String ME = "FileDumper";
   private static Logger log = Logger.getLogger(FileDumper.class.getName());
   private Global glob;
   private String directoryName;
   /** forceBase64==false: ASCII dump for content if possible (XML embedable) */
   private boolean forceBase64 = false;
   
   public FileDumper(Global glob) throws XmlBlasterException {
      this.glob = glob;
      String defaultPath = System.getProperty("user.home") + System.getProperty("file.separator") + "FileDumper";
      this.directoryName = this.glob.getProperty().get("directoryName", defaultPath);
      initDirectory(null, "directoryName", this.directoryName);
      
      log.info("Dumping occurrences of topic '" + Constants.OID_DEAD_LETTER + "' to directory " + this.directoryName);
      this.forceBase64 = this.glob.getProperty().get("forceBase64", this.forceBase64);
   }
   
   /**
    * Dump dead message to hard disk. 
    * The file name is the receive timestamp of the message, for example
    * <tt>/home/xmlblast/tmp/2004-10-23_18_52_39_87.xml</tt>
    */                     
   public void dumpMessage(UpdateKey updateKey, byte[] content, UpdateQos updateQos) {
      try {
         String fn = updateQos.getRcvTime();
         String key = updateKey.toXml();
         String qos = updateQos.toXml();
         String oid = updateKey.getOid();

         fn = Global.getStrippedString(fn); // Strip chars like ":" so that fn is usable as a file name
         fn = fn + ".xml";

         initDirectory(null, "directoryName", this.directoryName); // In case somebody has removed it
         File to_file = new File(this.directoryName, fn);

         FileOutputStream to = new FileOutputStream(to_file);
         log.info("Dumping dead message to  '" + to_file.toString() + "'" );

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
         log.severe("Dumping of message failed: " + updateQos.toXml() + updateKey.toXml() + new String(content));
      }
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
}
