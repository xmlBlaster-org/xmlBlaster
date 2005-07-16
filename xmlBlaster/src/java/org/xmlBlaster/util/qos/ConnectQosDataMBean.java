/*------------------------------------------------------------------------------
Name:      ConnectQosDataMBean.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Interface to access information about ConnectQos
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.qos;

import org.xmlBlaster.util.XmlBlasterException;

/**
 * Declares JMX available methods of a ConnectQos instance. 
 * @author xmlBlaster@marcelruff.info
 * @since 1.0.5
 * @see org.xmlBlaster.util.qos.ConnectQos
 */
public interface ConnectQosDataMBean {
   /** How often the same client may login */
   public long getMaxSessions();
   /** The configured session live span in milli seconds */
   public long getSessionTimeout();
   /** Does the client accept PtP messages? */
   public boolean isPtpAllowed();
   /** If this flag is set, the session will persist a server crash. */
   public boolean isPersistent();
}
