/*------------------------------------------------------------------------------
Name:      SecurityQos.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   The qos for the security (a subelement of connect qos)
------------------------------------------------------------------------------*/

/**
 * Parse the default security handling with loginName and password
 * from the login qos xml string:
 * <pre>
 *  &lt;securityService type="simple" version="1.0">
 *     &lt;user>aUser&lt;/user>
 *     &lt;passwd>theUsersPwd&lt;/passwd>
 *  &lt;/securityService>
 * </pre>
 */

#ifndef _AUTHENTICATION_SECURITY_QOS_H
#define _AUTHENTICATION_SECURITY_QOS_H

#include <util/xmlBlasterDef.h>
#include <string>
#include <util/SaxHandlerBase.h>
#include <util/StringTrim.h>

// #include <util/XmlBlasterException>

namespace org { namespace xmlBlaster { namespace authentication {

   class Dll_Export SecurityQos: public util::SaxHandlerBase
   {
   private:
      const string ME;

      // helper flags for SAX parsing
      bool inSecurityService_;
      bool inUser_;
      bool inPasswd_;

      string type_;
      string version_;
      string user_;
      string passwd_;
      int args_;
      const char * const* argc_;

      util::StringTrim<char> trim_;

      void prep(int args,const char * const argc[])
      {
         inSecurityService_ = false;
         inUser_            = false;
         inPasswd_          = false;
         type_              = "simple";
         version_           = "1.0";
         user_              = "";
         passwd_            = "";
         args_              = args;
         argc_              = argc;

      }

      void copy(const SecurityQos& securityQos)
      {
         inSecurityService_ = securityQos.inSecurityService_;
         inUser_            = securityQos.inUser_;
         inPasswd_          = securityQos.inPasswd_;
         type_              = securityQos.type_;
         version_           = securityQos.version_;
         user_              = securityQos.user_;
         passwd_            = securityQos.passwd_;
         args_              = securityQos.args_;
         argc_              = securityQos.argc_;
      }

   public:
      SecurityQos(int args=0, const char * const argc[]=0);

      void parse(const string& xmlQoS_literal)
      {
         log_.call(ME, "parse");
         // Strip CDATA tags that we are able to parse it:
         string ret = xmlQoS_literal;

//         xmlQoS_literal = StringHelper.replaceAll(xmlQoS_literal, "<![CDATA[", "");
         string::size_type pos = 0;
         while (pos != ret.npos) {
            pos = ret.find("<![CDATA[");
            if (pos == ret.npos) break;
            ret = ret.erase(pos, 9);
         }

//         xmlQoS_literal = StringHelper.replaceAll(xmlQoS_literal, "]]>", "");
         pos = 0;
         while (pos != ret.npos) {
            pos = ret.find("]]>");
            if (pos == ret.npos) break;
            ret = ret.erase(pos, 3);
         }

         init(ret);
      }

      SecurityQos(const string& xmlQoS_literal, int args=0, const char * const argc[]=0);

      SecurityQos(const string& loginName,
                  const string& password,
                  int args=0,
                   const char * const argc[]=0);

      SecurityQos(const SecurityQos& securityQos);
      SecurityQos& operator =(const SecurityQos& securityQos);


      string getPluginVersion() const;

      string getPluginType() const;

      void setUserId(const string& userId);

      string getUserId() const;

      /**
       * @param cred The password
       */
      void setCredential(const string& cred);

      /**
       * @return "" (empty string) (no password is delivered)
       */
      string getCredential() const;

      /**
       * Start element, event from SAX parser.
       * <p />
       * @param name Tag name
       * @param attrs the attributes of the tag
       */
      void startElement(const XMLCh* const name, AttributeList& attrs);

      /**
       * End element, event from SAX parser.
       * <p />
       * @param name Tag name
       */

       void endElement(const XMLCh* const name);

      string toXml();

      /**
       * Dump state of this object into a XML ASCII string.
       * <br>
       * @param extraOffset indenting of tags for nice output
       * @return The xml representation
       */
      string toXml(const string& extraOffset);
   };

}}} // namespaces

#endif
