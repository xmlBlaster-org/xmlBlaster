/*
 * xmlBlaster/src/c++/util/thread/ThreadImpl.cpp
 *
 * Used to switch between the http://www.boost.org C++ multi threading library
 * and http://omniorb.sourceforge.net/ omniORB omnithread C++ multi threading library (default)
 * Add 
 *  thread.impl   = BOOST
 * to your build.properties (or as command line switch) to force using boost
 */
#ifdef BOOST
#include "ThreadImplBoost.cc"
#else // OMNITHREAD
#include "ThreadImplOmniOrb.cc"
#endif

