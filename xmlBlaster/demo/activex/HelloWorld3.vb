' XmlBlaster access with asynchronous callbacks
' TODO: CALLBACKS ARE NOT ARRIVING
' @author Marcel Ruff
Module HelloWorld3
    Private WithEvents xmlBlaster As XmlScriptAccess.XmlScriptAccessClass
    Sub Main()
        Call HelloWorld3()
    End Sub

    ' This method is never called (event from java): What is wrong?
    Private Sub update(ByVal evt As Object)
        Console.WriteLine("UPDATE ARRIVED:" & evt.ToString())
        MsgBox(evt.ToString)
    End Sub

    ' This method is never called (event from java): What is wrong?
    Private Sub HelloWorld3_update(ByVal evt As Object)
        Console.WriteLine("UPDATE ARRIVED:" & evt.ToString())
        MsgBox(evt.ToString)
    End Sub

    Sub HelloWorld3()
        Dim request, response As String

        'late binding
        'Set objJava = CreateObject("XmlScriptAccess.Bean.1")

        xmlBlaster = New XmlScriptAccess.XmlScriptAccessClass

        'xmlBlaster.addUpdateListener(xmlBlaster)
        '.XmlScriptAccessSource.update()

        ' Configure using the SOCKET protocol
        Dim argArr(1) As String
        argArr(0) = "-protocol"
        argArr(1) = "SOCKET"
        xmlBlaster.initArgs(argArr)
        'MsgBox("Connecting to xmlBlaster ...")
        Try
            ' Connect to the server
            response = xmlBlaster.sendRequest("<xmlBlaster><connect/></xmlBlaster>")

            ' Query the free memory
            request = "<xmlBlaster><subscribe><key oid='test.VB'/></subscribe><publish><key oid='test.VB'/><content>This is a simple Visual Basic script test</content><qos/></publish></xmlBlaster>"
            response = xmlBlaster.sendRequest(request)
            Console.WriteLine("Got response:" & response)

            ' Leave the server
            response = xmlBlaster.sendRequest("<xmlBlaster><disconnect/></xmlBlaster>")
        Catch e As Exception
            Console.WriteLine("Exception:" & e.ToString())
        End Try
    End Sub
End Module
