/*-----------------------------------------------------------------------------
Name:      QosWrapper.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Handling one QoS message
-----------------------------------------------------------------------------*/

#ifndef _CLIENT_QOSWRAPPER_H
#define _CLIENT_QOSWRAPPER_H

#include <string>

namespace org { namespace xmlBlaster {

/**
 * This base class encapsulates XmlQoS which you send to xmlBlaster.
 * <p />
 * A typical minimal qos could look like this:<br />
 * <pre>
 *     &lt;qos>
 *     &lt;/qos>
 * </pre>
 * <br />
 * <p />
 * see xmlBlaster/src/dtd/XmlQoS.xml
 */


   class QosWrapper {

   private:

      string me() const {
         return "QosWrapper";
      }

   public:
      /**
       * Constructs this base object
       */
      QosWrapper() {
      }


      /**
       * Converts the data in XML ASCII string.
       * <p />
       * This is the minimal key representation.<br />
       * You should provide your own toString() method.
       * @return An XML ASCII string
       */
      string toString() const {
         return "<qos>\n</qos>";
      }
   }; // class
}} // namespace

#endif
