/*------------------------------------------------------------------------------
Name:      Util.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Handling callback over http
Version:   $Id$
------------------------------------------------------------------------------*/
package org.xmlBlaster.protocol.http;

import javax.servlet.*;
import javax.servlet.http.*;


/**
 * Common servlet methods
 * @author xmlBlaster@marcelruff.info
 */
public class Util
{
   private final String ME = "Util";


   /**
    * Get the request parameter, if not found the session is checked, if
    * not found again, the given default is returned. 
    * <br />
    * NOTE: The session check is commented out since not supported in Servlet API 2.0
    *
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

         // Experiment von Marcel
         //String val = (String)session.getAttribute(name); // !!!! only since Servlet API 2.1
         if (val == null) {
            return defaultVal;
         } else {
            return val;
         }
      }
      return strArr[0];
   }


   /**
    * 
    */
   public static final boolean getParameter(HttpServletRequest req, String name, boolean defaultVal)
   {
      Boolean b = new Boolean(getParameter(req, name, new Boolean(defaultVal).toString()));
      return b.booleanValue();
   }
}
