/*------------------------------------------------------------------------------
Name:      I_MapMsgToMasterId.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Interface top the load balancing implementation
Author:    ruff@swand.lake.de
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.cluster;

import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.engine.Global;
import org.xmlBlaster.engine.MessageUnitWrapper;

/**
 * Interface to implementation which finds the master cluster node of a given message. 
 * @author ruff@swand.lake.de
 */
public interface I_MapMsgToMasterId
{
   /**
    * This is called after instantiation of the plugin 
    * @param glob The Global handle of this xmlBlaster server instance.
    */
   public void initialize(Global glob);

   /** Get a human readable name of the implementation */
   public String getName();

   /**
    * Get the content MIME types for which this plugin applies, "*" is for all mime types
    * @return The supported mime types, for example  return { "text/plain", "text/xml", "application/mytext" };
    */
   public String[] getMimeTypes();

   /**
    * Get the content MIME version number for which this plugin applies. The returned String array length must
    * be the same as this of getMimeTypes(), the index corresponds to the index of getMimeTypes().<br />
    * For example "stable" is the extended mime type of "application/mytext" (see getMimeTypes()).
    * @return E.g. a string array like  { "1.0", "1.3", "stable" }
    */
   public String[] getMimeExtended();

   public NodeId getMasterId(MessageUnitWrapper msgWrapper) throws XmlBlasterException;
}
