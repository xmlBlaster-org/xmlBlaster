/*-----------------------------------------------------------------------------
Name:      PublishKeyWrapper.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Handling one xmlKey
Version:   $Id: PublishKeyWrapper.h,v 1.2 2000/07/06 23:42:27 laghi Exp $
-----------------------------------------------------------------------------*/

#ifndef _CLIENT_PUBLISHKEYWRAPPER_H
#define _CLIENT_PUBLISHKEYWRAPPER_H

#include <string>
#include <util/Log.h>
#define CLIENT_HEADER xmlBlaster
#include <util/CompatibleCorba.h>


namespace client {
   
/**
 * This class encapsulates the Message meta data and unique identifier (key) 
 * of a publish() message.<p />
 * A typical <b>publish</b> key could look like this:<br />
 * <pre>
 *     &lt;key oid='4711' contentMime='text/xml'>
 *        &lt;AGENT id='192.168.124.20' subId='1' type='generic'>
 *           &lt;DRIVER id='FileProof' pollingFreq='10'>
 *           &lt;/DRIVER>
 *        &lt;/AGENT>
 *     &lt;/key>
 * </pre>
 * <br />
 * Note that the AGENT and DRIVER tags are application know how, which you 
 * have to supply to the wrap() method.<br />
 * A well designed xml hierarchy of your problem domain is essential for a 
 * proper working xmlBlaster <p />
 * This is exactly the key how it was published from the data source.
 *
 * @see org.xmlBlaster.util.PublishKeyWrapperBase <p />
 * see xmlBlaster/src/dtd/PublishKeyWrapper.xml   <p />
 * see http://www.w3.org/TR/xpath
 */
   class PublishKeyWrapper : public KeyWrapper {
      
   private:
      string me() {
	 return "PublishKeyWrapper";
      }
      
      /** value from attribute <key oid="" contentMime="..."> */
      string contentMime_;
      /** value from attribute <key oid="" contentMimeExtended="..."> */
      string contentMimeExtended_;
      string clientTags_;

      
   public:
      /**
       * Constructor with given oid and contentMime.
       * @param oid is optional and will be generated if ""
       * @param contentMime the MIME type of the content e.g. "text/xml" or 
       * "image/gif"
       * @param contentMimeExtended Use it for whatever, e.g. the version 
       *        number or parser infos for your content<br />
       *        set to null if not needed
       */
      PublishKeyWrapper(const string &oid="", const string &contentMime="", 
			const string &contentMimeExtended="") : 
	 KeyWrapper(oid) {
	 if (contentMime != "") contentMime_ = contentMime;
	 else                   contentMime_ = "text/plain";
	 contentMimeExtended_ = contentMimeExtended;
	 clientTags_ = "";
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
	 return wrap(clientTags_);
      }
      
      
      /**
       * May be used to integrate your application tags. <p />
       * Derive your special PublishKey class from this.
       * @param str Your tags in ASCII XML syntax
       */
      string wrap(const string &str) {
	 clientTags_ = str;
	 string ret = "<key oid='";
	 ret += oid_ + "' contentMime='" + contentMime_ + "'";
	 if (contentMimeExtended_ != "")
	    ret += " contentMimeExtended='" + contentMimeExtended_ + "'";
	 ret += ">\n" + clientTags_ + "\n</key>";
	 return ret;
      }
   };
};

#endif
