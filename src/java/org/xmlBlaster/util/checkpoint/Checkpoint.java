/**
 *
 */
package org.xmlBlaster.util.checkpoint;

import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.util.SessionName;
import org.xmlBlaster.util.StringPairTokenizer;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.context.ContextNode;
import org.xmlBlaster.util.def.Constants;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.util.plugin.PluginInfo;
import org.xmlBlaster.util.qos.ClientProperty;

/**
 * Plugin to trace the message flow into log files.
 * <p>
 * Currently there are 3 checkpoints defined inside the xmlBlaster core (see
 * I_Checkpoint.java).<br />
 * You can add a client property (e.g. "wfguid") to your PublishQos and register
 * this here with filterClientPropertyKey="wfguid". When such a marked message
 * passes one of the checkpoints a logging entry will be written to a log file
 * (as configured in logging.properties). See logging.properties for examples
 * on how to control which checkpoints are logged and if the complete message
 * shall be dumped.
 * <p>
 * Is adjustable during runtime using JMX (jconsole)
 * <p>
 * Needs to be loaded in xmlBlasterPlugins.xml at run level 1:
 *
 * <pre>
 *  &lt;plugin create='true' id='Checkpoint' className='org.xmlBlaster.util.checkpoint.Checkpoint'&gt;
 *  &lt;action do='LOAD' onStartupRunlevel='1' sequence='1'
 *  onFail='resource.configuration.pluginFailed'/&gt;
 *  &lt;action do='STOP' onShutdownRunlevel='0' sequence='1'/&gt;
 *  &lt;attribute id='filterClientPropertyKey'&gt;wfguid&lt;/attribute&gt;
 *  &lt;attribute id='xmlStyle'&gt;true&lt;/attribute&gt;
 *  &lt;attribute id='showAllMessages'&gt;false&lt;/attribute&gt;
 *  &lt;attribute id='showAllClientProperties'&gt;false&lt;/attribute&gt;
 *  &lt;/plugin&gt;
 * </pre>
 *
 * If showAllMessages is set to true all messages are logged and the filter is
 * ignored.
 * <p>
 * A typical log entry looks like this:
 *
 * <pre>
 * Oct 20, 2007 7:14:29 PM org.xmlBlaster.util.checkpoint.Checkpoint passingBy
 *  INFO: &lt;cp&gt;publish.ack&lt;/cp&gt; &lt;topicId&gt;Hello&lt;/topicId&gt; &lt;wfguid&gt;234345667777&lt;/wfguid&gt; &lt;sender&gt;client/Publisher/1&lt;/sender&gt;
 * </pre>
 *
 * or if you set xmlStyle to false like this:
 *
 * <pre>
 * Oct 20, 2007 7:26:46 PM org.xmlBlaster.util.checkpoint.Checkpoint passingBy
 *  INFO: [cp=update.ack] [topicId=Hello] [wfguid=234345667777] [sender=client/Publisher/1] [destination=client/Subscribe/1]
 * </pre>
 *
 * You can extend this class and change the output style by overwriting the
 * method append()
 *
 * @author <a href="mailto:xmlBlaster@marcelruff.info">Marcel Ruff</a>
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/admin.checkpoint.html">The admin.checkpoint requirement</a>
 */
public class Checkpoint implements I_Checkpoint {
   private String ME = "Checkpoint";

   private Global glob;

   private static Logger log = Logger.getLogger(Checkpoint.class.getName());

   protected Logger[] loggers = new Logger[I_Checkpoint.CP_NAMES.length];

   /** xmlBlasterPlugins.xml awareness */
   private PluginInfo pluginInfo;

   private boolean running;

   private boolean shutdown;

   /** My JMX registration */
   protected Object mbeanHandle;

   protected ContextNode contextNode;

   /** If given will be used to filter which messages are logged */
   protected String filterClientPropertyKey;

   protected boolean showAllClientProperties;

   /** If false show only messages marked by filterClientPropertyKey=wfguid */
   protected boolean showAllMessages;

   protected boolean cluster;

   protected boolean xmlStyle = true;

