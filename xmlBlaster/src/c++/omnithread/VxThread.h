#ifndef __VXTHREAD_H__
#define __VXTHREAD_H__
/*
%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
%% Project:     omniORB
%% Filename:    $Filename$
%% Author:      Guillaume/Bill ARRECKX
%%              Copyright Wavetek Wandel & Goltermann, Plymouth.
%% Description: OMNI thread implementation classes for VxWorks threads
%% Notes:
%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
%% $Log: VxThread.h,v $
%% Revision 1.1  2003/12/16 17:03:16  ruff
%% Updated to omniORB-4.0.3
%%
%% Revision 1.1.2.2  2003/11/05 15:42:39  dgrisby
%% vxWorks omnithread fixes from Jochen Gern.
%%
%% Revision 1.2  2003/11/05 11:09:05  gernjo
%% In omni_condition implementation, removed waiters_ and waiters_lock_
%%
%% Revision 1.1.1.1  2003/10/20 10:15:20  gernjo
%% Original distribution
%%
%% Revision 1.1.2.1  2003/02/17 02:03:07  dgrisby
%% vxWorks port. (Thanks Michael Sturm / Acterna Eningen GmbH).
%%
%% Revision 1.1.1.1  2002/11/19 14:55:21  sokcevti
%% OmniOrb4.0.0 VxWorks port
%%
%% Revision 1.2  2002/06/14 12:45:50  engeln
%% unnecessary members in condition removed.
%% ---
%%
%% Revision 1.1.1.1  2002/04/02 10:08:49  sokcevti
%% omniORB4 initial realease
%%
%% Revision 1.1  2001/03/23 16:50:23  hartmut
%% Initial Version 2.8
%%
%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
*/


///////////////////////////////////////////////////////////////////////////
// Includes
///////////////////////////////////////////////////////////////////////////
#include <vxWorks.h>
#include <semLib.h>
#include <taskLib.h>


///////////////////////////////////////////////////////////////////////////
// Externs prototypes
///////////////////////////////////////////////////////////////////////////
extern "C" void omni_thread_wrapper(void* ptr);


///////////////////////////////////////////////////////////////////////////
// Exported macros
// Note: These are added as private members in each class implementation.
///////////////////////////////////////////////////////////////////////////
#define OMNI_MUTEX_IMPLEMENTATION \
   SEM_ID mutexID;	\
   bool m_bConstructed;

#define OMNI_CONDITION_IMPLEMENTATION \
   SEM_ID sema_;

#define OMNI_SEMAPHORE_IMPLEMENTATION \
   SEM_ID semID;

#define OMNI_MUTEX_LOCK_IMPLEMENTATION                  \
	if(semTake(mutexID, WAIT_FOREVER) != OK)	\
	{	\
		throw omni_thread_fatal(errno);	\
	}

#define OMNI_MUTEX_UNLOCK_IMPLEMENTATION                \
	if(semGive(mutexID) != OK)	\
	{	\
		throw omni_thread_fatal(errno);	\
	}

#define OMNI_THREAD_IMPLEMENTATION \
   friend void omni_thread_wrapper(void* ptr); \
   static int vxworks_priority(priority_t); \
   omni_condition *running_cond; \
   void* return_val; \
   int tid; \
   public: \
   static void attach(void); \
   static void detach(void); \
   static void show(void);


///////////////////////////////////////////////////////////////////////////
// Porting macros
///////////////////////////////////////////////////////////////////////////
// This is a wrapper function for the 'main' function which does not exists
//  as such in VxWorks. The wrapper creates a launch function instead,
//  which spawns the application wrapped in a omni_thread.
// Argc will always be null.
///////////////////////////////////////////////////////////////////////////
#define main( discarded_argc, discarded_argv ) \
        omni_discard_retval() \
          { \
          throw; \
          } \
        int omni_main( int argc, char **argv ); \
        void launch( ) \
          { \
          omni_thread* th = new omni_thread( (void(*)(void*))omni_main );\
          th->start();\
          }\
        int omni_main( int argc, char **argv )


#endif // ndef __VXTHREAD_H__
