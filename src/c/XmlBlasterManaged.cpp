/*----------------------------------------------------------------------------
Name:      XmlBlasterManaged.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Author:    "Marcel Ruff" <xmlBlaster@marcelruff.info>
Note:      Managed C++ wrapper around unmanaged C xmlBlaster client library DLL
Todo:      Currently only works for one connection as i don't know how to pass
           'XmlBlasterAccessM^ xmlBlasterAccess' to myUpdate() function
-----------------------------------------------------------------------------*/
#ifdef _MANAGED // If /clr flag is set

#include <iostream>
#include "XmlBlasterManaged.h"
#include "XmlBlasterAccessUnparsed.h"

using namespace System;
using namespace System::Text;
using namespace System::Collections;

using namespace org::xmlBlaster::client;

/* Which multibyte string conversion to use, "toAnsi()" or "toUtf8()" */
#define TO_MBS toAnsi
#define FROM_MBS fromUtf8


static void MarshalString(System::String^ s, std::string& os, const char *prefix=0) {
   if (prefix != 0) os = prefix;
   using namespace System::Runtime::InteropServices;
   const char* chars = 
      (const char*)(Marshal::StringToHGlobalAnsi(s)).ToPointer();
   os += chars;
   Marshal::FreeHGlobal(IntPtr((void*)chars));
}

/**
 * @return is malloced, you need to free it
 */
static char *toAnsi(System::String^ s, const char *prefix=0) {
   std::string os;
   MarshalString(s, os, prefix);
   return strcpyAlloc(os.c_str());
}

static System::String^ fromUtf8(const char *encodedBytes) {
   std::string s(encodedBytes);
   String ^str= gcnew String(s.c_str());

   //System::String^ str;
   //str = encodedBytes;
   return str;
   /*
   //System::String^ str(p);
   UTF8Encoding^ utf8 = gcnew UTF8Encoding;
   const int len = (int)strlen(encodedBytes);
   array<Byte>^ arr;
   for (int i=0; (encodedBytes+i) != 0; i++)
   arr[i] = (Byte)encodedBytes[i];
   System::String^ decodedString = utf8->GetString(arr);
   return decodedString;
   */
}


/**
 * @return is malloced, you need to free it
 */
static char *toUtf8(String^ unicodeString, const char *prefix=0) {
   UTF8Encoding^ utf8 = gcnew UTF8Encoding;
   Console::WriteLine("original=" + unicodeString );
   array<Byte>^encodedBytes = utf8->GetBytes( unicodeString );
   //Console::WriteLine();
   Console::WriteLine( "Encoded bytes:" );

   int prefixLen = (prefix == 0) ? 0 : (int)strlen(prefix);
   int len = prefixLen + encodedBytes->Length + 1;

   char *mbs = (char *)malloc(len);
   *(mbs+len-1) = 0;
   if (prefixLen > 0)
      strncpy0(mbs, prefix, prefixLen+1);

   int i=prefixLen;
   IEnumerator^ myEnum = encodedBytes->GetEnumerator();
   while ( myEnum->MoveNext() )
   {
      Byte b = safe_cast<Byte>(myEnum->Current);
      mbs[i] = b;
      Console::Write( "[{0}]", b );
   }
   return mbs;
}


