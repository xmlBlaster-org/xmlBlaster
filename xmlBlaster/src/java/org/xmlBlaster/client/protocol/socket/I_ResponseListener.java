/*------------------------------------------------------------------------------
Name:      I_ResponseListener.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Event for asynchronous response from server
Version:   $Id: I_ResponseListener.java,v 1.2 2002/02/15 12:55:36 ruff Exp $
Author:    michele.laghi@attglobal.net
------------------------------------------------------------------------------*/
package org.xmlBlaster.client.protocol.socket;

import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.engine.helper.MessageUnit;
import org.xmlBlaster.client.protocol.ConnectionException;


/**
 * @author <a href="mailto:ruff@swand.lake.de">Marcel Ruff</a>.
 */
public interface I_ResponseListener
{
   public void responseEvent(String requestId, Object response);
}

