/*------------------------------------------------------------------------------
Name:      ReqBaseServlet.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Handling the Client data
Version:   $Id$
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.servlet;

import java.io.*;
import java.util.*;
import java.net.*;

import javax.servlet.*;
import javax.servlet.http.*;

import org.xmlBlaster.util.XmlBlasterException;

import org.xml.sax.*;
import com.jclark.xsl.sax.ServletDestination;
import com.jclark.xsl.sax.OutputMethodHandlerImpl;
import com.jclark.xsl.sax.XSLProcessorImpl;

import java.util.logging.Logger;
import java.util.logging.Level;


/*
 * This is the base class for all servlets displaying the requirements.
 * This class processes the configuration which should be set in the
 * servlet engine environment. <br>
 *
 */
abstract public class ReqBaseServlet extends HttpServlet
{
   private static final String ME               = "ReqBaseServlet";
   private static final String DEFAULT_PARSER   = "com.jclark.xml.sax.CommentDriver";
   private static Logger log = Logger.getLogger(ReqBaseServlet.class.getName());


  /**
   */
   public void init(ServletConfig conf) throws ServletException {
      super.init(conf);

   }


  /**
   */
   public void doGet(HttpServletRequest request, HttpServletResponse response)
                       throws ServletException {
      doRequest(request, response);
   }


  /**
   */
   public void doPost(HttpServletRequest request, HttpServletResponse response)
                       throws ServletException {
      doRequest(request, response);
   }

  /**
   */
   abstract public void doRequest(HttpServletRequest request, HttpServletResponse response)
                       throws ServletException;



   /*
   */
   public void xmlOutput( String xmlData, String dir, String template, HttpServletResponse response ) throws ServletException
   {
      try {
         response.setContentType("text/html");

         XSLProcessorImpl xsl = getStylesheet( dir, template );
         OutputMethodHandlerImpl outputMethodHandler =
                                      new OutputMethodHandlerImpl(xsl);

         xsl.setOutputMethodHandler(outputMethodHandler);

         outputMethodHandler.setDestination( new ServletDestination(response) );

         xsl.parse( new InputSource( new StringReader( xmlData ) ) );

      }
      catch(Exception e) {
         log.warning("servlet output broken:"+e.toString());
         throw new ServletException(e.toString());
      }

   }

   /*
   */
   public void fileXmlOutput( String fileName, String dir, String template, HttpServletResponse response ) throws ServletException
   {
      try {
         response.setContentType("text/html");

         XSLProcessorImpl xsl = getStylesheet( dir, template );
         OutputMethodHandlerImpl outputMethodHandler =
                                      new OutputMethodHandlerImpl(xsl);

         xsl.setOutputMethodHandler(outputMethodHandler);

         outputMethodHandler.setDestination( new ServletDestination(response) );

         xsl.parse( new InputSource( (new URL( "file:"+fileName )).toString() ) );

      }
      catch(Exception e) {
         log.warning("servlet output broken:"+e.toString());
         throw new ServletException(e.toString());
      }

   }


   /*
    * returns a stylsheet object by a given name.
    * @param xslName Name of xsl file without full path and extension
    * @return XSLProcessorImpl
    */
   public XSLProcessorImpl getStylesheet( String reqDir, String xslFile ) throws XmlBlasterException, IOException
   {

      String xslPath = reqDir+"/"+xslFile+".xsl";

      XSLProcessorImpl xsl             = xsl  = new XSLProcessorImpl();
      xsl.setParser(createParser());

      try {
         String url = new URL("file", "", xslPath).toString();
         log.info("Reading from "+url);
         xsl.loadStylesheet( new InputSource( new URL("file", "", xslPath).toString() ) );
         log.info("Successfully read from "+url);
      }
      catch ( Exception e) {
         log.severe(e.toString());
         throw new XmlBlasterException(ME,"Could not read XSL file.");
      }

      return xsl;
   }


   /*
    * creates a parser object
    * @return Parser
    */
   static Parser createParser() throws XmlBlasterException
   {
       try {
         return (Parser)Class.forName(DEFAULT_PARSER).newInstance();
       }

       catch ( Exception e) {
         throw new XmlBlasterException(ME,e.toString());
       }
   }

}


