// Module:  Log4CPLUS
// File:    timehelper.h
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

/** @file */

#ifndef _LOG4CPLUS_HELPERS_TIME_HELPER_HEADER_
#define _LOG4CPLUS_HELPERS_TIME_HELPER_HEADER_

#include <log4cplus/config.h>
#include <log4cplus/tstring.h>

#ifdef TM_IN_SYS_TIME
#include <sys/time.h>
#endif

#include <time.h>


namespace log4cplus {
    namespace helpers {

        /**
         * This class represents a Epoch time with microsecond accuracy.
         */
        class LOG4CPLUS_EXPORT Time {
        public:
            Time();
            Time(long tv_sec, long tv_usec);
            Time(time_t time);

            /**
             * Returns the current time using the <code>gettimeofday()</code>
             * method if it is available on the current platform.  (Not on 
             * WIN32.)
             */
            static Time gettimeofday();

          // Methods
            /**
             * Returns <i>seconds</i> value.
             */
            long sec() const { return tv_sec; }

            /**
             * Returns <i>microseconds</i> value.
             */
            long usec() const { return tv_usec; }

            /**
             * Sets the <i>seconds</i> value.
             */
            void sec(long s) { tv_sec = s; }

            /**
             * Sets the <i>microseconds</i> value.
             */
            void usec(long us) { tv_usec = us; }

            /**
             * Sets this Time using the <code>mktime</code> function.
             */
            int setTime(struct tm* t);

            /**
             * Returns this Time as a <code>time_t></code> value.
             */
            time_t getTime() const;

            /**
             * Populates <code>tm</code> using the <code>gmtime()</code>
             * function.
             */
            void gmtime(struct tm* t) const;

            /**
             * Populates <code>tm</code> using the <code>localtime()</code>
             * function.
             */
            void localtime(struct tm* t) const;

            /**
             * Returns a string with a "formatted time" specified by
             * <code>fmt</code>.  It used the <code>strftime()</code>
             * function to do this.  
             * <p>
             * Look at your platform's <code>strftime()</code> documentation
             * for the formatting options available.
             * <p>
             * The following additional options are provided:<br>
             * <code>%q</code> - 3 character field that provides milliseconds
             * <code>%Q</code> - 7 character field that provides fractional 
             * milliseconds.
             */
            log4cplus::tstring getFormattedTime(const log4cplus::tstring& fmt,
                                                bool use_gmtime = false) const;

          // Operators
            Time& operator+=(const Time& rhs);
            Time& operator-=(const Time& rhs);
            Time& operator/=(long rhs);
            Time& operator*=(long rhs);
	    bool operator==(const Time& rhs) { return tv_sec == rhs.tv_sec &&
		                                      tv_usec == rhs.tv_usec; }
	    bool operator!=(const Time& rhs) { return !(*this == rhs); }

        private:
          // Data
            long  tv_sec;   /* seconds */
            long  tv_usec;  /* microseconds */
        };

    }
}


LOG4CPLUS_EXPORT const log4cplus::helpers::Time operator+
                                   (const log4cplus::helpers::Time& lhs,
                                    const log4cplus::helpers::Time& rhs);
LOG4CPLUS_EXPORT const log4cplus::helpers::Time operator-
                                   (const log4cplus::helpers::Time& lhs,
                                    const log4cplus::helpers::Time& rhs);
LOG4CPLUS_EXPORT const log4cplus::helpers::Time operator/
                                   (const log4cplus::helpers::Time& lhs,
                                    long rhs);
LOG4CPLUS_EXPORT const log4cplus::helpers::Time operator*
                                   (const log4cplus::helpers::Time& lhs,
                                    long rhs);

LOG4CPLUS_EXPORT bool operator<(const log4cplus::helpers::Time& lhs,
                                const log4cplus::helpers::Time& rhs);
LOG4CPLUS_EXPORT bool operator<=(const log4cplus::helpers::Time& lhs,
                                 const log4cplus::helpers::Time& rhs);

LOG4CPLUS_EXPORT bool operator>(const log4cplus::helpers::Time& lhs,
                                const log4cplus::helpers::Time& rhs);
LOG4CPLUS_EXPORT bool operator>=(const log4cplus::helpers::Time& lhs,
                                 const log4cplus::helpers::Time& rhs);

LOG4CPLUS_EXPORT bool operator==(const log4cplus::helpers::Time& lhs,
                                 const log4cplus::helpers::Time& rhs);
LOG4CPLUS_EXPORT bool operator!=(const log4cplus::helpers::Time& lhs,
                                 const log4cplus::helpers::Time& rhs);

#endif // _LOG4CPLUS_HELPERS_TIME_HELPER_HEADER_

