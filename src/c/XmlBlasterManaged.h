/*----------------------------------------------------------------------------
Name:      XmlBlasterManaged.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Author:    "Marcel Ruff" <xmlBlaster@marcelruff.info>
Note:      A managed wrapper around the xmlBlaster client C library
           Used for .net applications like C# or VB.net
See:       This strange C++-CLI programming language is described in
           http://download.microsoft.com/download/9/9/c/99c65bcd-ac66-482e-8dc1-0e14cd1670cd/C++-CLI%20Standard.pdf
-----------------------------------------------------------------------------*/
#ifndef XMLBLASTER_XmlBlasterManaged_H
#define XMLBLASTER_XmlBlasterManaged_H
#ifdef _MANAGED // If /clr flag is set

struct XmlBlasterAccessUnparsed;

namespace org { namespace xmlBlaster { namespace client {

   public ref class XmlBlasterExceptionM : System::Exception
{
   private:
      const System::String ^errorCodeStr_;
      const System::String ^embeddedMessage_;

   public:
      XmlBlasterExceptionM(const System::String^ errorCode,
         const System::String^ embeddedMessage) {
            this->errorCodeStr_ = errorCode;
            this->embeddedMessage_ = embeddedMessage;
   }

      virtual ~XmlBlasterExceptionM(){};

   const System::String ^getErrorCodeStr() { return this->errorCodeStr_; }
   const System::String ^getMessage() { return this->errorCodeStr_ + this->embeddedMessage_; }
};


public ref class XmlBlasterAccessM
{
public:
   XmlBlasterAccessM(System::Collections::Hashtable ^properties);
   ~XmlBlasterAccessM();
   System::String^ connect(System::String ^connectQos);
   /*
   org::xmlBlaster::client::qos::SubscribeReturnQos subscribe(const org::xmlBlaster::client::key::SubscribeKey& key, const org::xmlBlaster::client::qos::SubscribeQos& qos, I_Callback *callback=0);
   std::vector<org::xmlBlaster::util::MessageUnit> get(const org::xmlBlaster::client::key::GetKey& key, const org::xmlBlaster::client::qos::GetQos& qos);
   std::vector<org::xmlBlaster::client::qos::UnSubscribeReturnQos> unSubscribe(const org::xmlBlaster::client::key::UnSubscribeKey& key, const org::xmlBlaster::client::qos::UnSubscribeQos& qos);
   org::xmlBlaster::client::qos::PublishReturnQos publish(const org::xmlBlaster::util::MessageUnit& msgUnit);
   void publishOneway(const std::vector<org::xmlBlaster::util::MessageUnit>& msgUnitArr);
   std::vector<org::xmlBlaster::client::qos::PublishReturnQos> publishArr(const std::vector<org::xmlBlaster::util::MessageUnit> &msgUnitArr);
   std::vector<org::xmlBlaster::client::qos::EraseReturnQos> erase(const org::xmlBlaster::client::key::EraseKey& key, const org::xmlBlaster::client::qos::EraseQos& qos);
   */
   System::Boolean disconnect(System::String ^qos);
   System::String^ getVersion();
   System::String^ getUsage();
private:
   void check();
   void shutdown();
   XmlBlasterAccessUnparsed *connection_;
};
}}}


#endif // _MANAGED
#endif /* XMLBLASTER_XmlBlasterManaged_H */
