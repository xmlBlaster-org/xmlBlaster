/*-----------------------------------------------------------------------------
Name:      KeyWrapper.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Handling one xmlKey
Version:   $Id: KeyWrapper.h,v 1.1 2000/07/06 22:55:44 laghi Exp $
-----------------------------------------------------------------------------*/

#ifndef _CLIENT_KEYWRAPPER_H
#define _CLIENT_KEYWRAPPER_H

#include <string>
#include <util/Log.h>
#define CLIENT_HEADER xmlBlaster
#include <util/CompatibleCorba.h>

namespace client {
    
    /**
     * This base class encapsulates XmlKey which you send to xmlBlaster.
     * <p /> A typical minimal key could look like this:<br />
     * <pre>
     *     &lt;key oid='4711'>
     *     &lt;/key>
     * </pre> <br />
     * Note that tags inside of key are application know how, which you have 
     * to supply.<br />  A well designed xml hierarchy of your problem domain
     * is essential for a proper working xmlBlaster <p />
     * see xmlBlaster/src/dtd/XmlKey.xml
     */
   class KeyWrapper {

   private:

      string me() {
	 return "KeyWrapper";
      }
 
      /** 
       * The default oid value is an empty string, in which case xmlBlaster 
       * generates an oid for you 
       */
   protected:

      string oid_;
	
	
      /**
       * Constructor.
       */
   public:
      KeyWrapper(const string &oid = "") : oid_(oid) {
      }
      
      /**
       * Converts the data in XML ASCII string.
       * <p />
       * This is the minimal key representation.<br />
       * You should provide your own toString() method.
       * @return An XML ASCII string
       */
      string toString() const {
	 string ret = "<key oid='";
	 ret += oid_ + "'>\n </key>";
	 return ret;
      }
   };
}; 

#endif

