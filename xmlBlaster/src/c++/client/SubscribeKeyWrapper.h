/*-----------------------------------------------------------------------------
Name:      SubscribeKeyWrapper.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Handling one xmlKey
-----------------------------------------------------------------------------*/

//  package org.xmlBlaster.client;
//  import org.xmlBlaster.util.Log;
//  import org.xmlBlaster.protocol.corba.serverIdl.XmlBlasterException;

#include <string>
#include <client/KeyWrapper.h>
#define CLIENT_HEADER xmlBlaster
#include <util/CompatibleCorba.h>

using namespace std;

namespace org { namespace xmlBlaster {
/**
 * This class encapsulates the Message meta data and unique identifier (key) 
 * of a subscribe() or get() message.<p />
 * A typical <b>subscribe</b> key could look like this:<br />
 * <pre>
 *     &lt;key oid='4711' queryType='EXACT'>
 *     &lt;/key>
 * </pre>
 * or like this:
 * <pre>
 *     &lt;key oid='' queryType='XPATH'>
 *        //AGENT
 *     &lt;/key>
 * </pre>
 *
 * @see org.xmlBlaster.util.KeyWrapper <p />
 * see xmlBlaster/src/dtd/XmlKey.xml <p />
 * see http://www.w3.org/TR/xpath
 */
   class SubscribeKeyWrapper : public KeyWrapper {

   private:

      string me() {
         return "SubscribeKeyWrapper";
      }

      string queryString_;
      /** value from attribute <key oid="" queryType="..."> */
      string queryType_; //  = "EXACT";

   public:

      /**
       * Constructor with given oid.
       * @param oid Subscribe to a well known oid.
       */
      SubscribeKeyWrapper(const string &oid) : KeyWrapper(oid) {
         queryString_ = "";
         queryType_   = "EXACT";
      }


      /**
       * Constructor with given oid.
       * @param queryString  The String with e.g. XPath syntax
       * @param queryType    The query syntax, only "XPATH" for the moment
       */
      SubscribeKeyWrapper(const string &queryString, 
                          const string &queryType) : KeyWrapper("") {
         queryType_ = queryType;
         if (queryType_ == "EXACT") oid_ = queryString;
         else {
            if (queryType_ == "XPATH") queryString_ = queryString;
            else {
               string msg = "Your queryType=";
               msg += queryType_+" is invalid,use one of \"EXACT\",\"XPATH\"";
               throw serverIdl::XmlBlasterException(me().c_str(),msg.c_str());
            }
         }
      }

      
      /**
       * Converts the data in XML ASCII string.
       * @return An XML ASCII string
       */
      string toString() {
         return toXml();
      }

      
      /**
       * Converts the data in XML ASCII string.
       * @return An XML ASCII string
       */
      string toXml() {
         return wrap(queryString_);
      }


      /**
       * May be used to integrate your application tags.
       * <p />
       * Derive your special PublishKey class from this.
       * @param str Your tags in ASCII XML syntax
       */
      string wrap(const string &str) {
         queryString_ = str;
         string ret = "<key oid='";
         ret += oid_ + "'" + " queryType='" + queryType_ + "'>\n";
         ret += queryString_ + "\n</key>";
         return ret;
      }
   };
}} // namespace
