

#ifndef _UTIL_PLATFORMUTILS_C
#define _UTIL_PLATFORMUTILS_C

#pragma warning(disable:4786)

#include <xercesc/util/PlatformUtils.hpp>
#include "PlatformUtils.h"

using namespace org::xmlBlaster::util;

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

