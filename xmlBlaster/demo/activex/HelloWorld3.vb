'------------------------------------------------------------------------------
' XmlBlaster access with asynchronous callbacks from Visual Basic .net
' Calls are routed over ActiveX encapsulating a Java client bean which
' connects to xmlBlaster
' @file xmlBlaster/demo/activex/VisualBasic3.vb
' @author Marcel Ruff, xmlBlaster@marcelruff.info (2004-03-17)
' @see http://www.xmlBlaster.org/xmlBlaster/doc/requirements/client.activex.html
' @see org.xmlBlaster.client.activex.XmlScriptAccess
'------------------------------------------------------------------------------
Imports System

Module HelloWorld3
   Private WithEvents xmlBlaster As XmlScriptAccess.XmlScriptAccessClass

   Sub Main()
      Call HelloWorld3()
   End Sub

   '---------------------------------------------------------------------------
   ' This method is called asynchronously from java delivering a message. 
   ' As events from java into ActiveX can't deliver a return value
   ' or an exception back we need to call either
   '    setUpdateReturn()        -> passes a return value to the server
   ' or
   '    setUpdateException()     -> throws an XmlBlasterException
   ' If you forget this the update thread of the java bean will block forever
   '---------------------------------------------------------------------------
   Private Sub XmlScriptAccess_update(ByVal msg As Object) _
               Handles xmlBlaster.XmlScriptAccessSource_Event_update
      Try
         Dim age As String
         age = msg.getQos().getClientProperty("myAge").getStringValue()
         Console.WriteLine("SUCCESS: Update arrived: " & msg.getCbSessionId() & _
                 ", oid=" & msg.getKey().getOid() & _
                 ", content=" & msg.getContentStr() & _
                 ", myAge=" & age)
         ' MsgBox("Success, message arrived:" & msg.getKey().toXml())
         xmlBlaster.setUpdateReturn("<qos><state id='OK'/></qos>")
      Catch e As SystemException
         Console.WriteLine("Exception in update:" & e.ToString())
         xmlBlaster.setUpdateException("user.update.internalError", e.ToString())
      End Try
   End Sub

   '---------------------------------------------------------------------------
   ' Connect to xmlBlaster and try all possible methods
   '---------------------------------------------------------------------------
   Sub HelloWorld3()
      Dim key, qos As String
      'Dim content As Byte()
      Dim contentStr As String
      Dim prop As Object, msg As Object, response As Object

      xmlBlaster = New XmlScriptAccess.XmlScriptAccessClass

      prop = xmlBlaster.createPropertiesInstance()
      prop.setProperty("protocol", "SOCKET")
      prop.setProperty("trace", "false")
      xmlBlaster.initialize(prop)

      Try
         ' Connect to the server
         qos = "<qos>" & _
               "  <securityService type='htpasswd' version='1.0'>" & _
               "   <![CDATA[" & _
               "   <user>HelloWorld3</user>" & _
               "   <passwd>secret</passwd>" & _
               "   ]]>" & _
               "  </securityService>" & _
               "</qos>"
         response = xmlBlaster.connect(qos)
         Console.WriteLine("Connected to xmlBlaster, sessionId=" & _
                           response.getSecretSessionId())

         ' Publish a message
         key = "<key oid='HelloWorld3' contentMime='text/xml'>" & _
               "  <org.xmlBlaster><demo/></org.xmlBlaster>" & _
               "</key>"
         contentStr = "Hi"
         qos = "<qos>" & _
               "<clientProperty name='myAge' type='int'>18</clientProperty>" & _
               "</qos>"
         Dim publishReturnQos As Object
         publishReturnQos = xmlBlaster.publish(key, contentStr, qos)
         Console.WriteLine("Published message id=" & _
                       publishReturnQos.getRcvTimestamp().toXml("", True))

         ' Get synchronous the above message
         Dim getMsgArr As Object()
         getMsgArr = xmlBlaster.get("<key oid='HelloWorld3'/>", "<qos/>")
         For Each msg In getMsgArr
            Console.WriteLine("Get returned:" & msg.toXml())
         Next

         ' Subscribe
         Dim subscribeReturnQos As Object
         subscribeReturnQos = xmlBlaster.subscribe("<key oid='HelloWorld3'/>", "<qos/>")
         Console.WriteLine("Got subscribe response:" & _
                           subscribeReturnQos.getSubscriptionId())

         ' Give control to the main loop to receive the update event
         System.Windows.Forms.Application.DoEvents()

         ' Publish again, message arrives asynchronously in
         ' Sub XmlScriptAccess_update() (see above)
         publishReturnQos = xmlBlaster.publish(key, "Ho", qos)
         Console.WriteLine("Got publish response:" & publishReturnQos.toXml())

         ' Give control to the main loop to receive the update event
         System.Windows.Forms.Application.DoEvents()

         ' UnSubscribe
         Dim k As String = "<key oid='" & subscribeReturnQos.getSubscriptionId() & "'/>"
         xmlBlaster.unSubscribe(k, "<qos/>")

         ' Destroy the topic "HelloWorld3"
         xmlBlaster.erase("<key oid='HelloWorld3'/>", "<qos/>")

         ' Leave the server, cleanup resources
         xmlBlaster.disconnect("<qos/>")

         ' Pass control to eventLoop ...
         MsgBox("Click me to finish ...")

      Catch e As SystemException
         Console.WriteLine("Exception:" & e.ToString())
      End Try
   End Sub
End Module
