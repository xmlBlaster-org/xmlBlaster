/*------------------------------------------------------------------------------
Name:      I_ResponseListener.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Event for asynchronous response from server
Version:   $Id$
Author:    xmlBlaster@marcelruff.info
------------------------------------------------------------------------------*/
package org.xmlBlaster.util;

/**
 * Used for asynchronous responses
 * @author <a href="mailto:xmlBlaster@marcelruff.info">Marcel Ruff</a>.
 */
public interface I_ResponseListener
{
   public void incomingMessage(String requestId, Object response);
}

