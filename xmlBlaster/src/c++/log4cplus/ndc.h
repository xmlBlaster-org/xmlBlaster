// Module:  Log4CPLUS
// File:    ndc.h
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

/** @file 
 * This header defined the NDC class.
 */

#ifndef _LO4CPLUS_NDC_HEADER_
#define _LO4CPLUS_NDC_HEADER_

#include <log4cplus/config.h>
#include <log4cplus/tstring.h>
#include <log4cplus/helpers/logloguser.h>
#include <log4cplus/helpers/threads.h>

#include <map>
#include <stack>

#if (defined(__MWERKS__) && defined(__MACOS__))
using std::size_t;
#endif


namespace log4cplus {
    // Forward declarations
    class NDC;
    struct DiagnosticContext;
    typedef std::stack<DiagnosticContext> DiagnosticContextStack;

    /**
     * Return a reference to the singleton object.
     */
    LOG4CPLUS_EXPORT NDC& getNDC();

    /**
     * The NDC class implements <i>nested diagnostic contexts</i> as
     * defined by Neil Harrison in the article "Patterns for Logging
     * Diagnostic Messages" part of the book "<i>Pattern Languages of
     * Program Design 3</i>" edited by Martin et al.
     *
     * <p>A Nested Diagnostic Context, or NDC in short, is an instrument
     * to distinguish interleaved log output from different sources. Log
     * output is typically interleaved when a server handles multiple
     * clients near-simultaneously.
     *
     * <p>Interleaved log output can still be meaningful if each log entry
     * from different contexts had a distinctive stamp. This is where NDCs
     * come into play.
     *
     * <p><em><b>Note that NDCs are managed on a per thread
     * basis</b></em>. NDC operations such as {@link #push}, {@link
     * #pop}, {@link #clear}, {@link #getDepth} and {@link #setMaxDepth}
     * affect the NDC of the <em>current</em> thread only. NDCs of other
     * threads remain unaffected.
     *
     * <p>For example, a server can build a per client request NDC
     * consisting the clients host name and other information contained in
     * the the request. <em>Cookies</em> are another source of distinctive
     * information. To build an NDC one uses the {@link #push}
     * operation. Simply put,
     *
     * <p><ul>
     *   <li>Contexts can be nested.
     *
     *   <p><li>When entering a context, call <code>getNDC().push()</code>. As a
     *   side effect, if there is no nested diagnostic context for the
     *   current thread, this method will create it.
     *
     *   <p><li>When leaving a context, call <code>getNDC().pop()</code>.
     *
     *   <p><li><b>When exiting a thread make sure to call {@link #remove
     *   NDC.remove()}</b>.  
     * </ul>
     *                                          
     * <p>There is no penalty for forgetting to match each
     * <code>push</code> operation with a corresponding <code>pop</code>,
     * except the obvious mismatch between the real application context
     * and the context set in the NDC.  Use of the {@link NDCContextCreator}
     * class can automate this process and make your code exception-safe.
     *
     * <p>If configured to do so, {@link #PatternLayout} and {@link
     * #TTCCLayout} instances automatically retrieve the nested diagnostic
     * context for the current thread without any user intervention.
     * Hence, even if a server is serving multiple clients
     * simultaneously, the logs emanating from the same code (belonging to
     * the same logger) can still be distinguished because each client
     * request will have a different NDC tag.
     *
     * <p>Heavy duty systems should call the {@link #remove} method when
     * leaving the run method of a thread. This ensures that the memory
     * used by the thread can be freed.
     *
     * <p>A thread may inherit the nested diagnostic context of another
     * (possibly parent) thread using the {@link #inherit inherit}
     * method. A thread may obtain a copy of its NDC with the {@link
     * #cloneStack cloneStack} method and pass the reference to any other
     * thread, in particular to a child.
     */
    class LOG4CPLUS_EXPORT NDC : protected log4cplus::helpers::LogLogUser {
    public:
        /**
         * Clear any nested diagnostic information if any. This method is
         * useful in cases where the same thread can be potentially used
         * over and over in different unrelated contexts.
         *
         * <p>This method is equivalent to calling the {@link #setMaxDepth}
         * method with a zero <code>maxDepth</code> argument.
         */
        void clear();

        /**
         * Clone the diagnostic context for the current thread.
         *
         * <p>Internally a diagnostic context is represented as a stack.  A
         * given thread can supply the stack (i.e. diagnostic context) to a
         * child thread so that the child can inherit the parent thread's
         * diagnostic context.
         *
         * <p>The child thread uses the {@link #inherit inherit} method to
         * inherit the parent's diagnostic context.
         *                                        
         * @return Stack A clone of the current thread's  diagnostic context.
         */
        DiagnosticContextStack cloneStack();

