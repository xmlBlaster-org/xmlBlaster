/*-----------------------------------------------------------------------------
Name:      StopParseException.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Throw this exception to stop SAX parsing
Version:   $Id: StopParseException.h,v 1.3 2001/11/26 09:20:59 ruff Exp $
-----------------------------------------------------------------------------*/

#ifndef _UTIL_STOPPARSEEXCEPTION
#define _UTIL_STOPPARSEEXCEPTION

namespace org { namespace xmlBlaster {
namespace util {

    /**
     * Throw this exception to stop SAX parsing. <p />
     * Usually thrown in startElement() or endElement() if
     * you are not interested in the following tags anymore.<br />
     */
    class StopParseException {

      public:
       StopParseException() {
       }
    };
}}} // namespace

#endif
