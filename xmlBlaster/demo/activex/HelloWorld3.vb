' XmlBlaster access with asynchronous callbacks
' @author Marcel Ruff
Imports System

Module HelloWorld3
    Private WithEvents xmlBlaster As XmlScriptAccess.XmlScriptAccessClass

    Sub Main()
        Call HelloWorld3()
    End Sub

    ' This method is called from java delivering a message
    Private Sub XmlScriptAccess_update(ByVal msg As Object) Handles xmlBlaster.XmlScriptAccessSource_Event_update
        Console.WriteLine("SUCCESS: Update arrived: " & msg.getCbSessionId() & msg.getKey().toXml() & msg.getContentStr() & msg.getQos().toXml())
        MsgBox("SUCCESS: Update arrived: " & msg.getKey().toXml())
    End Sub

    Sub HelloWorld3()
        Dim request, response As String
        Dim prop As Object, msg As Object
        Dim getMsgArr As Object()

        xmlBlaster = New XmlScriptAccess.XmlScriptAccessClass

        prop = xmlBlaster.createPropertiesInstance()
        prop.setProperty("protocol", "SOCKET")
        xmlBlaster.initialize(prop)

        Try
            ' Connect to the server
            response = xmlBlaster.sendRequest("<xmlBlaster><connect/></xmlBlaster>")

            ' Query the free memory
            request = "<xmlBlaster>" & _
                      "  <subscribe><key oid='test.VB'/></subscribe>" & _
                      "  <publish><key oid='test.VB'/><content>Visual Basic script test</content><qos/></publish>" & _
                      "</xmlBlaster>"
            response = xmlBlaster.sendRequest(request)
            Console.WriteLine("Got response:" & response)

            xmlBlaster.publish("<key oid='test.VB'/>", "Bla", "<qos/>")

            getMsgArr = xmlBlaster.get("<key oid='test.VB'/>", "<qos/>")
            msg = getMsgArr(0)
            Console.WriteLine("Get returned:" & msg.toXml())

            xmlBlaster.erase("<key oid='test.VB'/>", "<qos/>")

            ' Leave the server
            response = xmlBlaster.sendRequest("<xmlBlaster><disconnect/></xmlBlaster>")

            ' Pass control to eventLoop ...
            MsgBox("Waiting for update ...")
            'System.Windows.Forms.Application.DoEvents()


        Catch e As SystemException
            Console.WriteLine("Exception:" & e.ToString())
        End Try
    End Sub
End Module
