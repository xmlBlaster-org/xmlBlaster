/*------------------------------------------------------------------------------
Name:      BrowserTest.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Handling callback over http
Version:   $Id: BrowserTest.java,v 1.3 2000/03/03 15:51:10 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.protocol.http;

import java.rmi.RemoteException;
import java.io.*;
import javax.servlet.*;
import javax.servlet.http.*;
import org.xmlBlaster.util.*;


/**
 * This servlet doesn't leave the doGet() method after an invocation
 * keeping a permanent http connection
 * Use this class to test the behavior of your browser.
 * <br />
 * <pre>
 * http://localhost/servlets/BrowserTest?mode=multi
 * http://localhost/servlets/BrowserTest?mode=push
 * http://localhost/servlets/BrowserTest?mode=poll
 * </pre>
 * @author ruff@swand.lake.de
 */
public class BrowserTest extends HttpServlet
{
   private final String ME = "BrowserTest";
   private int globalVal = 1;
   private String mode = "multi";


   /**
    * This method is invoked only once when the servlet is startet.
    * @param conf init parameter of the servlet
    */
   public void init(ServletConfig conf) throws ServletException
   {
      super.init(conf);
      Log.info(ME, "Initialize ...");
   }


   /**
    * POST request from the browser.
    * <p>
    * This method is called through a SUBMIT of a HTML FORM,<br>
    * the TARGET should be set to "callbackFrame"
    * @param req Data from browser
    * @param res Response of the servlet
    */
   public void doPost(HttpServletRequest req, HttpServletResponse res)
                               throws ServletException, IOException
   {
      res.setContentType("text/html");
      PrintWriter out = res.getWriter();

      HttpSession session = req.getSession(true);
      String sessionId = req.getRequestedSessionId();
      String loginName = Util.getParameter(req, "login", null);    // "Joe";
      String password = Util.getParameter(req, "password", null);  // "secret";
      Log.info(ME, "Entering BrowserTest servlet for '" + loginName + "', sessionId=" + sessionId);

      StringBuffer retStr = new StringBuffer();
      try {
         String actionType = Util.getParameter(req, "ActionType", null);
         if (actionType!=null && actionType.equals("Login")) {
            Log.info(ME, "Login pressed ...");

            if (loginName == null || loginName.length() < 1)
               throw new Exception("Missing login name");
            if (password == null || password.length() < 1)
               throw new Exception("Missing password");
         }
         else if (actionType!=null && actionType.equals("Logout")) {
            Log.info(ME, "Logout pressed ...");
         }
         else {
           throw new Exception("Unknown action type");
         }

      } catch (Exception e) {
         Log.error(ME, "RemoteException: " + e.getMessage());
         e.printStackTrace();
         retStr.append("<body>http communication problem</body>");
      } finally {
         out.println(retStr.toString());
      }
   }


