/*------------------------------------------------------------------------------
Name:      I_ProxyInterceptor.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Helper to easy get the callback messages
Version:   $Id: I_ProxyInterceptor.java,v 1.4 2002/12/18 12:39:09 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.protocol.http;


/**
 * This is a interceptor for a BlasterHttpProxy connection.
 * You may derive from this interface for manipulating xmlBlaster updates
 * before sended to the Browser.
 * <p>
 *
 * @version $Revision: 1.4 $
 * @author $Author: ruff $
 */
public interface I_ProxyInterceptor
{
   /**
    * This update method manipulate the update content arrives from the xmlBlaster
    * before sended to the Browser.
    *
    * @param updateKey The arrived key
    * @param content   The arrived message content
    * @param qos       Quality of Service of the MsgUnitRaw
    */
   public String[] update(String updateKey, String content, String updateQos);
}

