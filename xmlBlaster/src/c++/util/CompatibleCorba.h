/*----------------------------------------------------------------------------
Name:      CompatibleCorba.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Helper header to encapsulate all corba-implementor specific stuff
Version:   $Id: CompatibleCorba.h,v 1.2 2000/07/06 23:42:27 laghi Exp $
Author:    <Michele Laghi> michele.laghi@attglobal.net
----------------------------------------------------------------------------*/

#ifndef _COMPATIBLECORBA_H
#define _COMPATIBLECORBA_H

#define BUILD_INCLUDE(NAME, POST) NAME ## POST

/*
 * Implementor specific macros (for includes etc.)
 */

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
#endif  // MICO

/**************************************************************
 *         GENERAL STUFF COMMON TO ALL IMPLEMENTORS 
 **************************************************************/
#include CORBA_HEADER
#undef   CORBA_HEADER

#endif // _COMPATIBLECORBA_H

#ifdef   SERVER_HEADER
#define  SERVER_INCLUDE2  <SERVER_INCLUDE(SERVER_HEADER)>
#include SERVER_INCLUDE2
#undef   SERVER_HEADER
#endif

#ifdef   CLIENT_HEADER
#define  CLIENT_INCLUDE2  <CLIENT_INCLUDE(CLIENT_HEADER)>
#include CLIENT_INCLUDE2
#undef   CLIENT_HEADER
#endif

const char* to_string(const CORBA::Exception &ex);
