/*------------------------------------------------------------------------------
Name:      DomainToMaster.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Simple demo implementation for clustering
Author:    ruff@swand.lake.de
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.cluster.simpledomain;

import org.xmlBlaster.util.Log;
import org.xmlBlaster.util.I_Plugin;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.engine.Global;
import org.xmlBlaster.engine.MessageUnitWrapper;
import org.xmlBlaster.engine.cluster.I_MapMsgToMasterId;
import org.xmlBlaster.engine.cluster.NodeId;


/**
 * Finds the master of a message depending
 * on the <i>domain</i> attribute of the message key tag.
 * <p />
 * This is a simple demo implementation for clustering.
 * <p />
 * @author ruff@swand.lake.de 
 * @since 0.79e
 */
final public class DomainToMaster implements I_Plugin, I_MapMsgToMasterId {
   private final String ME = "DomainToMaster";
   private Global glob;
   private Log log;

   /**
    * This is called after instantiation of the plugin 
    * @param glob The Global handle of this xmlBlaster server instance.
    */
   public void initialize(Global glob) {
      this.glob = glob;
      this.log = glob.getLog();
      log.info(ME, "Mapper is initialized");
   }

   /**
    * This method is called by the PluginManager.
    * <p />
    * This xmlBlaster.properties entry example
    * <pre>
    *   MimeAccessPlugin[ContentLenFilter][1.0]=org.xmlBlaster.engine.mime.demo.ContentLenFilter,DEFAULT_MAX_LEN=200
    * </pre>
    * passes 
    * <pre>
    *   options[0]="DEFAULT_MAX_LEN"
    *   options[1]="200"
    * </pre>
    * <p/>
    * @param String[] Some arguments from xmlBlaster.properties.
    */
   public void init(String[] options) throws XmlBlasterException {
      if (options != null) {
         for (int ii=0; ii<options.length-1; ii++) {
            if (options[ii].equalsIgnoreCase("DUMMY")) {
               //DUMMY = (new Long(options[++ii])).longValue();  // ...
            }
         }
      }
   }

   /**
    * Return plugin type for Plugin loader
    * @return "SimpleDomainToMasterMapper"
    */
   public String getType() {
      return "SimpleDomainToMasterMapper";
   }

   /**
    * Return plugin version for Plugin loader
    * @return "1.0"
    */
   public String getVersion() {
      return "1.0";
   }

   /**
    * Get the content MIME type for which this plugin applies
    * @return "*" This plugin handles all mime types
    */
   public String[] getMimeTypes() {
      String[] mimeTypes = { "*" };
      return mimeTypes;
   }

   /**
    * Get the content MIME version number for which this plugin applies
    * @return "1.0" (this is the default version number)
    */
   public String[] getMimeExtended() {
      String[] mimeExtended = { org.xmlBlaster.util.XmlKeyBase.DEFAULT_contentMimeExtended }; // "1.0"
      return mimeExtended;
   }

   /**
    * Get a human readable name of this filter implementation
    * @return "SimpleDomainToMasterMapper"
    */
   public String getName() {
      return "SimpleDomainToMasterMapper";
   }

   public NodeId getMasterId(MessageUnitWrapper msgWrapper) throws XmlBlasterException {
      Log.error(ME, "getMasterId() not implemented");
      // here is the implementation of cluster logic ...
      return new NodeId();
   }
}