   /*
    * @see org.xmlBlaster.util.checkpoint.I_Checkpoint#passing
    */
   public void passingBy(int checkpoint, MsgUnit msgUnit,
         SessionName destination, String[] context) {
      if (!this.isActive())
         return;
      try {
         if (checkpoint < 0 || checkpoint >= CP_NAMES.length) {
            log.severe("Internal problem: checkpoint=" + checkpoint
                  + " is not known");
            return;
         }
         if (msgUnit == null)
            return;

         Logger l = loggers[checkpoint];

         ClientProperty cp = (this.filterClientPropertyKey.length() == 0) ? null
               : msgUnit.getQosData().getClientProperty(
                     this.filterClientPropertyKey);

         if (showAllMessages || cp != null) {
            boolean finest = l.isLoggable(Level.FINEST);
            if (l.isLoggable(Level.INFO)) {
               StringBuffer buf = new StringBuffer(2048);
               append(buf, "cp", CP_NAMES[checkpoint]);
               append(buf, "topicId", msgUnit.getKeyOid());
               if (this.showAllClientProperties) {
                  Iterator it = msgUnit.getQosData().getClientProperties()
                        .keySet().iterator();
                  while (it.hasNext()) {
                     String key = (String) it.next();
                     String value = msgUnit.getQosData().getClientProperty(key,
                           "");
                     append(buf, key, value);
                  }
               } else if (cp != null) {
                  append(buf, this.filterClientPropertyKey, cp.getStringValue());
               }
               append(buf, "sender", (this.cluster) ? msgUnit.getQosData()
                     .getSender().getAbsoluteName() : msgUnit.getQosData()
                     .getSender().getRelativeName());
               if (destination != null) {
                  append(buf, "destination", (this.cluster) ? destination
                        .getAbsoluteName() : destination.getRelativeName());
               }
               if (finest) {
                  buf.append(" ").append(msgUnit.toXml("", true));
                  l.finest(buf.toString());
               } else {
                  l.info(buf.toString());
               }
            }
         }
      } catch (Throwable e) {
         e.printStackTrace();
         log.severe(e.toString());
      }
   }

   /**
    * Format the key/value output. Default is xml style
    *
    * @param buf
    * @param key
    * @param value
    */
   protected void append(StringBuffer buf, String key, String value) {
      if (xmlStyle) {
         buf.append("<").append(key).append(">");
         buf.append(value);
         buf.append("</").append(key).append("> ");
      } else {
         buf.append("[").append(key).append("=");
         buf.append(value).append("] ");
      }
   }

   /*
    * (non-Javadoc)
    *
    * @see org.xmlBlaster.util.plugin.I_Plugin#init(org.xmlBlaster.util.Global,
    *      org.xmlBlaster.util.plugin.PluginInfo)
    */
   public void init(Global glob, PluginInfo pluginInfo)
         throws XmlBlasterException {
      this.shutdown = false;
      this.pluginInfo = pluginInfo;
      this.glob = glob;
      this.ME = getType();
      org.xmlBlaster.engine.ServerScope engineGlob = (org.xmlBlaster.engine.ServerScope) glob
            .getObjectEntry(Constants.OBJECT_ENTRY_ServerScope);
      if (engineGlob == null)
         throw new XmlBlasterException(this.glob, ErrorCode.INTERNAL_UNKNOWN,
               ME + ".init",
               "could not retreive the ServerNodeScope. Am I really on the server side ?");

      this.filterClientPropertyKey = glob.get("filterClientPropertyKey",
            "wfguid", null, this.pluginInfo);
      this.xmlStyle = glob
            .get("xmlStyle", this.xmlStyle, null, this.pluginInfo);
      this.showAllMessages = glob.get("showAllMessages", this.showAllMessages,
            null, this.pluginInfo);
      this.showAllClientProperties = glob.get("showAllClientProperties",
            this.showAllClientProperties, null, this.pluginInfo);

      for (int i = 0; i < loggers.length; i++) {
         String loggerName = "xmlBlaster.checkpoint." + CP_NAMES[i];
         loggers[i] = Logger.getLogger(loggerName);
         log.info("Adding logger '" + loggerName + "'");
      }

      this.cluster = engineGlob.isClusterManagerReady();

      // For JMX instanceName may not contain ","
      String vers = ("1.0".equals(getVersion())) ? "" : getVersion();
      this.contextNode = new ContextNode(ContextNode.SERVICE_MARKER_TAG,
            "Checkpoint[" + getType() + vers + "]", glob.getContextNode());
      this.mbeanHandle = this.glob.registerMBean(this.contextNode, this);

      // LoggerInformation.getInfo();
      // LoggerInformation.tryLevels(log);

      try {
         if (log.isLoggable(Level.FINE))
            log.fine("Using pluginInfo=" + this.pluginInfo.toString());
         activate();
      } catch (XmlBlasterException ex) {
         throw ex;
      } catch (Throwable ex) {
         throw new XmlBlasterException(this.glob, ErrorCode.INTERNAL_UNKNOWN,
               ME + ".init", "init. Could'nt initialize the driver.", ex);
      }

      engineGlob.setCheckpointPlugin(this);
   }

