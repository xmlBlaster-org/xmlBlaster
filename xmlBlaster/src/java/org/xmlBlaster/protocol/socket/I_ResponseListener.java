/*------------------------------------------------------------------------------
Name:      I_ResponseListener.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Event for asynchronous response from server
Version:   $Id: I_ResponseListener.java,v 1.2 2002/11/26 12:39:21 ruff Exp $
Author:    ruff@swand.lake.de
------------------------------------------------------------------------------*/
package org.xmlBlaster.protocol.socket;

import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.engine.helper.MessageUnit;


/**
 * @author <a href="mailto:ruff@swand.lake.de">Marcel Ruff</a>.
 */
public interface I_ResponseListener
{
   public void responseEvent(String requestId, Object response);
}

