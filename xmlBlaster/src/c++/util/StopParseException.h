/*-----------------------------------------------------------------------------
Name:      StopParseException.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Throw this exception to stop SAX parsing
Version:   $Id: StopParseException.h,v 1.2 2000/07/06 23:42:27 laghi Exp $
-----------------------------------------------------------------------------*/

#ifndef _UTIL_STOPPARSEEXCEPTION
#define _UTIL_STOPPARSEEXCEPTION

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
};

#endif
