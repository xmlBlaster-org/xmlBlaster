' The xmlBlaster callback events don't arrive in VisualBasic <= 6
' The reason is not yet known (Marcel 2004-03-18)
'
VERSION 5.00
Begin VB.Form Form1 
   AutoRedraw      =   -1  'True
   Caption         =   "Form1"
   ClientHeight    =   4710
   ClientLeft      =   60
   ClientTop       =   450
   ClientWidth     =   7725
   LinkTopic       =   "Form1"
   ScaleHeight     =   4710
   ScaleWidth      =   7725
   StartUpPosition =   3  'Windows Default
   Begin VB.TextBox Logger 
      Height          =   3975
      Left            =   120
      MultiLine       =   -1  'True
      ScrollBars      =   3  'Both
      TabIndex        =   1
      Text            =   "XmlBlasterVB6.frx":0000
      Top             =   720
      Width           =   7455
   End
   Begin VB.CommandButton Start 
      Caption         =   "Start"
      Height          =   615
      Left            =   120
      TabIndex        =   0
      Top             =   120
      Width           =   1695
   End
End
Attribute VB_Name = "Form1"
Attribute VB_GlobalNameSpace = False
Attribute VB_Creatable = False
Attribute VB_PredeclaredId = True
Attribute VB_Exposed = False
'---------------------------------------------------------------------------
' This method is called asynchronously from java delivering a message.
' As events from java into ActiveX can't deliver a return value
' or an exception back we need to call either
'    setUpdateReturn()        -> passes a return value to the server
' or
'    setUpdateException()     -> throws an XmlBlasterException
' If you forget this the update thread of the java bean will block forever
'---------------------------------------------------------------------------
Public Sub XmlScriptAccess_update(ByVal msg As Object)
   On Error GoTo UpdateErrorHandler
      Dim age As String
      age = msg.getQos().getClientProperty("myAge").getStringValue()
      log ("SUCCESS: Update arrived: " & msg.getCbSessionId() & _
              ", oid=" & msg.getKey().getOid() & _
              ", content=" & msg.getContentStr() & _
              ", myAge=" & age)
      MsgBox ("Success, message arrived:" & Str)
      xmlBlaster.setUpdateReturn ("<qos><state id='OK'/></qos>")
   Exit Sub
UpdateErrorHandler:
      log (Err.Number & ": " & Err.Description)
      xmlBlaster.setUpdateException "user.update.internalError", Err.Description
   Exit Sub
End Sub

'---------------------------------------------------------------------------
' Connect to xmlBlaster and try all possible methods
'---------------------------------------------------------------------------
Private Sub xmlBlasterDemo()
   On Error GoTo ErrorHandler
      Set xmlBlaster = CreateObject("XmlScriptAccess.Bean")
      
      Set prop = xmlBlaster.createPropertiesInstance()
      Rem CallByName(prop, "setProperty", vbLet, "protocol","SOCKET")
      Rem prop.setProperty("protocol", "SOCKET")
      Rem prop.setProperty("trace", "false")
      xmlBlaster.Initialize (prop)
      
      Dim argArr(3) As String
      argArr(0) = "-protocol"
      argArr(1) = "SOCKET"
      argArr(2) = "-trace"
      argArr(3) = "false"
      xmlBlaster.initArgs (argArr)
      
      ' Connect to the server
      qos = "<qos>" & _
            "  <securityService type='htpasswd' version='1.0'>" & _
            "   <![CDATA[" & _
            "   <user>HelloWorld3</user>" & _
            "   <passwd>secret</passwd>" & _
            "   ]]>" & _
            "  </securityService>" & _
            "</qos>"
      Dim connectReturnQos As Object
      
      'On Error Resume Next
      'Err.Raise 99, "Marcel", "Marcels Error"
      
      Set connectReturnQos = xmlBlaster.Connect(qos)
            
      sessionId = connectReturnQos.getSecretSessionId()
      log ("Connected to xmlBlaster, sessionId=" & sessionId)
      
      ' Publish a message
      Key = "<key oid='HelloWorld3' contentMime='text/xml'>" & _
            "  <org.xmlBlaster><demo/></org.xmlBlaster>" & _
            "</key>"
      contentStr = "Hi"
      qos = "<qos>" & _
            "<clientProperty name='myAge' type='int'>18</clientProperty>" & _
            "</qos>"
      Set publishReturnQos = xmlBlaster.publishStr(Key, contentStr, qos)
      log ("Published message id=" & publishReturnQos.getRcvTimestamp().toXml("", True))
   
      ' Get synchronous the above message
      getMsgArr = xmlBlaster.get("<key oid='HelloWorld3'/>", "<qos/>")
      For Each msg In getMsgArr
         log ("Get returned:" & msg.toXml())
      Next
   
      ' Subscribe
      Set subscribeReturnQos = xmlBlaster.subscribe("<key oid='HelloWorld3'/>", "<qos/>")
      log ("Got subscribe response:" & subscribeReturnQos.getSubscriptionId())
   
      Call loopEvents
      
      ' Publish again, message arrives asynchronously in
      ' Sub XmlScriptAccess_update() (see above)
      Set publishReturnQos = xmlBlaster.publishStr(Key, "Ho", qos)
      log ("Got publish response:" & publishReturnQos.toXml())
      
      Call loopEvents
            
      ' UnSubscribe
      k = "<key oid='" & subscribeReturnQos.getSubscriptionId() & "'/>"
      unSubscribeReturnQos = xmlBlaster.unSubscribe(k, "<qos/>")
   
      ' Destroy the topic "HelloWorld3"
      eraseReturnQos = xmlBlaster.erase("<key oid='HelloWorld3'/>", "<qos/>")
   
      Call loopEvents
      
      xmlBlaster.disconnect ("<qos/>")
      Set xmlBlaster = Nothing
      Rem MsgBox ("Hit a key to continue ...")
   Exit Sub
   
ErrorHandler:
   log (Err.Number & ": " & Err.Description)
   MsgBox ("Error, giving up: " & Err.Description)
   Exit Sub
End Sub

Private Sub loopEvents()
   ' Give control to the main loop to receive the update event
   'System.Windows.Forms.Application.DoEvents()
   MsgBox ("Hit a key to continue ...")
End Sub

Private Sub log(text)
   Logger.text = Logger.text & vbCrLf & text
End Sub

Private Sub Start_Click()
    Call xmlBlasterDemo
End Sub