/**
* Here we receive the callback messages from xmlBlaster
* @param msgUnitArr The received messages
* @param userData Is the corresponding XmlBlasterAccessUnparsed * pointer
* @param exception An OUT parameter to transport back an exception
* @see UpdateFp in XmlBlasterAccessUnparsed.h
* @see UpdateFp in CallbackServerUnparsed.h
*/
static bool myUpdate(::MsgUnitArr *msgUnitArr, void *userData,
                     ::XmlBlasterException *exception)
{
   size_t i;
   bool testException = false;
   ::XmlBlasterAccessUnparsed *connection_ = (::XmlBlasterAccessUnparsed *)userData;
   //Cannot convert an unmanaged type to a managed type
   //XmlBlasterAccessM^ xmlBlasterAccess = static_cast<XmlBlasterAccessM^>(connection_->userObject);

   for (i=0; i<msgUnitArr->len; i++) {
      char *xml = ::messageUnitToXml(&msgUnitArr->msgUnitArr[i]);
      printf("[client] CALLBACK update(): Asynchronous message update arrived:%s\n",
         xml);
      ::xmlBlasterFree(xml);
      msgUnitArr->msgUnitArr[i].responseQos = 
         strcpyAlloc("<qos><state id='OK'/></qos>");
      /* Return QoS: Everything is OK */
   }
   return true;
}

   XmlBlasterAccessM::XmlBlasterAccessM(Hashtable ^props) {
      System::Console::WriteLine("Hello from xmlBlasterClientC CLR");

      int argc = (2*props->Count)+1;
      const char ** ptr = (const char **)malloc(argc*sizeof(char *));
      ptr[0] = strcpyAlloc("someClientName"); // TODO
      IDictionaryEnumerator^ myEnumerator = props->GetEnumerator();
      int i=1;
      while (myEnumerator->MoveNext()) {
         //System::Console::WriteLine("\t{0}:\t{1}", myEnumerator->Key, myEnumerator->Value);
         ptr[i] = TO_MBS((System::String ^)myEnumerator->Key, "-");
         printf("key='%s'\n", ptr[i]);
         i++;
         ptr[i] = TO_MBS((System::String ^)myEnumerator->Value);
         printf("value='%s'\n", ptr[i]);
         //std::cout << "UTF8 result is " << ptr[i] << std::endl;
         i++;
      }

      this->connection_ = getXmlBlasterAccessUnparsed(argc, (const char* const*)ptr);
      // Cannot convert a managed type to an unmanaged type
      //this->connection_->userObject = (void *)this; // Transports us to the myUpdate() method
      // this->connection_->log = myLogger;    // Register our own logging function
      // this->connection_->logUserP = this;   // Pass ourself to myLogger()
   }

   XmlBlasterAccessM::~XmlBlasterAccessM() {
      shutdown();
   }

   String ^XmlBlasterAccessM::connect(String ^connectQos) {
      ::XmlBlasterException xmlBlasterException;
      check();
      if (connection_->initialize(connection_, (UpdateFp)myUpdate, &xmlBlasterException) == false) {
         printf("[client] Connection to xmlBlaster failed,"
            " please start the server or check your configuration\n");
         shutdown();
         throw gcnew XmlBlasterExceptionM(FROM_MBS(xmlBlasterException.errorCode), FROM_MBS(xmlBlasterException.message));
      }

      char *retQos = connection_->connect(connection_, TO_MBS(connectQos),
         (UpdateFp)myUpdate, &xmlBlasterException);
      if (*xmlBlasterException.errorCode != 0) {
         printf("[client] Caught exception during connect errorCode=%s, message=%s\n",
            xmlBlasterException.errorCode, xmlBlasterException.message);
         shutdown();
         throw gcnew XmlBlasterExceptionM(FROM_MBS(xmlBlasterException.errorCode), FROM_MBS(xmlBlasterException.message));
      }
      String^ ret = FROM_MBS(retQos);
      xmlBlasterFree(retQos);
      printf("[client] Connected to xmlBlaster ...\n");
      return ret;
   }

   System::Boolean XmlBlasterAccessM::disconnect(String ^qos) {
      
      ::XmlBlasterException xmlBlasterException;
      check();
      if (this->connection_->disconnect(this->connection_, TO_MBS(qos), &xmlBlasterException) == false) {
         printf("[client] Caught exception in disconnect, errorCode=%s, message=%s\n",
               xmlBlasterException.errorCode, xmlBlasterException.message);
         shutdown();
         return false;
      }
      return true;
   }

   void XmlBlasterAccessM::check() {
      if (this->connection_ == 0) {
         throw gcnew XmlBlasterExceptionM(L"user.illegalArgument", L"The connection is shutdown, please create another XmlBlasterAccessM instance");
      }
   }

   void XmlBlasterAccessM::shutdown() {
      freeXmlBlasterAccessUnparsed(this->connection_);
      this->connection_ = 0;
   }

   String ^XmlBlasterAccessM::getVersion() {
      check();
      const char *version = ::getXmlBlasterVersion();
      return FROM_MBS(version);
   }

   String ^XmlBlasterAccessM::getUsage() {
      check();
      char usage[XMLBLASTER_MAX_USAGE_LEN];
      ::xmlBlasterAccessUnparsedUsage(usage);
      return FROM_MBS(usage);
   }

#endif // _MANAGED
