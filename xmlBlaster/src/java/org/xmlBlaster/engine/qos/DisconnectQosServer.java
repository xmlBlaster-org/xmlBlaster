/*------------------------------------------------------------------------------
Name:      DisconnectQosServer.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.qos;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.qos.DisconnectQosData;

/**
 * This class encapsulates the qos of a logout() or disconnect()
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.disconnect.html">The interface.disconnect requirement</a>
 * @see org.xmlBlaster.test.classtest.DisconnectQosServerTest
 */
public class DisconnectQosServer extends DisconnectQosData
{
   /**
    * Default constructor
    */
   public DisconnectQosServer(Global glob) {
      super(glob);
   }

   /**
    * Parses the given ASCII logout QoS. 
    */
   public DisconnectQosServer(Global glob, String xmlQoS_literal) throws XmlBlasterException {
      super(glob, xmlQoS_literal);
   }
}
