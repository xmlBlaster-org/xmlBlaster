/*------------------------------------------------------------------------------
Name:      PluginConfig.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.runlevel;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.Timestamp;
import org.xmlBlaster.util.enum.ErrorCode;
import org.xmlBlaster.util.enum.Constants;

import java.util.Vector;
import java.util.Properties;
import java.util.Enumeration;
import java.util.HashSet;
import org.xmlBlaster.util.plugin.PluginInfo;

/**
 * This class contains the information on how to configure a certain pluginand when a certain plugin is invoked by the run level manager
 * <p>
 * @author <a href="mailto:laghi@swissinfo.org">Michele Laghi</a>
 * @author <a href="mailto:xmlBlaster@marcelruff.info">Marcel Ruff</a>
 * @see <a href="http://www.xmlblaster.org/xmlBlaster/doc/requirements/engine.runlevel.html">engine.runlevel requirement</a>
 * <pre>
 *
 *  &lt;plugin id='storage:CACHE' className='org.xmlBlaster.engine.msgstore.cache.PersistenceCachePlugin'>
 *     &lt;attribute id='transientQueue'>storage:RAM&lt;/attribute>
 *     &lt;attribute id='persistentQueue'>storage:JDBC&lt;/attribute>
 *  &lt;/plugin>
 *
 * </pre>
 */
public class PluginConfig
{
   private String ME = "PluginConfig";
   private final Global glob;
   private final LogChannel log;

   /** the id specifying a given plugin configuration */
   private String id = "";

   /** the complete class name for the plugin to be loaded */
   private String className = "";

   /**the coloumn separated list of jar files on which to look for the class */
   private String jarPath;

   /* the actions to trigger (all actions are put here) */
   private Vector actions;

   /** stores the action for the upgoing run level (if none it will be null) */ 
   private RunLevelAction upAction;

   /** stores the action for the down going run level (if none it will be null) */ 
   private RunLevelAction downAction;

   /** the properties for the plugin */
   private Properties attributes;

   /* Set containing all attributes which are wrapped inside a CDATA */
   private HashSet wrappedAttributes; 

   /** timestamp used to get uniquity (since runlevel + sequeuence is not unique) */
   Timestamp uniqueTimestamp;

   /**
    * This constructor takes all parameters needed
    */
   public PluginConfig(Global glob, String id, String className, String jar, Properties attributes, Vector actions) {
      this.uniqueTimestamp = new Timestamp();
      this.glob = glob;
      this.log = this.glob.getLog("runlevel");
      if (this.log.CALL) this.log.call(ME, "constructor");
      if (this.log.TRACE) 
         this.log.trace(ME, "constructor id='" + id + "', className='" + className + "'");
      this.id = id;
      this.className = className;
      this.jarPath = jar;

      if (attributes != null) this.attributes = attributes;
      else this.attributes = new Properties();

      if (actions != null) this.actions = actions;
      else this.actions = new Vector();
      this.wrappedAttributes = new HashSet();
   }

   /**
    * Construtor where we can define attributes (no need to define actions)
    */
   public PluginConfig(Global glob, String id, String className, Properties attributes) {
      this(glob, id, className, (String)null, attributes, (Vector)null);
   }

   /**
    * Minimal constructor
    */
   public PluginConfig(Global glob, String id, String className) {
      this(glob, id, className, (String)null, (Properties)null, (Vector)null);
   }

   /**
    * Really minimal constructor
    */
   public PluginConfig(Global glob) {
      this(glob, "", "", (String)null, (Properties)null, (Vector)null);
   }

   public String getId() {
      return this.id;
   }

   public String getClassName() {
      return this.className;
   }

   public void addAction(RunLevelAction action) {
      if (action == null ) {
         this.log.warn(ME, "addAction the action is null");
         return;
      }
      if (action.getOnStartupRunlevel() > -1) this.upAction = action;
      else if (action.getOnShutdownRunlevel() > -1) this.downAction = action;
      // adds it also to the common actions (in future there could be more action types)
      this.actions.add(action);
   }

   public RunLevelAction getUpAction() {
      return this.upAction;
   }

   public RunLevelAction getDownAction() {
      return this.downAction;
   }

   public void addAttribute(String key, String value) {
      this.attributes.setProperty(key, value);
   }

   public RunLevelAction[] getActions() {
      return (RunLevelAction[])this.actions.toArray(new RunLevelAction[this.actions.size()]);
   }

   public void setId(String id) {
      if (id != null) this.id = id;
   }

   public void setClassName(String className) {
      if (className != null) this.className = className;
   }

   public void setJar(String jar) {
      if (jar != null) this.jarPath = jar;
   }

   /**
    * returns the PluginInfo object out of this configuration
    */
   public PluginInfo getPluginInfo() {
      return new PluginInfo(this.glob, this.id, this.className, this.attributes);
   }

   /**
    * When the attribute is written to a string in the toXml methods it is wrapped inside a CDATA in case
    * you pass 'true' here.
    */
   public void wrapAttributeInCDATA(String attributeKey) {
      this.wrappedAttributes.add(attributeKey);
   }

   /**
    * When the attribute is written to a string in the toXml methods it is wrapped inside a CDATA. This can
    * be undone if you pass 'true' here.
    */
   public void unwrapAttributeFromCDATA(String attributeKey) {
      this.wrappedAttributes.remove(attributeKey);
   }

   /**
    * returns an xml litteral string representing this object.
    */
   public String toXml(String extraOffset) {
      StringBuffer sb = new StringBuffer(512);
      if (extraOffset == null) extraOffset = "";
      String offset = Constants.OFFSET + extraOffset;

      sb.append(offset).append("<plugin ");
      sb.append("id='").append(this.id).append("' ");
      sb.append("className='").append(this.className).append("' ");
      if (this.jarPath !=null) {
         sb.append("jar='").append(this.jarPath).append("' ");
      }
      sb.append(">");

      // and now the child elements (first attributes and then actions)
      String offset2 = offset + "   ";
      Enumeration enum = this.attributes.keys();
      while (enum.hasMoreElements()) {
         String key =(String)enum.nextElement();
         String value = this.attributes.getProperty(key);
         sb.append(offset2).append("<attribute id='").append(key).append("'>");
         if (this.wrappedAttributes.contains(key)) {
            sb.append("<![CDATA[").append(value).append("]]>");
         }
         else sb.append(value);
         sb.append("</attribute>");
      }

      enum = this.actions.elements();
      while (enum.hasMoreElements()) {
         RunLevelAction value = (RunLevelAction)enum.nextElement();
         sb.append(value.toXml(extraOffset + "   "));
      }
      sb.append(offset).append("</plugin>");
      return sb.toString();
   }

   public String toXml() {
      return toXml("");
   }
}
