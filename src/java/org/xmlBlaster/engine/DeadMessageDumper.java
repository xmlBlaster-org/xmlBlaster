/*------------------------------------------------------------------------------
Name:      DeadMessageDumper.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import java.util.logging.Logger;
import java.util.logging.Level;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.EncodableData;
import org.xmlBlaster.util.def.Constants;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.util.plugin.PluginInfo;
import org.xmlBlaster.util.plugin.I_Plugin;
import org.xmlBlaster.util.Timestamp;
import org.xmlBlaster.client.XmlBlasterAccess;
import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.client.I_XmlBlasterAccess;
import org.xmlBlaster.client.key.SubscribeKey;
import org.xmlBlaster.client.key.UpdateKey;
import org.xmlBlaster.client.qos.ConnectQos;
import org.xmlBlaster.client.qos.SubscribeQos;
import org.xmlBlaster.client.qos.UpdateQos;

/**
 * Subscribes to the "__sys__deadMessage" topic and dumps the dead messages to the hard disk. 
 * <p>
 * The dumped messages are xml formatted and can be resend with {@link org.xmlBlaster.client.script.XmlScriptInterpreter}:
 * </p>
 * <pre>
 * java org.xmlBlaster.Main -xmlBlaster/acceptWrongSenderAddress/joe true
 *
 * java javaclients.script.XmlScript -prepareForPublish true -session.name joe -requestFile 2004-10-23_21_25_33_39.xml
 * </pre>
 * <p>In the above example xmlBlaster allows 'joe' to send faked sender addresses (the original ones)
 *  with the command line parameter <tt>-xmlBlaster/acceptWrongSenderAddress/joe true</tt>.</p>
 *
 * <p>
 * This <tt>DeadMessageDumper</tt> plugin is started with the run level manager as configured in xmlBlasterPlugins.xml,
 * for example:</p>
 * <pre>
 *  &lt;plugin id='DeadMessageDumper' className='org.xmlBlaster.engine.DeadMessageDumper'>
 *     &lt;action do='LOAD' onStartupRunlevel='7' sequence='1'
 *                          onFail='resource.configuration.pluginFailed'/>
 *     &lt;action do='STOP' onShutdownRunlevel='6' sequence='1'/>
 *     &lt;attribute id='loginName'>_DeadMessageDumper&lt;/attribute>
 *     &lt;attribute id='password'>secret&lt;/attribute>
 *     &lt;attribute id='directoryName'>/tmp&lt;/attribute>
 *     &lt;attribute id='forceBase64'>false&lt;/attribute>
 *  &lt;/plugin>
 * </pre>
 * <p>The <tt>directorName</tt> defaults to <tt>$HOME/tmp</tt> and <tt>foceBase64=false</tt> tries to dump
 * the message content in human readable form (if the message dump xml syntax allows it).
 * If the directory does not exist, it is created automatically.</p>
 * <p>
 * We use the <tt>LOCAL</tt> protocol driver to talk to xmlBlaster, therefor this
 * plugin works only if the client and server is in the same virtual machine (JVM).
 * </p>
 *
 * @author <a href="mailto:xmlblast@marcelruff.info">Marcel Ruff</a>
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/admin.errorHandling.html">The admin.errorHandling requirement</a>
 */
public class DeadMessageDumper implements I_Plugin {

   private final static String ME = DeadMessageDumper.class.getName();
   
   private PluginInfo pluginInfo;
   private Global global;
   private static Logger log = Logger.getLogger(DeadMessageDumper.class.getName());

   private I_XmlBlasterAccess connection;
   private String directoryName;

   private String loginName;
   private String password = "secret";
   /** forceBase64==false: ASCII dump for content if possible (XML embedable) */
   private boolean forceBase64 = false;

   /**
    * Initializes the plugin
    * @see org.xmlBlaster.util.plugin.I_Plugin#init(org.xmlBlaster.util.Global, org.xmlBlaster.util.plugin.PluginInfo)
    */
   public void init(org.xmlBlaster.util.Global glob, PluginInfo pluginInfo) throws XmlBlasterException {
      this.pluginInfo = pluginInfo;
      this.global = glob.getClone(glob.getNativeConnectArgs());
      this.global.addObjectEntry("ServerNodeScope", glob.getObjectEntry("ServerNodeScope"));


      if (log.isLoggable(Level.FINER)) this.log.finer("init");

      String defaultPath = System.getProperty("user.home") + System.getProperty("file.separator") + "tmp";

      this.directoryName = this.global.get("directoryName", defaultPath, null, this.pluginInfo);
      initDirectory(null, "directoryName", this.directoryName);
      
      log.info("Dumping occurrences of topic '" + Constants.OID_DEAD_LETTER + "' to directory " + this.directoryName);

      this.loginName = this.global.get("loginName", ME, null, this.pluginInfo);
      this.password = this.global.get("password", this.password, null, this.pluginInfo);
      this.forceBase64 = this.global.get("forceBase64", this.forceBase64, null, this.pluginInfo);

      subscribeToDeadMessages();
   }

   /**
    * @return the plugin type, defaults to "DeadMessageDumper"
    * @see org.xmlBlaster.util.plugin.I_Plugin#getType()
    */
   public String getType() {
      if (this.pluginInfo != null) return this.pluginInfo.getType();
      return ME;
   }

