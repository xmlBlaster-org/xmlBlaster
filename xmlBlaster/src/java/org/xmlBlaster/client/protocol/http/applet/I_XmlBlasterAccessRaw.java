/*------------------------------------------------------------------------------
Name:      I_XmlBlasterRaw.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.client.protocol.http.applet;

import org.xmlBlaster.util.MsgUnitRaw;

/**
 * Interface for XmlBlaster, the supported methods on applet client side.
 * <p />
 * This allows string access, another interface allows object based access.
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/src/java/org/xmlBlaster/protocol/corba/xmlBlaster.idl" target="others">CORBA xmlBlaster.idl</a>
 * @author xmlBlaster@marcelruff.info
 */
public interface I_XmlBlasterAccessRaw
{
   public String connect(String qos, I_CallbackRaw callback) throws Exception;

   public String subscribe(java.lang.String xmlKey, java.lang.String qos) throws Exception;

   public MsgUnitRaw[] get(java.lang.String xmlKey, java.lang.String qos) throws Exception;

   public String[] unSubscribe(java.lang.String xmlKey, java.lang.String qos) throws Exception;

   public String publish(MsgUnitRaw msgUnit) throws Exception;

   public String[] erase(java.lang.String xmlKey, java.lang.String qos) throws Exception;

   public void disconnect(String qos);
}
