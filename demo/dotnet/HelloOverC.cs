/*----------------------------------------------------------------------------
Name:      HelloManaged.cs
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Author:    "Marcel Ruff" <xmlBlaster@marcelruff.info>
Note:      Example xmlBlaster access with a managed .net C# client using xmlBlaster client C DLL library
Compile    csc.exe /reference:%XMLBLASTER_HOME%\lib\xmlBlasterClientCmanaged.dll HelloManaged.cs
Start:     Copy xmlBlasterClientCmanaged.dll to the current directory and launch
           HelloManaged.exe --help
-----------------------------------------------------------------------------*/
using System;
using System.Collections;
using System.Collections.Generic;
using System.Text;
using org.xmlBlaster.client; // xmlBlasterClientCmanaged.dll

class HelloManaged
{
   const string callbackSessionId = "secretCb";

   static void Main(string[] argv)
   {
      new HelloManaged(argv);
   }

   public HelloManaged(string[] args)
   {
      string connectQos = String.Format(
            "<qos>\n" +
            " <securityService type='htpasswd' version='1.0'>\n" +
            "  <![CDATA[\n" +
            "   <user>fritz</user>\n" +
            "   <passwd>secret</passwd>\n" +
            "  ]]>\n" +
            " </securityService>\n" +
            " <queue relating='callback' maxEntries='50000' maxEntriesCache='10000'>\n" +
            "   <callback type='SOCKET' sessionId='{0}'>\n" +
            "   </callback>\n" +
            " </queue>\n" +
            "</qos>", callbackSessionId);
      try
      {
         Console.WriteLine("Hello World");
         Hashtable props = new Hashtable();
         //props->Add(L"dispatch/connection/plugin/socket/hostname", L"localhost");
         bool help = false;
         IEnumerator myEnum = args.GetEnumerator();
         while (myEnum.MoveNext())
         {
            string key = (string)myEnum.Current;
            if (key.Equals("--help") || key.Equals("-help"))
               help = true;
            if (myEnum.MoveNext())
            {
               string value = (string)myEnum.Current;
               if (key.StartsWith("-"))
                  key = key.Substring(1);
               props.Add(key, value);
            }
         }

         XmlBlasterAccessM xb = new XmlBlasterAccessM(props);
         if (help)
         {
            Console.WriteLine("Usage:\nXmlBlaster C SOCKET client {0}\n{1}\n",
                     xb.getVersion(), xb.getUsage());
            return;
         }

         xb.connect(connectQos);
         Console.WriteLine("Connected to xmlBlaster");

         xb.disconnect("");
         Console.WriteLine("Disconnected from xmlBlaster");
      }
      catch (XmlBlasterExceptionM e)
      {
         Console.WriteLine("Caught an exception:" + e.getMessage());
      }
   }
}
