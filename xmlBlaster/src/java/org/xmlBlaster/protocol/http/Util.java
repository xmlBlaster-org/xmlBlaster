/*------------------------------------------------------------------------------
Name:      Util.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Handling callback over http
Version:   $Id: Util.java,v 1.2 2000/05/29 11:43:41 freidlin Exp $
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
      if (strArr == null || strArr.length < 1) {
         HttpSession session = req.getSession(false);
         if (session == null) {
            return defaultVal;
         }
         String val = (String)session.getValue(name);
         if (val == null) {
            return defaultVal;
         } else {
            return val;
         }
      }
      return strArr[0];
   }



   public static final boolean getParameter(HttpServletRequest req, String name, boolean defaultVal)
   {
      return Boolean.getBoolean(getParameter(req, name, new Boolean(defaultVal).toString()));
   }
}
