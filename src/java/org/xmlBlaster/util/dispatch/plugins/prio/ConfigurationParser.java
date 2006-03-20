/*------------------------------------------------------------------------------
Name:      ConfigurationParser.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.dispatch.plugins.prio;

import java.util.logging.Logger;
import java.util.logging.Level;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.dispatch.ConnectionStateEnum;

import org.xml.sax.*;
import org.xml.sax.helpers.*;

import java.util.Vector;
import java.util.Iterator;
import java.util.Map;
import java.util.HashMap;


/**
 * Parsing the configuration parameter of the priority based message selection plugin. 
 * <p />
 * Example:<p />
 * <pre>
 * &lt;msgDispatch type='Priority' version='1.0' defaultStatus='64k' defaultAction='send'>
 *   &lt;onStatus oid='_bandwidth.status' content='64k' defaultAction='destroy'>
 *     &lt;action do='send'  ifPriority='7-9'/>
 *     &lt;action do='queue'  ifPriority='2-6'/>
 *   &lt;/onStatus>
 *   &lt;onStatus oid='_bandwidth.status' content='2M'>
 *     &lt;action do='send'  ifPriority='0-9'/>
 *   &lt;/onStatus>
 *   &lt;onStatus oid='_bandwidth.status' content='down' connectionState='polling'>
 *     &lt;action do='send'  ifPriority='0-9'/>
 *   &lt;/onStatus>
 * &lt;/msgDispatch>
 *
 * <!-- Actions: "send", "queue", "destroy", "notifySender" -->
 * <!-- Default settings are: defaultStatus=null and action do='send' -->
 * <!-- The plugin type and version are currently optional and not checked -->
 * </pre>
 * @see org.xmlBlaster.test.dispatch.ConfigurationParserTest
 * @author xmlBlaster@marcelruff.info
 */
public class ConfigurationParser extends org.xmlBlaster.util.SaxHandlerBase
{
   private String ME = "ConfigurationParser";
   private final Global glob;
   private static Logger log = Logger.getLogger(ConfigurationParser.class.getName());

   // helper flags for SAX parsing
   private boolean inMsgDispatch = false; // parsing inside <msgDispatch> ?
   private boolean inAction = false;      // parsing inside <action> ?
   private boolean inOnStatus = false;    // parsing inside <onStatus> ?

   private DispatchAction defaultAction;
   private String defaultStatus = null;

   private StatusConfiguration statusConfiguration = null;

   /** key='status message content',  value='StatusConfiguration instances' */
   private final Map configurationContentMap = new HashMap();

   private String pluginType;
   private String pluginVersion;

   /**
    * @param the XML based ASCII string
    */
   public ConfigurationParser(Global glob, String xmlLiteral) throws XmlBlasterException {
      super(glob);
      // if (log.isLoggable(Level.FINE)) log.trace(ME, "\n"+xmlLiteral);
      this.glob = glob;

      this.defaultAction = new DispatchAction(glob, DispatchAction.SEND);
      parseIt(xmlLiteral);
   }

   /**
    */
   private final void parseIt(String xmlLiteral) throws XmlBlasterException {
      if (xmlLiteral == null || xmlLiteral.length() < 1)
         return;
      init(xmlLiteral);      // use SAX parser to parse it
   }

   /**
    * Default action is "send"
    */
   public final DispatchAction getDefaultDispatchAction() {
      return this.defaultAction;
   }

   /**
    * Is null if not set with xml configuration
    */
   public final String getDefaultStatus() {
      return this.defaultStatus;
   }

   /**
    * Invoked from parser if new configuration is available. 
    */
   void addStatusConfiguration(StatusConfiguration conf) {
      synchronized (configurationContentMap) {
         configurationContentMap.put(conf.getContent(), conf);
      }
   }

   /**
    * Access the configuration for the given status
    * @return never null
    */
   public final StatusConfiguration getStatusConfiguration(String currStatus) {
      if (currStatus == null) currStatus = this.defaultStatus;

      if (currStatus == null) {
         if (log.isLoggable(Level.FINE)) log.fine("Current status is null, using default action '" + this.defaultAction.toString() + "'");
         return new StatusConfiguration(this.glob, null, null, null, this.defaultAction);
      }
      StatusConfiguration conf = null;
      synchronized (configurationContentMap) {
         conf = (StatusConfiguration)configurationContentMap.get(currStatus);
         if (conf == null && this.defaultStatus != null && !this.defaultStatus.equals(currStatus)) { // try again with default
            log.warning("Can't find '" + currStatus + "' configuration, using default status '" + this.defaultStatus + "'");
            currStatus = this.defaultStatus;
            conf = (StatusConfiguration)configurationContentMap.get(currStatus);
         }
      }
      if (conf == null) {
         log.warning("Can't find '" + currStatus + "' configuration, using default action '" + this.defaultAction + "'");
         return new StatusConfiguration(this.glob, null, null, null, this.defaultAction);
      }
      if (log.isLoggable(Level.FINE)) log.fine("Current status '" + currStatus + "' with configuration: " + conf.toXml(""));
      return conf;
   }

