// Module:  Log4CPLUS
// File:    nullappender.cxx
// Created: 6/2003
// Author:  Tad E. Smith
//
//
// Copyright (C) Tad E. Smith  All rights reserved.
//
// This software is published under the terms of the Apache Software
// License version 1.1, a copy of which has been included with this
// distribution in the LICENSE.APL file.
//
// $Log: nullappender.cxx,v $
// Revision 1.1  2004/02/08 22:52:24  ruff
// Added http://log4cplus.sourceforge.net for C++ logging, Version 1.0.1
//
// Revision 1.2  2003/07/30 05:52:29  tcsmith
// Modified to remove "unused parameter" warning.
//
// Revision 1.1  2003/06/23 21:02:53  tcsmith
// Initial version.
//

#include <log4cplus/nullappender.h>

using namespace std;
using namespace log4cplus;
using namespace log4cplus::helpers;



///////////////////////////////////////////////////////////////////////////////
// log4cplus::NullAppender ctors and dtor
///////////////////////////////////////////////////////////////////////////////

log4cplus::NullAppender::NullAppender()
{
}


log4cplus::NullAppender::NullAppender(const log4cplus::helpers::Properties& properties)
: Appender(properties)
{
}



log4cplus::NullAppender::~NullAppender()
{
    destructorImpl();
}



///////////////////////////////////////////////////////////////////////////////
// log4cplus::NullAppender public methods
///////////////////////////////////////////////////////////////////////////////

void
log4cplus::NullAppender::close()
{
}



///////////////////////////////////////////////////////////////////////////////
// log4cplus::NullAppender protected methods
///////////////////////////////////////////////////////////////////////////////

// This method does not need to be locked since it is called by
// doAppend() which performs the locking
void
log4cplus::NullAppender::append(const spi::InternalLoggingEvent&)
{
}


