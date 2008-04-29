/*------------------------------------------------------------------------------
Name:      PublishRetQosWrapper.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Holding two objects, allows us to return them both.
Author:    xmlBlaster@marcelruff.info
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.cluster;

import org.xmlBlaster.client.qos.PublishReturnQos;

/**
 * This class holds the PublishReturnQos (the returned QoS of a publish() call) and
 * the NodeMasterInfo object responsible for the publish() and allows us to return
 * them both on method return. 
 * <p />
 * The only reason that we need this class is to convey back the two above objects
 * on forwardPublish() calls.
 */
public final class PublishRetQosWrapper {

   private NodeMasterInfo nodeMasterInfo;
   private PublishReturnQos publishRetQos;
   public PublishRetQosWrapper(NodeMasterInfo nodeMasterInfo, PublishReturnQos publishRetQos) {
      this.nodeMasterInfo = nodeMasterInfo;
      this.publishRetQos = publishRetQos;
   }
   public NodeMasterInfo getNodeMasterInfo() { return nodeMasterInfo; }
   public PublishReturnQos getPublishReturnQos() { return publishRetQos; }
}
