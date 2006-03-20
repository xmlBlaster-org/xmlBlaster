/*------------------------------------------------------------------------------
Name:      I_HttpRequest.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Event for asynchronous response from server
Version:   $Id: I_HttpRequest.java 12936 2004-11-24 20:15:11Z ruff $
Author:    xmlBlaster@marcelruff.info
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.http;

import org.xmlBlaster.util.XmlBlasterException;
import java.util.Map;

/**
 * Used for asynchronous responses
 * @author <a href="mailto:xmlBlaster@marcelruff.info">Marcel Ruff</a>.
 */
public interface I_HttpRequest
{
   /**
    * A HTTP request needs to be processed
    * @param urlPath The url path like "/monitor" which triggered this call
    * @param properties The key values from the browser
    * @return The HTML page to return
    * @throws XmlBlasterException
    */
   public HttpResponse service(String urlPath, Map properties) throws XmlBlasterException;
}

