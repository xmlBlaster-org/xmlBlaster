/*------------------------------------------------------------------------------
Name:      I_ResponseListener.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Event for asynchronous response from server
Version:   $Id: I_ResponseListener.java,v 1.1 2003/11/04 16:47:37 laghi Exp $
Author:    xmlBlaster@marcelruff.info
------------------------------------------------------------------------------*/
package org.xmlBlaster.util;

/**
 * Used for asynchronous responses
 * @author <a href="mailto:xmlBlaster@marcelruff.info">Marcel Ruff</a>.
 */
public interface I_ResponseListener
{
   public void responseEvent(String requestId, Object response);
}