        /**
         * Inherit the diagnostic context of another thread.
         *
         * <p>The parent thread can obtain a reference to its diagnostic
         * context using the {@link #cloneStack} method.  It should
         * communicate this information to its child so that it may inherit
         * the parent's diagnostic context.
         *
         * <p>The parent's diagnostic context is cloned before being
         * inherited. In other words, once inherited, the two diagnostic
         * contexts can be managed independently.
         *
         * @param stack The diagnostic context of the parent thread.
         */
        void inherit(const DiagnosticContextStack& stack);

        /**
         * Used when printing the diagnostic context.
         */
        log4cplus::tstring get();

        /**
         * Get the current nesting depth of this diagnostic context.
         *
         * @see #setMaxDepth
         */
        size_t getDepth();

        /**
         * Clients should call this method before leaving a diagnostic
         * context.
         *
         * <p>The returned value is the value that was pushed last. If no
         * context is available, then the empty string "" is returned.
         *
         * @return String The innermost diagnostic context.
         *
         * @see NDCContextCreator
         */
        log4cplus::tstring pop();

        /**
         * Looks at the last diagnostic context at the top of this NDC
         * without removing it.
         *
         * <p>The returned value is the value that was pushed last. If no
         * context is available, then the empty string "" is returned.
         *                          
         * @return String The innermost diagnostic context.
         */
        log4cplus::tstring peek();

        /**
         * Push new diagnostic context information for the current thread.
         *
         * <p>The contents of the <code>message</code> parameter is
         * determined solely by the client.  
         *
         * @param message The new diagnostic context information.
         *
         * @see NDCContextCreator
         */
        void push(const log4cplus::tstring& message);

        /**
         * Remove the diagnostic context for this thread.
         *
         * <p>Each thread that created a diagnostic context by calling
         * {@link #push} should call this method before exiting. Otherwise,
         * the memory used by the thread cannot be reclaimed.
         */
        void remove();

        /**
         * Set maximum depth of this diagnostic context. If the current
         * depth is smaller or equal to <code>maxDepth</code>, then no
         * action is taken.
         *
         * <p>This method is a convenient alternative to multiple {@link
         * #pop} calls. Moreover, it is often the case that at the end of
         * complex call sequences, the depth of the NDC is
         * unpredictable. The <code>setMaxDepth</code> method circumvents
         * this problem.
         *
         * <p>For example, the combination
         * <pre>
         *    void foo() {
         *    &nbsp;  size_t depth = NDC.getDepth();
         *
         *    &nbsp;  ... complex sequence of calls
         *
         *    &nbsp;  NDC.setMaxDepth(depth);
         *    }
         * </pre>
         *
         * ensures that between the entry and exit of foo the depth of the
         * diagnostic stack is conserved.
         * 
         * <b>Note:</b>  Use of the {@link NDCContextCreator} class will solve
         * this particular problem.
         *
         * @see #getDepth
         */
        void setMaxDepth(size_t maxDepth);

      // Dtor
        ~NDC();

    private:
      // Methods
        DiagnosticContextStack* getPtr()
            { return static_cast<DiagnosticContextStack*>
                          (LOG4CPLUS_GET_THREAD_LOCAL_VALUE( threadLocal )); }

      // Data
        LOG4CPLUS_THREAD_LOCAL_TYPE threadLocal;

      // Disallow construction (and copying) except by getNDC()
        NDC();
        NDC(const NDC&);
        NDC& operator=(const NDC&);

      // Friends
        friend LOG4CPLUS_EXPORT NDC& getNDC();
    };



    /**
     * This is the internal object that is stored on the NDC stack.
     */
    struct LOG4CPLUS_EXPORT DiagnosticContext {
      // Ctors
        DiagnosticContext(const log4cplus::tstring& message, DiagnosticContext *parent);
        DiagnosticContext(const log4cplus::tstring& message);

      // Data
        log4cplus::tstring message; /*!< The message at this context level. */
        log4cplus::tstring fullMessage; /*!< The entire message stack. */
    };


    /**
     * This class ensures that a {@link NDC#push} call is always matched with
     * a {@link NDC#pop} call even in the face of exceptions.
     */
    class LOG4CPLUS_EXPORT NDCContextCreator {
    public:
        /** Pushes <code>msg</code> onto the NDC stack. */
        NDCContextCreator(const log4cplus::tstring& msg) { getNDC().push(msg); }

        /** Pops the NDC stack. */
        ~NDCContextCreator() { getNDC().pop(); }
    };

} // end namespace log4cplus


#endif // _LO4CPLUS_NDC_HEADER_
