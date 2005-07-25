// Module:  Log4CPLUS
// File:    layout.cxx
// Created: 6/2001
// Author:  Tad E. Smith
//
//
// Copyright (C) Tad E. Smith  All rights reserved.
//
// This software is published under the terms of the Apache Software
// License version 1.1, a copy of which has been included with this
// distribution in the LICENSE.APL file.
//
// $Log: layout.cxx,v $
// Revision 1.2  2004/02/11 08:45:05  ruff
// Updated to version 1.0.2
//
// Revision 1.17  2003/07/30 05:04:27  tcsmith
// Changed to make "localtime" the default.
//
// Revision 1.16  2003/06/23 20:56:43  tcsmith
// Modified to support the changes in the spi::InternalLoggingEvent class.
//
// Revision 1.15  2003/06/13 17:45:24  tcsmith
// Added: using namespace std.
//
// Revision 1.14  2003/06/12 23:50:21  tcsmith
// Modified to support the rename of the toupper and tolower methods.
//
// Revision 1.13  2003/06/12 23:11:54  tcsmith
// Changed the default TTCCLayout date pattern to include milliseconds.
//
// Revision 1.12  2003/06/11 22:48:33  tcsmith
// Modified to use the new log4cplus::helpers::Time type.
//
// Revision 1.11  2003/06/09 18:13:16  tcsmith
// Changed the ctor to take a 'const' Properties object.
//
// Revision 1.10  2003/06/04 19:38:35  tcsmith
// Added a use_gmtime flag to TTCCLayout to specifiy whether dates should be
// logged using gmtime or localtime function calls.
//
// Revision 1.9  2003/06/04 18:59:00  tcsmith
// Modified to use the "time" function defined in the timehelper.h header.
//
// Revision 1.8  2003/05/21 22:14:46  tcsmith
// Fixed compiler warning: "conversion from 'size_t' to 'int', possible loss
// of data".
//
// Revision 1.7  2003/04/19 23:04:31  tcsmith
// Fixed UNICODE support.
//
// Revision 1.6  2003/04/18 21:19:42  tcsmith
// Converted from std::string to log4cplus::tstring.
//
// Revision 1.5  2003/04/03 00:58:59  tcsmith
// Standardized the formatting.
//

#include <log4cplus/layout.h>
#include <log4cplus/helpers/stringhelper.h>
#include <log4cplus/helpers/timehelper.h>
#include <log4cplus/spi/loggingevent.h>


using namespace std;
using namespace log4cplus;
using namespace log4cplus::helpers;
using namespace log4cplus::spi;


///////////////////////////////////////////////////////////////////////////////
// log4cplus::SimpleLayout public methods
///////////////////////////////////////////////////////////////////////////////

void
SimpleLayout::formatAndAppend(log4cplus::tostream& output, 
                              const log4cplus::spi::InternalLoggingEvent& event)
{
    output << llmCache.toString(event.getLogLevel()) 
           << LOG4CPLUS_TEXT(" - ")
           << event.getMessage() 
           << LOG4CPLUS_TEXT("\n");
}



///////////////////////////////////////////////////////////////////////////////
// log4cplus::TTCCLayout ctors and dtor
///////////////////////////////////////////////////////////////////////////////

TTCCLayout::TTCCLayout(bool use_gmtime)
// %q is no formatting option -> VC++ 2005 crashes, it is used to switch on milli/nanoseconds
// changed to 'qq' or 'QQ' by Marcel Ruff 2005-07-25
// -> see timehelper.cxx, there it is used
//: dateFormat( LOG4CPLUS_TEXT("%m-%d-%y %H:%M:%S,%q") ),
: dateFormat( LOG4CPLUS_TEXT("%m-%d-%y %H:%M:%S,qq") ),
  use_gmtime(use_gmtime)
{
}


TTCCLayout::TTCCLayout(const log4cplus::helpers::Properties& properties)
: Layout(properties),
// %q is no formatting option -> VC++ 2005 crashes, it is used to switch on milli/nanoseconds
// changed to 'qq' or 'QQ' by Marcel Ruff 2005-07-25
// -> see timehelper.cxx, there it is used
//  dateFormat( LOG4CPLUS_TEXT("%m-%d-%y %H:%M:%S,%q") ),
  dateFormat( LOG4CPLUS_TEXT("%m-%d-%y %H:%M:%S,qq") ),
  use_gmtime(false)
{
    if(properties.exists( LOG4CPLUS_TEXT("DateFormat") )) {
        dateFormat  = properties.getProperty( LOG4CPLUS_TEXT("DateFormat") );
    }

    tstring tmp = properties.getProperty( LOG4CPLUS_TEXT("Use_gmtime") );
    use_gmtime = (toLower(tmp) == LOG4CPLUS_TEXT("true"));
}


TTCCLayout::~TTCCLayout()
{
}



///////////////////////////////////////////////////////////////////////////////
// log4cplus::TTCCLayout public methods
///////////////////////////////////////////////////////////////////////////////

void
TTCCLayout::formatAndAppend(log4cplus::tostream& output, 
                            const log4cplus::spi::InternalLoggingEvent& event)
{
    output << event.getTimestamp().getFormattedTime(dateFormat, use_gmtime) 
           << LOG4CPLUS_TEXT(" [")
           << event.getThread()
           << LOG4CPLUS_TEXT("] ")
           << llmCache.toString(event.getLogLevel()) 
           << LOG4CPLUS_TEXT(" ")
           << event.getLoggerName()
           << LOG4CPLUS_TEXT(" <")
           << event.getNDC() 
           << LOG4CPLUS_TEXT("> - ")
           << event.getMessage()
           << LOG4CPLUS_TEXT("\n");
}




