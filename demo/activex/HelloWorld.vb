' Simple VisualBasic HelloWorld example how to access xmlBlaster
' We connect to xmlBlaster and query the free memory of the server
' @author Marcel Ruff
Module HelloWorld
    Sub Main()
        Dim request, response As String
        Dim xmlBlaster As XmlScriptAccess.XmlScriptAccessClass

        xmlBlaster = New XmlScriptAccess.XmlScriptAccessClass

        ' Configure using the SOCKET protocol
        Dim argArr(1) As String
        argArr(0) = "-protocol"
        argArr(1) = "SOCKET"
        xmlBlaster.initArgs(argArr)

        ' Connect to the server
        response = xmlBlaster.sendRequest("<xmlBlaster><connect/></xmlBlaster>")

        ' Query the free memory
        request = "<xmlBlaster><get><key oid='__cmd:?freeMem'/></get></xmlBlaster>"
        response = xmlBlaster.sendRequest(request)
        Console.WriteLine("Got response:" & response)

        ' Leave the server
        response = xmlBlaster.sendRequest("<xmlBlaster><disconnect/></xmlBlaster>")
    End Sub
End Module

