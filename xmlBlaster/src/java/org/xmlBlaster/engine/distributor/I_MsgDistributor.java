/*------------------------------------------------------------------------------
Name:      I_MsgDistributor.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.distributor;

import org.xmlBlaster.authentication.SessionInfo;
import org.xmlBlaster.engine.MsgUnitWrapper;
import org.xmlBlaster.engine.TopicHandler;
import org.xmlBlaster.util.dispatch.I_ConnectionStatusListener;

/**
 * I_MsgDistributor
 *
 * @author <a href="mailto:laghi@swissinfo.org">Michele Laghi</a>
 * 
 */
public interface I_MsgDistributor extends I_ConnectionStatusListener {

   boolean syncDistribution(TopicHandler topicHandler, SessionInfo publisherSessionInfo, MsgUnitWrapper msgUnitWrapper);

   void handleError(TopicHandler topicHandler, SessionInfo publisherSessionInfo, MsgUnitWrapper msgUnitWrapper);
}