   /**
    * @return the plugin version, defaults to "1.0"
    * @see org.xmlBlaster.util.plugin.I_Plugin#getVersion()
    */
   public String getVersion() {
      if (this.pluginInfo != null) return this.pluginInfo.getVersion();
      return "1.0";
   }

   /**
    * Shutdown the plugin
    * @see org.xmlBlaster.util.plugin.I_Plugin#shutdown()
    */
   public void shutdown() throws XmlBlasterException {
      if (log.isLoggable(Level.FINER)) this.log.finer("shutdown");
      if (connection != null) connection.disconnect(null);
   }

   /**
    * On startup subscribe to topic __sys__deadMessage. 
    */
   private void subscribeToDeadMessages() throws XmlBlasterException {
      try {
         final String secretCbSessionId = new Timestamp().toString();

         this.connection = new XmlBlasterAccess(this.global);

         ConnectQos connectQos = new ConnectQos(this.global, loginName, password);
         connectQos.setSecretCbSessionId(secretCbSessionId);
         // Constants.ONOVERFLOW_DISCARDOLDEST

         this.connection.connect(connectQos, new I_Callback() {
            public String update(String cbSessionId, UpdateKey updateKey, byte[] content, UpdateQos updateQos) {
               
               if (!secretCbSessionId.equals(cbSessionId)) {
                  log.warning("Ignoring received message '" + updateKey.getOid() + "' because of wrong credentials '" + cbSessionId + "'");
                  return Constants.RET_OK;
               }
               log.info("Receiving asynchronous message '" + updateKey.getOid() + "'" );

               if (Constants.OID_DEAD_LETTER.equals(updateKey.getOid())) {
                  dumpMessage(updateKey, content, updateQos);
               }
               return Constants.RET_OK;
            }
         });  // Login to xmlBlaster, default handler for updates

         SubscribeKey sk = new SubscribeKey(this.global, Constants.OID_DEAD_LETTER);
         SubscribeQos sq = new SubscribeQos(this.global);
         sq.setWantInitialUpdate(false);
         this.connection.subscribe(sk, sq);

         log.info("Subscribed to topic '" + Constants.OID_DEAD_LETTER + "'");
      }
      catch (XmlBlasterException e) {
         log.severe("Can't dump '" + Constants.OID_DEAD_LETTER + "': " + e.getMessage());
      }
   }

   /**
    * Dump dead message to hard disk. 
    * The file name is the receive timestamp of the message, for example
    * <tt>/home/xmlblast/tmp/2004-10-23_18_52_39_87.xml</tt>
    */                     
   private void dumpMessage(UpdateKey updateKey, byte[] content, UpdateQos updateQos) {
      try {
         String fn = updateQos.getClientProperty(Constants.CLIENTPROPERTY_RCVTIMESTAMP, "");      //"__rcvTimestamp"
         String key = updateQos.getClientProperty(Constants.CLIENTPROPERTY_DEADMSGKEY, "<key/>"); //"__key"
         String qos = updateQos.getClientProperty(Constants.CLIENTPROPERTY_DEADMSGQOS, "<qos/>"); //"__qos"
         String oid = updateQos.getClientProperty(Constants.CLIENTPROPERTY_OID, "");              //"__oid"
         String txt = updateQos.getClientProperty(Constants.CLIENTPROPERTY_DEADMSGREASON, "");    //"__deadMessageReason"

         fn = Global.getStrippedString(fn); // Strip chars like ":" so that fn is usable as a file name
         fn = fn + ".xml";

         initDirectory(null, "directoryName", this.directoryName); // In case somebody has removed it
         File to_file = new File(this.directoryName, fn);

         FileOutputStream to = new FileOutputStream(to_file);
         log.info("Dumping dead message to  '" + to_file.toString() + "'" );

         StringBuffer sb = new StringBuffer(qos.length() + key.length() + 1024);
         //sb.append("<?xml version='1.0' encoding='iso-8859-1'?>");
         //sb.append("<?xml version='1.0' encoding='utf-8' ?>");

         sb.append("\n  <!-- Dump of topic '").append(oid).append("' cause:").append(" -->");
         sb.append("\n  <!-- ").append(txt).append(" -->");
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
               throw new XmlBlasterException(this.global, ErrorCode.RESOURCE_FILEIO, ME, "could not create directory '" + absDirName + "'");
         }
         if (!dir.isDirectory()) {
            throw new XmlBlasterException(this.global, ErrorCode.RESOURCE_FILEIO, ME, "'" + dir.getAbsolutePath() + "' is not a directory");
         }
         if (!dir.canRead())
            throw new XmlBlasterException(this.global, ErrorCode.RESOURCE_FILEIO, ME + ".constructor", "no rights to read from the directory '" + dir.getAbsolutePath() + "'");
         if (!dir.canWrite())
            throw new XmlBlasterException(this.global, ErrorCode.RESOURCE_FILEIO, ME + ".constructor", "no rights to write to the directory '" + dir.getAbsolutePath() + "'");
      }
      else {
         log.info("Constructor: the '" + propName + "' property is not set. Instead of moving concerned entries they will be deleted");
      }
      return dir;
   }
}
