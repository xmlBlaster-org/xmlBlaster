<!doctype html public "-//w3c//dtd html 4.0 transitional//en">
<html>
<head>
   <meta http-equiv="Content-Type" content="text/html; charset=iso-8859-1">
   <meta name="GENERATOR" content="Mozilla/4.72 [en] (WinNT; U) [Netscape]">
   <title>xmlBlaster access</title>
</head>

<body bgcolor="#FFFFFF">

   <%@ page import = "org.xmlBlaster.util.Log" %>
   <%@ page import = "org.jutils.init.Args" %>
   <%@ page import = "org.jutils.text.StringHelper" %>
   <%@ page import = "org.xmlBlaster.client.protocol.XmlBlasterConnection" %>
   <%@ page import = "org.xmlBlaster.client.I_Callback" %>
   <%@ page import = "org.xmlBlaster.util.ConnectQos" %>
   <%@ page import = "org.xmlBlaster.client.UpdateKey" %>
   <%@ page import = "org.xmlBlaster.client.UpdateQos" %>
   <%@ page import = "org.xmlBlaster.client.GetKeyWrapper" %>
   <%@ page import = "org.xmlBlaster.client.GetQosWrapper" %>
   <%@ page import = "org.xmlBlaster.util.XmlBlasterException" %>
   <%@ page import = "org.xmlBlaster.util.XmlBlasterProperty" %>
   <%@ page import = "org.xmlBlaster.engine.helper.MessageUnit" %>

   <%
      XmlBlasterConnection blasterConnection = null;
      String ME = "ClientSub";
      String args[] = new String[0];
      {
         // Initialize command line argument handling (this is optional)
         try {
            XmlBlasterProperty.init(args);
         } catch(org.jutils.JUtilsException e) {
            Log.error(ME, e.toString());
         }

         try {
            // check if parameter -name <userName> is given at startup of client
            String loginName = Args.getArg(args, "-name", ME);
            String passwd = Args.getArg(args, "-passwd", "secret");
            ConnectQos loginQos = new ConnectQos(); // creates "<qos></qos>" string

            blasterConnection = new XmlBlasterConnection(args);
            blasterConnection.login(loginName, passwd, loginQos);
            Log.info(ME, "Now we are connected to xmlBlaster MOM server");

            Log.info(ME, "Getting a message - checking free memory in server ...");
            // GetKeyWrapper helps us to create this string "<key oid='__sys__FreeMem' queryType='EXACT'></key>";
            GetKeyWrapper key = new GetKeyWrapper("__sys__FreeMem", "EXACT");
            GetQosWrapper qos = new GetQosWrapper(); // helps us to create "<qos></qos>":

            try {
               MessageUnit[] msgArr = null;
               msgArr = blasterConnection.get(key.toXml(), qos.toXml());
               Log.info(ME, "Get done.");
               // We expect for this get() only one returned massage (msgArr.length == 1):
               for (int ii=0; ii<msgArr.length; ii++) {
                  out.println("<h1>Current free memory in xmlBlaster</h1>");
                  out.println("<pre>");
                  String msg = StringHelper.replaceAll(msgArr[ii].toXml(), "<", "&lt;");
                  out.println(msg);
                  out.println("</pre>");
                  //out.println("<hr />");
               }
               out.println();
            } catch(XmlBlasterException e) {
               Log.warn(ME, "XmlBlasterException: " + e.reason);
               out.println(e.reason);
            }
            
            blasterConnection.logout();
         }
         catch (Exception e) {
            Log.error(ME, e.toString());
            out.println(e.toString());
            e.printStackTrace();
         }
      }
   %>
</body>
</html>
