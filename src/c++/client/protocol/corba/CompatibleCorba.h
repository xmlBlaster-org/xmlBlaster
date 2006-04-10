/*----------------------------------------------------------------------------
Name:      CompatibleCorba.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Helper header to encapsulate all corba-implementor specific stuff
Author:    <Michele Laghi> laghi@swissinfo.org
----------------------------------------------------------------------------*/

#ifndef _COMPATIBLECORBA_H
#define _COMPATIBLECORBA_H

#include <string>

/*
 * Implementor specific macros (for includes etc.)
 * The invocation of this header is done maximum one time per compilation unit. Since the server side 
 * will include different files than the client side (see SERVER_HEADER and CLIENT_HEADER) this might cause
 * problems in units acting as a server and a client. In such cases invoke it first as a server.
 */

/******************************************************************
 *                      OMNIORB (Nils.Nilson@in-gmbh.de)
 ******************************************************************/
#if defined(XMLBLASTER_OMNIORB)
#define ORB_IS_THREAD_SAFE        true
#ifdef SERVER_HEADER
#  include <generated/xmlBlaster.h>
#else
#  include <generated/xmlBlaster.h> // client side include header
#endif
#define CORBA_HEADER              <omniORB4/CORBA.h>
//#define CORBA_HEADER              <omniORB3/CORBA.h>
#define COSCONTAINEMENT           <not_implemented.h>
#define COSOBJECTIDENTITY         <not_implemented.h>
#define COSREFERENCE              <not_implemented.h>
#define COSEVENTCHANNELADMIN      <not_implemented.h>
#define COSEVENTCOMM              <not_implemented.h>
#define COSRELATIONSHIPS          <not_implemented.h>
#define COSGRAPHS                 <not_implemented.h>
#define COSTIME                   <not_implemented.h>
#define COSGRAPHEXTENTSION        <not_implemented.h>
#define COSTRADING                <not_implemented.h>
#define COSNAMING                 <omniORB4/Naming.hh>
//#define COSNAMING                 <omniORB3/Naming.hh>
#define COSPROPERTY               <not_implemented.h>
#define COSTRADINGREPOS           <not_implemented.h>
#define COSTYPEDEVENTCHANNELADMIN <not_implemented.h>
#define COSTYPEDEVENT             <not_implemented.h>
#define UPDATE_THROW_SPECIFIER    
#define PING_THROW_SPECIFIER      

/******************************************************************
 *                      ORBACUS (OB-4.03) and (OB-4.1.0)
 ******************************************************************/
#elif defined(XMLBLASTER_ORBACUS)
#define ORB_IS_THREAD_SAFE        true
#define CORBA_HEADER              <OB/CORBA.h>
#define COSCONTAINEMENT           <not_implemented.h>
#define COSOBJECTIDENTITY         <not_implemented.h>
#define COSREFERENCE              <not_implemented.h>
#define COSEVENTCHANNELADMIN      <OB/CosEventChannelAdmin.h>
#define COSEVENTCOMM              <OB/CosEventComm.h>
#define COSRELATIONSHIPS          <not_implemented.h>
#define COSGRAPHS                 <not_implemented.h>
#define COSTIME                   <not_implemented.h>
#define COSGRAPHEXTENTSION        <not_implemented.h>
#define COSTRADING                <not_implemented.h>
#define COSNAMING                 <OB/CosNaming.h>
#define COSPROPERTY               <OB/CosProperty.h>
#define COSTRADINGREPOS           <not_implemented.h>
#define COSTYPEDEVENTCHANNELADMIN <OB/CosTypedEventChannelAdmin.h>
#define COSTYPEDEVENT             <OB/CosTypedEventComm.h>
#define UPDATE_THROW_SPECIFIER    
#define PING_THROW_SPECIFIER      

# include CORBA_HEADER
#ifdef SERVER_HEADER
#  include <generated/xmlBlaster_skel.h>
#else
#  include <generated/xmlBlaster.h> // client side include header
#endif

/*****************************************************************
 *                     MICO (ver. 2.3.1)
 *****************************************************************/
#elif defined(XMLBLASTER_MICO)
# include <mico/version.h>
# if MICO_BIN_VERSION < 0x02030b  // mico older MICO_VERSION 2.3.11
#   define ORB_IS_THREAD_SAFE   false
#   define MICO_INCLUDE_PREAFIX mico
# else
#   define ORB_IS_THREAD_SAFE   true
#   define MICO_INCLUDE_PREAFIX coss
# endif
#ifdef SERVER_HEADER
#  include <generated/xmlBlaster.h>
#else
#  include <generated/xmlBlaster.h> // client side include header
#endif
#define CORBA_HEADER              <CORBA.h>
#define COSCONTAINEMENT           <MICO_INCLUDE_PREAFIX/CosContainment.h>
#define COSOBJECTIDENTITY         <MICO_INCLUDE_PREAFIX/CosObjectIdentity.h>
#define COSREFERENCE              <MICO_INCLUDE_PREAFIX/CosReference.h>
#define COSEVENTCHANNELADMIN      <MICO_INCLUDE_PREAFIX/CosEventChannelAdmin.h>
#define COSEVENTCOMM              <MICO_INCLUDE_PREAFIX/CosEventComm.h>
#define COSRELATIONSHIPS          <MICO_INCLUDE_PREAFIX/CosRelationships.h>
#define COSGRAPHS                 <MICO_INCLUDE_PREAFIX/CosGraphs.h>
#define COSTIME                   <MICO_INCLUDE_PREAFIX/CosTime.h>
#define COSGRAPHSEXTENSION        <MICO_INCLUDE_PREAFIX/CosGraphsExtension.h>
#define COSTRADING                <MICO_INCLUDE_PREAFIX/CosTrading.h>
#define COSNAMING                 <MICO_INCLUDE_PREAFIX/CosNaming.h>
#define COSPROPERTY               <not_implemented.h>
#define COSTRADINGREPOS           <MICO_INCLUDE_PREAFIX/CosTradingRepos.h>
#define COSTYPEDEVENTCHANNELADMIN <not_implemented.h>
#define COSTYPEDEVENT             <not_implemented.h>
#define UPDATE_THROW_SPECIFIER    
#define PING_THROW_SPECIFIER      