   /**
    * GET request from the browser
    * Testing three modes how to update the browser
    */
   public void doGet(HttpServletRequest req, HttpServletResponse res)
                                 throws ServletException, IOException
   {
      Log.info(ME, "Entering doGet()");
      String tmp = Util.getParameter(req, "mode", null);
      if (tmp != null) {
         mode = tmp;
         Log.info(ME, "Testing mode=" + mode);
      }

      try {

         if (mode.equals("multi")) {
            /* Every line which is sent to the browser overwrites the former one
               Problems: (Linux/netscape)
               1. The watch-wait cursor is displayed, until the doGet() leaves.
               2. Resizing the browser window doesn't resize the content.
            */
            ServletOutputStream out = res.getOutputStream();

            res.setContentType("multipart/x-mixed-replace;boundary=End");
            out.println();         // An empty line an
            out.println("--End");  // the end marker (from the boundary above)
                                   // are finishing the multipart

            int val = 1;

            while (true) {
               Log.info(ME, "Sending next multipart");

               out.println("Content-Type: text/html");
               out.println();

               out.println("<HTML>");
               out.println("<HEAD>");
               out.println("<meta http-equiv='Content-Type' content='text/html; charset=iso-8859-1'>");
               out.println("<meta http-equiv='Pragma' content='no-cache'>");
               out.println("<meta http-equiv='Expires' content='Tue, 31 Dec 1997 23:59:59 GMT'>");
               out.println("<TITLE>Hello World</TITLE>");
               out.println("</HEAD>");
               out.println("<BODY>");
               out.println("<BIG>Hello World - GET - multipart No." + val++ + "</BIG>");
               out.println("<A HREF='http://www.doubleslash.de'>DOUBLESLASH</A>");
               /*
               out.println("<script language='JavaScript'>");
               out.println("alert('Konrad Konradowitsch');");
               out.println("</script>");
               */
               out.println("</BODY></HTML>");

               out.println();
               out.println("--End");
               out.flush();

               try { Thread.currentThread().sleep(2000); } catch(Exception e) {}
               if (val > 10)
                  break;
            }
            /* deletes the last page
            out.println();
            out.println("--End");
            out.flush();
            */
            out.close();
         }
         else if (mode.equals("poll")) {
            /* Every line which is sent to the browser overwrites the former one
               Problems: (Linux/netscape)
               None, but it is a polling mode.
            */
            PrintWriter out = res.getWriter();
            res.setContentType("text/html");
            if (globalVal % 2 == 0) {
               globalVal++;
               Log.info(ME, "SC_NO_CONTENT globalVal=" + globalVal);
               res.setStatus(HttpServletResponse.SC_NO_CONTENT);
               return;
            }
            Log.info(ME, "globalVal=" + globalVal);
            out.println("<HTML>");
            out.println("<HEAD>");
            out.println("<meta http-equiv='Content-Type' content='text/html; charset=iso-8859-1'>");
            out.println("<meta http-equiv='Pragma' content='no-cache'>");
            // the polling variant every 2 sec:
            out.println("<meta http-equiv='refresh' content='2;URL=BrowserTest'>");
            out.println("<TITLE>Hello World</TITLE>");
            out.println("</HEAD>");
            out.println("<BODY>");
            out.println("<BIG>Hello World - GET - Polling No." + globalVal++ + "</BIG>");
            out.println("</BODY></HTML>");
            out.flush();
            out.close();
         }
         else if (mode.equals("push")) {
            /*
               Problems: (Linux/netscape)
               1. The watch-wait cursor is displayed, until the doGet() leaves.
               2. Resizing the browser window doesn't resize the content.
               3. Every line which is sent again to the browser is written after
                  the previous one resulting in a list of ten rows.
            */
            PrintWriter out = res.getWriter();
            res.setContentType("text/html");
            res.setHeader("Cache-Control", "no-cache");
            res.setHeader("Expires","1 Jan 2000");

            int val = 1;

            out.println("<HTML>");
            out.println("<HEAD>");
            out.println("<meta http-equiv='Content-Type' content='text/html; charset=iso-8859-1'>");
            out.println("<meta http-equiv='Pragma' content='no-cache'>");
            out.println("<meta http-equiv='Expires' content='Tue, 31 Dec 1997 23:59:59 GMT'>");
            out.println("<TITLE>Hello World</TITLE>");
            out.println("</HEAD>");
            out.println("<BODY>");
            while (true) {
               out.println("<BIG>Hello World - GET - Simple server push No." + val++ + "</BIG>");
               out.println("<P>"); // This newline forces a refresh everytime!
               out.flush();
               try { Thread.currentThread().sleep(2000); } catch(Exception e) {}
               if (val > 10)
                  break;
            }
            out.println("</BODY></HTML>");
            out.flush();
            out.close();
         }
         else if (mode.equals("pushall")) {
            /*
               Problems: (Linux/netscape)
               see push
               The behavior is the same as with 'push', sending the HTML header doesn't change
               anything.
            */
            PrintWriter out = res.getWriter();
            res.setContentType("text/html");
            res.setHeader("Cache-Control", "no-cache");
            res.setHeader("Expires","1 Jan 2000");

            int val = 1;

            while (true) {
               out.println("<HTML>");
               out.println("<HEAD>");
               out.println("<meta http-equiv='Content-Type' content='text/html; charset=iso-8859-1'>");
               out.println("<meta http-equiv='Pragma' content='no-cache'>");
               out.println("<meta http-equiv='Expires' content='Tue, 31 Dec 1997 23:59:59 GMT'>");
               out.println("<TITLE>Hello World</TITLE>");
               out.println("</HEAD>");
               out.println("<BODY>");
               out.println("<BIG>Hello World - GET - Simple server push No." + val++ + "</BIG>");
               out.println("<P>"); // This newline forces a refresh everytime!
               out.println("</BODY></HTML>");
               out.flush();
               try { Thread.currentThread().sleep(2000); } catch(Exception e) {}
               if (val > 10)
                  break;
            }
            out.close();
         }
         Log.info(ME, "doGet() done");
      }
      catch(Exception e) {  // if browser closes in multipart: java.io.IOException
         Log.error(ME, "doGet() failed, " + e.toString());
      }
   }
}
