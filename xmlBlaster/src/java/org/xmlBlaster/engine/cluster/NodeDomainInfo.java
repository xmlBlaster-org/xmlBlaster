/*------------------------------------------------------------------------------
Name:      NodeDomainInfo.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Mapping from domain informations to master id
Author:    ruff@swand.lake.de
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.cluster;

import org.xmlBlaster.engine.xml2java.XmlKey;

/**
 * Here we have the rules to find out who is the master of a message. 
 */
public class NodeDomainInfo {
   /**
    * Get the key based rules
    */
   public XmlKey[] getKeyMappings(){
         return keyMappings;
   }

   /**
    * Set a key based rule
    * @parameter XmlKey, e.g.<pre>
    *            &lt;key domain='rugby'/>
    */
   public void setKeyMappings(XmlKey[] keyMappings){
         this.keyMappings = keyMappings;
   }

   private XmlKey[] keyMappings;

   /** Unique name for logging */
   private final static String ME = "NodeDomainInfo";
}
