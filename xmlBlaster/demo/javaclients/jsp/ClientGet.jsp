<!doctype html public "-//w3c//dtd html 4.0 transitional//en">
<html>
<head>
   <meta http-equiv="Content-Type" content="text/html; charset=iso-8859-1">
   <meta name="GENERATOR" content="Mozilla/4.72 [en] (WinNT; U) [Netscape]">
   <title>xmlBlaster access</title>
</head>

<body bgcolor="#FFFFFF">

   <%@ page import = "org.jutils.log.LogChannel" %>
   <%@ page import = "org.jutils.init.Args" %>
   <%@ page import = "org.jutils.text.StringHelper" %>
   <%@ page import = "org.xmlBlaster.client.I_XmlBlasterAccess" %>
   <%@ page import = "org.xmlBlaster.client.I_Callback" %>
   <%@ page import = "org.xmlBlaster.client.qos.ConnectQos" %>
   <%@ page import = "org.xmlBlaster.client.key.UpdateKey" %>
   <%@ page import = "org.xmlBlaster.client.qos.UpdateQos" %>
   <%@ page import = "org.xmlBlaster.client.key.GetKey" %>
   <%@ page import = "org.xmlBlaster.client.qos.GetQos" %>
   <%@ page import = "org.xmlBlaster.util.XmlBlasterException" %>
   <%@ page import = "org.xmlBlaster.util.XmlBlasterProperty" %>
   <%@ page import = "org.xmlBlaster.util.MsgUnit" %>

   <%
      I_XmlBlasterAccess blasterConnection = null;
      String ME = "ClientSub";
      Global glob = new Global();
      LogChannel log = glob.getLog(null);
      {
         try {
            // check if parameter -loginName <userName> is given at startup of client
            String loginName = glob.getProperty().get("loginName", ME);
            String passwd = glob.getProperty().get("passwd", "secret");
            ConnectQos loginQos = new ConnectQos(glob, loginName, passwd); // creates "<qos>...</qos>" string

            blasterConnection = glob.getXmlBlasterAccess();
            blasterConnection.connect(loginQos);
            log.info(ME, "Now we are connected to xmlBlaster MOM server");

            log.info(ME, "Getting a message - checking free memory in server ...");
            // GetKey helps us to create this string "<key oid='__cmd:?freeMem' queryType='EXACT'></key>";
            GetKey key = new GetKey(glob, "__cmd:?freeMem", "EXACT");
            GetQos qos = new GetQos(glob); // helps us to create "<qos></qos>":

            try {
               MsgUnit[] msgArr = null;
               msgArr = blasterConnection.get(key.toXml(), qos.toXml());
               log.info(ME, "Get done.");
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
               log.warn(ME, "XmlBlasterException: " + e.getMessage());
               out.println(e.getMessage());
            }
            
            blasterConnection.logout();
         }
         catch (Exception e) {
            log.error(ME, e.toString());
            out.println(e.toString());
            e.printStackTrace();
         }
      }
   %>
</body>
</html>
