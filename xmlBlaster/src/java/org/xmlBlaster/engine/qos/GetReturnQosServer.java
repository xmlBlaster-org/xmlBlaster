/*------------------------------------------------------------------------------
Name:      GetReturnQosServer.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.qos;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.qos.MsgQosData;
import org.xmlBlaster.util.enum.Constants;


/**
 * QoS (quality of service) informations sent from server to client<br />
 * via the return value of the get() method. 
 * <p />
 * The server uses this decorator to create the QoS.
 * @author xmlBlaster@marcelruff.info
 * @see org.xmlBlaster.util.qos.MsgQosData
 * @see org.xmlBlaster.util.qos.MsgQosSaxFactory
 */
public class GetReturnQosServer
{
   private String ME = "GetReturnQosServer";
   private final Global glob;
   private final MsgQosData msgQosData;

   public GetReturnQosServer(Global glob, MsgQosData msgQosData, String state) {
      this.glob = (glob==null) ? Global.instance() : glob;
      this.msgQosData = (msgQosData == null) ? new MsgQosData(glob) : msgQosData;
      this.msgQosData.setState((state == null) ? Constants.STATE_OK : state);
   }

   public MsgQosData getData() {
      return this.msgQosData;
   }

   public final String toXml() {
      return toXml((String)null);
   }

   public final String toXml(String extraOffset) {
      return this.msgQosData.toXml(extraOffset);
   }

   public final String toString() {
      return toXml((String)null);
   }
}
