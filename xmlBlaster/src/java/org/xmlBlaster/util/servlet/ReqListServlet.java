/*------------------------------------------------------------------------------
Name:      ReqListServlet.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Collects all xml requirement files into the all.xml master file
Version:   $Id: ReqListServlet.java,v 1.2 2000/04/06 15:10:46 kkrafft2 Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.servlet;

import java.io.*;

import javax.servlet.*;
import javax.servlet.http.*;

import org.xmlBlaster.util.*;


/*
 *
 */
public class ReqListServlet extends ReqBaseServlet
{
   private static final String ME               = "ReqListServlet";

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

         String dir_s     = Util.getParameter(request,"dir",null);
         String xslFile   = Util.getParameter(request,"xsl",null);

         File dir = new File(dir_s);
         StringBuffer xmlData = new StringBuffer();

         File[] files = dir.listFiles(new MyFilenameFilter());

         xmlData.append("<files>\n");
         xmlData.append("<dir>"+dir_s+"</dir>\n");
         for (int ii=0; ii<files.length; ii++) {
            xmlData.append("   <url>file:" + dir_s + "/" + files[ii].getName() + "</url>\n");
         }
         xmlData.append("</files>");
         Log.info(ME, "Found " + files.length + " entries for requirement list.");
         xmlOutput( xmlData.toString(),dir_s, xslFile, response );
      }
      catch (Exception e) {
         Log.error(ME, "Can't create requirement list: " + e.toString());
         return;
      }
   }

   private class MyFilenameFilter implements FilenameFilter
   {
      public MyFilenameFilter() {}
      public boolean accept(File dir, String name)
      {
         if (name.endsWith(".xml"))
            return true;
         return false;
      }

   }

}

