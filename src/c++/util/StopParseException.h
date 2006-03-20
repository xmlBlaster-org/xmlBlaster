/*-----------------------------------------------------------------------------
Name:      StopParseException.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Throw this exception to stop SAX parsing
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
    class Dll_Export StopParseException {

      public:
       StopParseException() {
       }
    };
}}} // namespace

#endif
