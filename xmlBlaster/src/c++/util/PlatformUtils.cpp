

#ifndef _UTIL_PLATFORMUTILS_C
#define _UTIL_PLATFORMUTILS_C

#pragma warning(disable:4786)

#include <xercesc/util/PlatformUtils.hpp>
#include "PlatformUtils.h"

using namespace org::xmlBlaster::util;

#if defined(XERCES_HAS_CPP_NAMESPACE)
	// Since Xerces 2.2 namespace is introduced:
   XERCES_CPP_NAMESPACE_USE
#endif

/**
 *
 * Required Library Initailization.
 *
 *
 *
 */

void PlatformUtils::init()
{

  XMLPlatformUtils::Initialize();

}

#endif

