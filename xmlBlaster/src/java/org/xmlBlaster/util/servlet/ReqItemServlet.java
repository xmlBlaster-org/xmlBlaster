/*------------------------------------------------------------------------------
Name:      ReqItemServlet.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Collects all xml requirement files into the all.xml master file
Version:   $Id: ReqItemServlet.java,v 1.1 2000/03/27 07:33:19 kkrafft2 Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.servlet;

import java.io.*;

import javax.servlet.*;
import javax.servlet.http.*;

import org.xmlBlaster.util.*;


public class ReqItemServlet extends ReqBaseServlet
{
   private static final String ME               = "ReqItemServlet";

  /**
   */
   public void init(ServletConfig conf) throws ServletException {
      super.init(conf);
   }

   /**
    */
   public void doRequest(HttpServletRequest request, HttpServletResponse response)
                       throws ServletException
   {
      try {
         String reqName = Util.getParameter(request, "id", null);
         String dir     = Util.getParameter(request, "dir", null);
         String xsl     = Util.getParameter(request, "xsl", null);

         File file = new File(dir+"/"+reqName+".xml");
         StringBuffer xmlData = new StringBuffer();


         xmlOutput( xmlData.toString(), dir, xsl, response );
      }
      catch (Exception e) {
         Log.error(ME, "Can't create requirement item: " + e.toString());
         return;
      }

   }

}