   /**
    * Access the configuration for the given state of the dispatcher connection
    * @return null if no configuration is found
    * @see org.xmlBlaster.util.dispatch.DispatchConnectionsHandler
    */
   public final StatusConfiguration getStatusConfiguration(ConnectionStateEnum currConnectionState) {
      if (currConnectionState == null) {
         if (log.isLoggable(Level.FINE)) log.fine("Current connection state is null, returning null");
         return null;
      }
      synchronized (configurationContentMap) {
         Iterator it = configurationContentMap.values().iterator();
         while (it.hasNext()) {
            StatusConfiguration conf = (StatusConfiguration)it.next();
            if (conf.getConnectionState() == currConnectionState) {
               if (log.isLoggable(Level.FINE)) log.fine("Found configuration for connection state '" + currConnectionState + "'");
               return conf;
            }
         }
      }
      if (log.isLoggable(Level.FINE)) log.fine("Can't find '" + currConnectionState + "' configuration, returning null");
      return null;
   }

   /**
    * Access the internal map holding all status configurations. 
    * <p />
    * You need to synchronize on it on usage.
    * @return map with key='status message content',  value='StatusConfiguration instances'
    */
   public final Map getStatusConfigurationMap() {
      return this.configurationContentMap;
   }

   /**
    * Start element, event from SAX parser.
    * <p />
    * @param name Tag name
    * @param attrs the attributes of the tag
    */
   public final void startElement(String uri, String localName, String name, Attributes attrs) {

      if (name.equalsIgnoreCase("msgDispatch")) {
         inMsgDispatch = true;
         if (attrs != null) {
            int len = attrs.getLength();
            for (int i = 0; i < len; i++) {
               if (attrs.getQName(i).equalsIgnoreCase("type")) {
                  this.pluginType = attrs.getValue(i).trim();
               }
               else if (attrs.getQName(i).equalsIgnoreCase("version")) {
                  this.pluginVersion = attrs.getValue(i).trim();
               }
               else if (attrs.getQName(i).equalsIgnoreCase("defaultStatus")) {
                  this.defaultStatus = attrs.getValue(i).trim();
               }
               else if (attrs.getQName(i).equalsIgnoreCase("defaultAction")) {
                  this.defaultAction = new DispatchAction(glob, attrs.getValue(i).trim());
               }
               else {
                  log.warning("Ignoring unknown attribute <msgDispatch " + attrs.getQName(i) + "='" + attrs.getValue(i) + "'>");
               }
            }
         }
         return;
      }

      if (name.equalsIgnoreCase("onStatus")) {
         if (!inMsgDispatch)
            return;
         inOnStatus = true;
         String oid = null;
         String content = null;
         ConnectionStateEnum connectionState = null; // The curr state of the dispatcher framework
         DispatchAction defAct = this.defaultAction;
         if (attrs != null) {
            int len = attrs.getLength();
            for (int i = 0; i < len; i++) {
               if( attrs.getQName(i).equalsIgnoreCase("oid") ) {
                  oid = attrs.getValue(i).trim();
               }
               else if( attrs.getQName(i).equalsIgnoreCase("content") ) {
                  content = attrs.getValue(i).trim();
               }
               else if( attrs.getQName(i).equalsIgnoreCase("connectionState") ) {
                  connectionState = ConnectionStateEnum.parseConnectionState(attrs.getValue(i).trim());
               }
               else if( attrs.getQName(i).equalsIgnoreCase("defaultAction") ) {
                  defAct = new DispatchAction(glob, attrs.getValue(i).trim());
               }
               else {
                  log.warning("Ignoring unknown attribute <onStatus " + attrs.getQName(i) + "='" + attrs.getValue(i) + "'>");
               }
            }
         }
         statusConfiguration = null;
         if (oid == null && connectionState == null) {
            throw new IllegalArgumentException(ME+": Missing oid attribute in <onStatus content='" + content + "'> tag (or specify 'connectionState')");
         }
         if (content == null && connectionState == null) {
            throw new IllegalArgumentException(ME+":Missing content attribute in <onStatus oid='" + oid + "'> tag (or specify 'connectionState')");
         }
         statusConfiguration = new StatusConfiguration(glob, oid, content, connectionState, defAct);
         addStatusConfiguration(statusConfiguration);
         return;
      }

      if (name.equalsIgnoreCase("action")) {
         if (!inOnStatus)
            return;
         inAction = true;
         if (statusConfiguration == null) {
            return;
         }
         if (attrs != null) {
            int len = attrs.getLength();
            String priority = attrs.getValue("ifPriority");
            String doAttr = attrs.getValue("do");
            DispatchAction dispatchAction = new DispatchAction(glob, doAttr);
            statusConfiguration.addDispatchAction(priority, dispatchAction);
         }
         return;
      }
   }

   /**
    * End element, event from SAX parser.
    * <p />
    * @param name Tag name
    */
   public void endElement(String uri, String localName, String name) {

      if( name.equalsIgnoreCase("msgDispatch") ) {
         inMsgDispatch = false;
         character.setLength(0);
         return;
      }

      if(name.equalsIgnoreCase("action")) {
         inAction = false;
         character.setLength(0);
         return;
      }

      if(name.equalsIgnoreCase("onStatus")) {
         inOnStatus = false;
         character.setLength(0);
         return;
      }

      character.setLength(0); // reset data from unknown tags
   }
}
