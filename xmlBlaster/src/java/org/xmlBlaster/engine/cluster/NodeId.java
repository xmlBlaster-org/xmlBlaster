/*------------------------------------------------------------------------------
Name:      NodeId.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Holds the unique name of a cluster node
Author:    ruff@swand.lake.de
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.cluster;

/**
 * Holds the unique name of an xmlBlaster server instance (= cluster node)
 * @author ruff@swand.lake.de 
 * @since 0.79e
 * @url http://www.xmlBlaster.org/xmlBlaster/doc/requirements/cluster.html
 */
public class NodeId {
   public String getId(){
         return id;
      }

   public void setId(String id){
         this.id = id;
      }

   private String id;
}
