/*------------------------------------------------------------------------------
Name:      ProxyInterceptor.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Helper to easy get the callback messages
Version:   $Id: ProxyInterceptor.java,v 1.1 2000/03/15 22:18:25 kkrafft2 Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.protocol.http;

import org.xmlBlaster.util.*;
import org.xmlBlaster.client.*;
import org.xmlBlaster.protocol.corba.serverIdl.*;
import org.xmlBlaster.protocol.corba.clientIdl.*;


/**
 * This is a interceptor for a BlasterHttpProxy connection.
 * You may derive from this interface for manipulating xmlBlaster updates
 * before sended to the Browser.
 * <p>
 *
 * @version $Revision: 1.1 $
 * @author $Author: kkrafft2 $
 */
public interface ProxyInterceptor
{
   /**
    * This update method manipulate the update content arrives from the xmlBlaster
    * before sended to the Browser.
    *
    * @param updateKey The arrived key
    * @param content   The arrived message content
    * @param qos       Quality of Service of the MessageUnit
    */
   public void update(UpdateKey updateKey, String content, UpdateQoS updateQoS);
}