   /*
    * (non-Javadoc)
    *
    * @see org.xmlBlaster.util.plugin.I_Plugin#getType()
    */
   public String getType() {
      return (this.pluginInfo == null) ? "Checkpoint" : this.pluginInfo
            .getType();
   }

   /*
    * (non-Javadoc)
    *
    * @see org.xmlBlaster.util.plugin.I_Plugin#getVersion()
    */
   public String getVersion() {
      return (this.pluginInfo == null) ? "1.0" : this.pluginInfo.getVersion();
   }

   /*
    * (non-Javadoc)
    *
    * @see org.xmlBlaster.util.plugin.I_Plugin#shutdown()
    */
   public void shutdown() throws XmlBlasterException {
      this.shutdown = true;
   }

   /*
    * (non-Javadoc)
    *
    * @see org.xmlBlaster.util.checkpoint.CheckpointMBean#getCheckpointList()
    */
   public String getCheckpointList() {
      return StringPairTokenizer.arrayToCSV(CP_NAMES, ",");
   }

   /*
    * (non-Javadoc)
    *
    * @see org.xmlBlaster.util.admin.I_AdminService#activate()
    */
   public void activate() throws Exception {
      this.running = true;
      this.shutdown = false;
   }

   /*
    * (non-Javadoc)
    *
    * @see org.xmlBlaster.util.admin.I_AdminService#deActivate()
    */
   public void deActivate() {
      this.running = false;
   }

   /*
    * (non-Javadoc)
    *
    * @see org.xmlBlaster.util.admin.I_AdminService#isActive()
    */
   public boolean isActive() {
      return this.running == true;
   }

   /*
    * (non-Javadoc)
    *
    * @see org.xmlBlaster.util.admin.I_AdminPlugin#isShutdown()
    */
   public boolean isShutdown() {
      return this.shutdown;
   }

   /*
    * (non-Javadoc)
    *
    * @see org.xmlBlaster.util.admin.I_AdminUsage#getUsageUrl()
    */
   public String getUsageUrl() {
      return Global.getJavadocUrl(this.getClass().getName(), null);
   }

   /* dummy to have a copy/paste functionality in jconsole */
   public void setUsageUrl(java.lang.String url) {
   }

   /*
    * (non-Javadoc)
    *
    * @see org.xmlBlaster.util.admin.I_AdminUsage#usage()
    */
   public String usage() {
      return "Logs message flow while passing checkpoints"
            + Global.getJmxUsageLinkInfo(this.getClass().getName(), null);
   }

   /**
    * @return the showAllClientProperties
    */
   public boolean isShowAllClientProperties() {
      return this.showAllClientProperties;
   }

   /**
    * @param showAllClientProperties
    *           the showAllClientProperties to set
    */
   public void setShowAllClientProperties(boolean showAllClientProperties) {
      this.showAllClientProperties = showAllClientProperties;
   }

   public boolean isShowAllMessages() {
      return this.showAllMessages;
   }

   public void setShowAllMessages(boolean showAllMessages) {
      this.showAllMessages = showAllMessages;
   }

   /**
    * @return the filterClientPropertyKey
    */
   public String getFilter() {
      return this.filterClientPropertyKey;
   }

   /**
    * A key of a ClientProperty, only those messages containing such a
    * ClientProperty are logged
    *
    * @param filterClientPropertyKey
    *           the filterClientPropertyKey to set
    */
   public void setFilter(String filterClientPropertyKey) {
      if (filterClientPropertyKey == null)
         filterClientPropertyKey = "";
      this.filterClientPropertyKey = filterClientPropertyKey;
   }

   /**
    * @return the xmlStyle
    */
   public boolean isXmlStyle() {
      return this.xmlStyle;
   }

   /**
    * @param xmlStyle
    *           the xmlStyle to set
    */
   public void setXmlStyle(boolean xmlStyle) {
      this.xmlStyle = xmlStyle;
   }
}