/*****************************************************************
 *                     TAO (ver. 2.3.1)
 *****************************************************************/
#elif defined(XMLBLASTER_TAO)
#define ORB_IS_THREAD_SAFE        true
#ifdef SERVER_HEADER
#  include <generated/xmlBlasterS.h>
#else
#  include <generated/xmlBlasterC.h> // client side include header
#endif
#define CORBA_HEADER              <tao/corba.h>
#define COSCONTAINEMENT           <not_implemented.h> // what is this ??
#define COSOBJECTIDENTITY         <not_implemented.h>
#define COSREFERENCE              <not_implemented.h>
#define COSEVENTCHANNELADMIN      <orbsvcs/CosEventChannelAdminC.h>
#define COSEVENTCOMM              <not_implemented.h>
#define COSRELATIONSHIPS          <not_implemented.h>
#define COSGRAPHS                 <not_implemented.h>
#define COSTIME                   <orbsvcs/CosTimeC.h>
#define COSGRAPHSEXTENSION        <not_implemented.h>
#define COSTRADING                <not_implemented.h>
#define COSNAMING                 <orbsvcs/CosNamingC.h>
#define COSPROPERTY               <orbsvcs/CosPropertyServiceC.h>
#define COSTRADINGREPOS           <not_implemented.h>
#define COSTYPEDEVENTCHANNELADMIN <not_implemented.h>
#define COSTYPEDEVENT             <not_implemented.h>
#define UPDATE_THROW_SPECIFIER    ACE_THROW_SPEC (( CORBA::SystemException, serverIdl::XmlBlasterException ))
#define PING_THROW_SPECIFIER      ACE_THROW_SPEC (( CORBA::SystemException ))


/*****************************************************************
 *                     ORBIX 2000 (ver. 2.0 )
 *****************************************************************/
#elif defined(XMLBLASTER_ORBIX)
#define ORB_IS_THREAD_SAFE        true
#ifdef SERVER_HEADER
#  include <generated/xmlBlasterS.h>
#  include <generated/xmlBlaster.h> // client side include header
#else
#  include <generated/xmlBlaster.h> // client side include header
#endif
#define CORBA_HEADER              <omg/orb.hh> 
#define COSCONTAINEMENT           <not_implemented.h>
#define COSOBJECTIDENTITY         <not_implemented.h>
#define COSREFERENCE              <not_implemented.h>
#define COSEVENTCHANNELADMIN      <omg/CosEventChannelAdmin.hh>
#define COSEVENTCOMM              <omg/CosEventComm.hh>
#define COSRELATIONSHIPS          <not_implemented.h>
#define COSGRAPHS                 <not_implemented.h>
#define COSTIME                   <not_implemented.h>
#define COSGRAPHSEXTENSION        <not_implemented.h>
#define COSTRADING                <omg/CosTrading.hh>
#define COSNAMING                 <omg/CosNaming.hh>
#define COSPROPERTY               <not_implemented.h>
#define COSTRADINGREPOS           <omg/CosTradingRepos.hh>
#define COSTYPEDEVENTCHANNELADMIN <not_implemented.h>
#define COSTYPEDEVENT             <omg/CosTypedEventComm.hh>
#define UPDATE_THROW_SPECIFIER    IT_THROW_DECL ((CORBA::SystemException))
#define PING_THROW_SPECIFIER      IT_THROW_DECL ((CORBA::SystemException))

#else
#  error "You must #define XMLBLASTER_OMNIORB, XMLBLASTER_ORBACUS, XMLBLASTER_MICO, XMLBLASTER_TAO or XMLBLASTER_ORBIX in xmlBlaster/src/c++/client/protocol/corba/CompatibleCorba.h"
#endif

/**************************************************************
 *         GENERAL STUFF COMMON TO ALL IMPLEMENTORS 
 **************************************************************/
#include CORBA_HEADER
#undef   CORBA_HEADER

#endif // _COMPATIBLECORBA_H

std::string to_string(const CORBA::Exception &ex);


# ifdef XBL_IDL_WITH_WSTRING
   // Note: This is switched off as the mico C++ client was loosing its ConnectQos wstring during send
   //       Marcel 2006/04
   // CORBA::WChar is equivalent to wchar_t
   void string2wstring(std::wstring &dest,const std::string &src);
   void wstring2string(std::string &dest,const std::wstring &src);
   std::wstring toWstring(const std::string &src);
   std::string toString(const std::wstring &src);
   // If changing in xmlBlaster.idl 
   //    'string' to 'wstring'  (typedef wstring XmlType;)
   //    This will lead to CORBA::WString_var
   std::string corbaWStringToString(const CORBA::WString_var &src);
   CORBA::WString_var toCorbaWString(const std::string &src);
#else
   std::string corbaWStringToString(const char *src);
   const char* toCorbaWString(const std::string &src);
# endif

