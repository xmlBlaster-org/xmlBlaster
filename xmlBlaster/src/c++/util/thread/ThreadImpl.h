/*-----------------------------------------------------------------------------
Name:      ThreadImpl.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Encapsulates (and hides) threads
------------------------------------------------------------------------------*/

#ifndef _UTIL_THREAD_THREAD_H
#define _UTIL_THREAD_THREAD_H

#include <util/thread/ThreadBase.h>

/**
 * This is a hack to solve the nameconflict between the org::xmlBlaster::util::thread::Mutex class
 * and the typename Mutex in the boost library.
 */

namespace org { namespace xmlBlaster { namespace util { namespace thread {

typedef MutexClass Mutex;

}}}} // namespaces

#endif

