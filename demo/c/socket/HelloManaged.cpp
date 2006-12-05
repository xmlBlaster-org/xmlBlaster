/*----------------------------------------------------------------------------
Name:      HelloManaged.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Author:    "Marcel Ruff" <xmlBlaster@marcelruff.info>
Note:      Example xmlBlaster acces with a managed .net C++ client
-----------------------------------------------------------------------------*/
#include "stdafx.h"
#using <xmlBlasterClientC.dll>

using namespace System;
using namespace System::Collections;
using namespace org::xmlBlaster::client;

/**
 * @example  HelloManagedCpp.exe -dispatch/connection/plugin/socket/hostname myserverIP
 */
int main(array<System::String ^> ^args)
{
   String^ callbackSessionId = L"secret";
   String^ connectQos = String::Format(
         L"<qos>\n"+
         " <securityService type='htpasswd' version='1.0'>\n"+
         "  <![CDATA[\n"+
         "   <user>fritz</user>\n"+
         "   <passwd>secret</passwd>\n"+
         "  ]]>\n"+
         " </securityService>\n"+
         " <queue relating='callback' maxEntries='50000' maxEntriesCache='10000'>\n"+
         "   <callback type='SOCKET' sessionId='{0}'>\n"+
         "   </callback>\n"+
         " </queue>\n"+
         "</qos>", callbackSessionId);
   try {
      Console::WriteLine(L"Hello World");
      Hashtable^ props = gcnew Hashtable();
      //props->Add(L"dispatch/connection/plugin/socket/hostname", L"localhost");
      bool help = false;
      IEnumerator^ myEnum = args->GetEnumerator();
      while ( myEnum->MoveNext() ) {
         String^ key = safe_cast<String^>(myEnum->Current);
         if (key->Equals(L"--help")||key->Equals(L"-help"))
            help = true;
         if (myEnum->MoveNext()) {
            String^ value = safe_cast<String^>(myEnum->Current);
            if (key->StartsWith("-"))
               key = key->Substring(1);
            props->Add(key, value);
         }
      }
      XmlBlasterAccessM xb(props);
      if (help) {
         Console::WriteLine("Usage:\nXmlBlaster C SOCKET client {0}\n{1}\n",
                  xb.getVersion(), xb.getUsage());
         return 1;
      }

      xb.connect(connectQos);
      Console::WriteLine("Connected to xmlBlaster");

      xb.disconnect(L"");
      Console::WriteLine("Disconnected from xmlBlaster");
      return 0;
   }
   catch (XmlBlasterExceptionM^ e) {
      Console::WriteLine("Caught an exception:" + e->getMessage());
   }
}
