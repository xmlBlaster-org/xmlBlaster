/*------------------------------------------------------------------------------
Name:      Util.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Handling callback over http
Version:   $Id: Util.java,v 1.1 2000/03/03 15:48:35 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.protocol.http;

import javax.servlet.*;
import javax.servlet.http.*;


/**
 * Common servlet methods
 * @author ruff@swand.lake.de
 */
public class Util
{
   private final String ME = "Util";


   /**
    * Get the request parameter
    * @param req request from client
    * @param name parameter name
    * @param defaultVal default value if parameter not found
    * @return The value
    */
   public static final String getParameter(HttpServletRequest req, String name, String defaultVal)
   {
      String[] strArr = req.getParameterValues(name);
      if (strArr == null || strArr.length < 1)
         return defaultVal;
      return strArr[0];
   }
}
