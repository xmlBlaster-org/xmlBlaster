/*----------------------------------------------------------------------------
Name:      CompatibleCorba.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Helper header to encapsulate all corba-implementor specific stuff
Author:    <Michele Laghi> laghi@swissinfo.org
----------------------------------------------------------------------------*/

#ifndef _COMPATIBLECORBA_H
#define _COMPATIBLECORBA_H

#define BUILD_INCLUDE(NAME, POST) NAME ## POST

/*
 * Implementor specific macros (for includes etc.)
 */

/******************************************************************
 *                      OMNIORB (Nils.Nilson@in-gmbh.de)
 ******************************************************************/
#ifdef  OMNIORB
#define CLIENT_INCLUDE(NAME)      BUILD_INCLUDE(NAME, .h)
#define SERVER_INCLUDE(NAME)      BUILD_INCLUDE(NAME, .h)
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
#define COSNAMING                 <omniORB4/naming.hh>
//#define COSNAMING                 <omniORB3/naming.hh>
#define COSPROPERTY               <not_implemented.h>
#define COSTRADINGREPOS           <not_implemented.h>
#define COSTYPEDEVENTCHANNELADMIN <not_implemented.h>
#define COSTYPEDEVENT             <not_implemented.h>
#define UPDATE_THROW_SPECIFIER    
#define PING_THROW_SPECIFIER      
#endif  // OMNIORB

/******************************************************************
 *                      ORBACUS (OB-4.03)
 ******************************************************************/
#ifdef  ORBACUS
#define CLIENT_INCLUDE(NAME)      BUILD_INCLUDE(NAME, .h)
#define SERVER_INCLUDE(NAME)      BUILD_INCLUDE(NAME, _skel.h)
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
#endif  // ORBACUS

/*****************************************************************
 *                     MICO (ver. 2.3.1)
 *****************************************************************/
#ifdef  MICO
#define CLIENT_INCLUDE(NAME)      BUILD_INCLUDE(NAME, .h)
#define SERVER_INCLUDE(NAME)      BUILD_INCLUDE(NAME, .h)
#define CORBA_HEADER              <CORBA.h>
#define COSCONTAINEMENT           <mico/CosContainment.h>
#define COSOBJECTIDENTITY         <mico/CosObjectIdentity.h>
#define COSREFERENCE              <mico/CosReference.h>
#define COSEVENTCHANNELADMIN      <mico/CosEventChannelAdmin.h>
#define COSEVENTCOMM              <mico/CosEventComm.h>
#define COSRELATIONSHIPS          <mico/CosRelationships.h>
#define COSGRAPHS                 <mico/CosGraphs.h>
#define COSTIME                   <mico/CosTime.h>
#define COSGRAPHSEXTENSION        <mico/CosGraphsExtension.h>
#define COSTRADING                <mico/CosTrading.h>
#define COSNAMING                 <mico/CosNaming.h>
#define COSPROPERTY               <not_implemented.h>
#define COSTRADINGREPOS           <mico/CosTradingRepos.h>
#define COSTYPEDEVENTCHANNELADMIN <not_implemented.h>
#define COSTYPEDEVENT             <not_implemented.h>
#define UPDATE_THROW_SPECIFIER    
#define PING_THROW_SPECIFIER      
#endif  // MICO

/*****************************************************************
 *                     TAO (ver. 2.3.1)
 *****************************************************************/
#ifdef  TAO
#define CLIENT_INCLUDE(NAME)      <BUILD_INCLUDE(NAME, C.h)>
#define SERVER_INCLUDE(NAME)      <BUILD_INCLUDE(NAME, S.hh)>
#define CORBA_HEADER              <tao/corba.h>
#define COSCONTAINEMENT           <not_implemented.h> // what is this ??
#define COSOBJECTIDENTITY         <not_implemented.h>
#define COSREFERENCE              <not_implemented.h>
#define COSEVENTCHANNELADMIN      <orbsvcs/CosEventChannelAdminC.h>
#define COSEVENTCOMM              <not_implemented.h>
#define COSRELATIONSHIPS          <not_implemented.h>
#define COSGRAPHS                 <not_implemented.h>
#define COSTIME                   <orbsvcs/CosTimeC.h>
#define COSGRAPHSEXTENSION        <not_implemented>
#define COSTRADING                <mico/CosTradingC.h>
#define COSNAMING                 <orbsvcs/CosNamingC.h>
#define COSPROPERTY               <orbsvcs/CosPropertyServiceC.h>
#define COSTRADINGREPOS           <mico/CosTradingReposC.h>
#define COSTYPEDEVENTCHANNELADMIN <not_implemented.h>
#define COSTYPEDEVENT             <not_implemented.h>
#define UPDATE_THROW_SPECIFIER    ACE_THROW_SPEC (( CORBA::SystemException, serverIdl::XmlBlasterException ))
#define PING_THROW_SPECIFIER      ACE_THROW_SPEC (( CORBA::SystemException ))
#endif  // TAO


/*****************************************************************
 *                     ORBIX 2000 (ver. 2.0 )
 *****************************************************************/
#ifdef ORBIX
#define CLIENT_INCLUDE(NAME)      <BUILD_INCLUDE(NAME, .h)>
#define SERVER_INCLUDE(NAME)      <BUILD_INCLUDE(NAME, S.h)>
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
#endif //ORBIX

/**************************************************************
 *         GENERAL STUFF COMMON TO ALL IMPLEMENTORS 
 **************************************************************/
#include CORBA_HEADER
#undef   CORBA_HEADER

#endif // _COMPATIBLECORBA_H

/*#ifdef   SERVER_HEADER
#define  SERVER_INCLUDE2  <SERVER_INCLUDE(SERVER_HEADER)>
#include SERVER_INCLUDE2
#undef   SERVER_HEADER
#endif
*/
 // #include <generated/xmlBlaster.h>
/*#ifdef   CLIENT_HEADER
#define  CLIENT_INCLUDE2  <CLIENT_INCLUDE(CLIENT_HEADER)>
#include CLIENT_INCLUDE2
#undef   CLIENT_HEADER
#endif
*/
const char* to_string(const CORBA::Exception &ex);
