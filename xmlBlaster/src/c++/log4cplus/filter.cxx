// Module:  Log4CPLUS
// File:    filter.cxx
// Created: 5/2003
// Author:  Tad E. Smith
//
//
// Copyright (C) Tad E. Smith  All rights reserved.
//
// This software is published under the terms of the Apache Software
// License version 1.1, a copy of which has been included with this
// distribution in the LICENSE.APL file.
//
// $Log: filter.cxx,v $
// Revision 1.2  2004/02/11 08:45:05  ruff
// Updated to version 1.0.2
//
// Revision 1.6  2003/06/23 20:56:43  tcsmith
// Modified to support the changes in the spi::InternalLoggingEvent class.
//
// Revision 1.5  2003/06/12 23:50:21  tcsmith
// Modified to support the rename of the toupper and tolower methods.
//
// Revision 1.4  2003/06/09 18:12:07  tcsmith
// Fixed compilation error.
//
// Revision 1.3  2003/06/08 17:39:12  tcsmith
// Corrected the LogLevelMatchFilter::decide() implementation.
//
// Revision 1.2  2003/06/04 00:13:57  tcsmith
// Corrected some of the filtering implementations.
//
// Revision 1.1  2003/05/28 17:36:03  tcsmith
// Initial version.
//

#include <log4cplus/spi/filter.h>
#include <log4cplus/helpers/loglog.h>
#include <log4cplus/helpers/stringhelper.h>

using namespace log4cplus;
using namespace log4cplus::spi;
using namespace log4cplus::helpers;


///////////////////////////////////////////////////////////////////////////////
// global methods
///////////////////////////////////////////////////////////////////////////////

FilterResult
log4cplus::spi::checkFilter(const Filter* filter, 
                            const InternalLoggingEvent& event)
{
    const Filter* currentFilter = filter;
    while(currentFilter) {
        FilterResult result = currentFilter->decide(event);
        if(result != NEUTRAL) {
            return result;
        }

        currentFilter = currentFilter->next.get();
    }

    return ACCEPT;
}



///////////////////////////////////////////////////////////////////////////////
// Filter implementation
///////////////////////////////////////////////////////////////////////////////

Filter::Filter()
{
}


Filter::~Filter()
{
}


void
Filter::appendFilter(FilterPtr filter)
{
    if(next.get() == 0) {
        next = filter;
    }
    else {
        next->appendFilter(filter);
    }
}



///////////////////////////////////////////////////////////////////////////////
// DenyAllFilter implementation
///////////////////////////////////////////////////////////////////////////////

FilterResult
DenyAllFilter::decide(const InternalLoggingEvent&) const
{
    return DENY;
}



///////////////////////////////////////////////////////////////////////////////
// LogLevelMatchFilter implementation
///////////////////////////////////////////////////////////////////////////////

LogLevelMatchFilter::LogLevelMatchFilter()
{
    init();
}



LogLevelMatchFilter::LogLevelMatchFilter(const Properties& properties)
{
    init();

    tstring tmp = properties.getProperty( LOG4CPLUS_TEXT("AcceptOnMatch") );
    acceptOnMatch = (toLower(tmp) == LOG4CPLUS_TEXT("true"));

    tmp = properties.getProperty( LOG4CPLUS_TEXT("LogLevelToMatch") );
    logLevelToMatch = getLogLevelManager().fromString(tmp);
}


void
LogLevelMatchFilter::init()
{
    acceptOnMatch = true;
    logLevelToMatch = NOT_SET_LOG_LEVEL;
}


FilterResult
LogLevelMatchFilter::decide(const InternalLoggingEvent& event) const
{
    if(logLevelToMatch == NOT_SET_LOG_LEVEL) {
        return NEUTRAL;
    }

    bool matchOccured = (logLevelToMatch == event.getLogLevel());
       
    if(matchOccured) {
        return (acceptOnMatch ? ACCEPT : DENY);
    }
    else {
        return NEUTRAL;
    }
}



///////////////////////////////////////////////////////////////////////////////
// LogLevelRangeFilter implementation
///////////////////////////////////////////////////////////////////////////////

LogLevelRangeFilter::LogLevelRangeFilter()
{
    init();
}



LogLevelRangeFilter::LogLevelRangeFilter(const Properties& properties)
{
    init();

    tstring tmp = properties.getProperty( LOG4CPLUS_TEXT("AcceptOnMatch") );
    acceptOnMatch = (toLower(tmp) == LOG4CPLUS_TEXT("true"));

    tmp = properties.getProperty( LOG4CPLUS_TEXT("LogLevelMin") );
    logLevelMin = getLogLevelManager().fromString(tmp);

    tmp = properties.getProperty( LOG4CPLUS_TEXT("LogLevelMax") );
    logLevelMax = getLogLevelManager().fromString(tmp);
}


void
LogLevelRangeFilter::init()
{
    acceptOnMatch = true;
    logLevelMin = NOT_SET_LOG_LEVEL;
    logLevelMax = NOT_SET_LOG_LEVEL;
}


FilterResult
LogLevelRangeFilter::decide(const InternalLoggingEvent& event) const
{
    if((logLevelMin != NOT_SET_LOG_LEVEL) && (event.getLogLevel() < logLevelMin)) {
        // priority of event is less than minimum
        return DENY;
    }

    if((logLevelMax != NOT_SET_LOG_LEVEL) && (event.getLogLevel() > logLevelMax)) {
        // priority of event is greater than maximum
        return DENY;
    }

    if(acceptOnMatch) {
        // this filter set up to bypass later filters and always return
        // accept if priority in range
        return ACCEPT;
    }
    else {
        // event is ok for this filter; allow later filters to have a look...
        return NEUTRAL;
    }
}



///////////////////////////////////////////////////////////////////////////////
// StringMatchFilter implementation
///////////////////////////////////////////////////////////////////////////////

StringMatchFilter::StringMatchFilter()
{
    init();
}



StringMatchFilter::StringMatchFilter(const Properties& properties)
{
    init();

    tstring tmp = properties.getProperty( LOG4CPLUS_TEXT("AcceptOnMatch") );
    acceptOnMatch = (toLower(tmp) == LOG4CPLUS_TEXT("true"));

    stringToMatch = properties.getProperty( LOG4CPLUS_TEXT("StringToMatch") );
}


void
StringMatchFilter::init()
{
    acceptOnMatch = true;
}


FilterResult
StringMatchFilter::decide(const InternalLoggingEvent& event) const
{
    const tstring& message = event.getMessage();

    if(stringToMatch.length() == 0 || message.length() == 0) {
        return NEUTRAL;
    }

    if(message.find(stringToMatch) == tstring::npos) {
        return NEUTRAL;
    }
    else {  // we've got a match
        return (acceptOnMatch ? ACCEPT : DENY);
    }
}

