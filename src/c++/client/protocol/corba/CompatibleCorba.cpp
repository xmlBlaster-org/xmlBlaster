/*----------------------------------------------------------------------------
Name:      CompatibleCorba.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   functions to encapsulate corba-implementor specific stuff
Version:   $Id$
Author:    <Michele Laghi> laghi@swissinfo.org
----------------------------------------------------------------------------*/
#ifdef _WINDOWS
#pragma warning(disable:4786)
#pragma warning(disable:4251)
#endif

#include <client/protocol/corba/CompatibleCorba.h>
#include <util/lexical_cast.h>

using namespace std;

/*
 * Further implementor specific macros which must be called after having
 * included the CORBA header files 
 */

/******************************************************************
 *                      OMNIORB (4.1.1)
 ******************************************************************/
#ifdef  XMLBLASTER_OMNIORB

string to_string(const CORBA::Exception &ex) {
   string name(ex._name());
   string repId(ex._rep_id());

   string ret = "CORBA::Exception name=" + name
          + " repId=" + repId;
   return ret;
}

#endif  // XMLBLASTER_OMNIORB

/******************************************************************
 *                      ORBACUS (OB-4.03)
 ******************************************************************/
#ifdef  XMLBLASTER_ORBACUS

string to_string(const CORBA::Exception &ex) {
   string name(ex._name());
   string repId(ex._rep_id());

   string ret = "CORBA::Exception name=" + name
          + " repId=" + repId;
   return ret;
}

#endif  // XMLBLASTER_ORBACUS

/*****************************************************************
 *                     MICO (ver. 2.3.1)
 *****************************************************************/
#ifdef  XMLBLASTER_MICO

string to_string(const CORBA::Exception &ex) {
   return ex._repoid();
}

#endif  // XMLBLASTER_MICO

/*****************************************************************
 *                    ORBIX 2000 (ver 2.0) 
 *****************************************************************/
#ifdef XMLBLASTER_ORBIX

string to_string(const CORBA::Exception &ex ) {
  return ex._rep_id();
}

#endif //XMLBLASTER_ORBIX

#ifdef  XMLBLASTER_TAO

string to_string(const CORBA::Exception &ex) {
   string name(ex._name());
   string repId(ex._rep_id());
   //string typeCode(ex._type());
   //string info(ex._info()); // info is not portable

   string ret = "CORBA::Exception name=" + name
          + " repId=" + repId
          //+ " typeCode=" + typeCode
          //+ " info=" + info
          ;
   return ret.c_str();
}

#endif  // XMLBLASTER_TAO

