/*------------------------------------------------------------------------------
Name:      Global.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Handling global data
Version:   $Id: Global.java,v 1.2 2002/03/13 16:41:11 ruff Exp $
Author:    ruff@swand.lake.de
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine;

import org.xmlBlaster.util.Timeout;
import org.xmlBlaster.engine.callback.CbWorkerPool;
import org.xmlBlaster.engine.RequestBroker;
import org.xmlBlaster.authentication.Authenticate;


/**
 * This holds global needed data of one xmlBlaster instance. 
 * <p>
 * @author <a href="mailto:ruff@swand.lake.de">Marcel Ruff</a>
 */
public final class Global extends org.xmlBlaster.util.Global
{
   private static final String ME = "Global";

   /** the authentication service */
   private Authenticate authenticate = null;
   /** the xmlBlaster core class */
   private RequestBroker requestBroker = null;


   private Timeout burstModeTimer;
   private Timeout sessionTimer;
   private Timeout messageTimer;

   private CbWorkerPool cbWorkerPool;


   /**
    * One instance of this represents one xmlBlaster server.
    */
   public Global()
   {
      super();
   }

   /**
    * One instance of this represents one xmlBlaster server.
    * @param args Environment arguments (key/value pairs)
    */
   public Global(String[] args)
   {
      super(args);
   }

   /**
    * Access the handle of the burst mode timer thread. 
    * @return The Timeout instance
    */
   public final Timeout getBurstModeTimer()
   {
      if (this.burstModeTimer == null) {
         synchronized(this) {
            if (this.burstModeTimer == null)
               this.burstModeTimer = new Timeout("BurstmodeTimer");
         }
      }
      return this.burstModeTimer;
   }

   /**
    * Access the handle of the user session timer thread. 
    * @return The Timeout instance
    */
   public final Timeout getSessionTimer()
   {
      if (this.sessionTimer == null) {
         synchronized(this) {
            if (this.sessionTimer == null)
               this.sessionTimer = new Timeout("SessionTimer");
         }
      }
      return this.sessionTimer;
   }

   /**
    * Access the handle of the message expiry timer thread. 
    * @return The Timeout instance
    */
   public final Timeout getMessageTimer()
   {
      if (this.messageTimer == null) {
         synchronized(this) {
            if (this.messageTimer == null)
               this.messageTimer = new Timeout("MessageTimer");
         }
      }
      return this.messageTimer;
   }

   /**
    * Access the handle of the callback thread pool. 
    * @return The CbWorkerPool instance
    */
   public final CbWorkerPool getCbWorkerPool()
   {
      if (this.cbWorkerPool == null) {
         synchronized(this) {
            if (this.cbWorkerPool == null)
               this.cbWorkerPool = new CbWorkerPool();
         }
      }
      return this.cbWorkerPool;
   }

   public void setAuthenticate(Authenticate auth)
   {
      this.authenticate = auth;
   }

   public Authenticate getAuthenticate()
   {
      return this.authenticate;
   }

   public void setRequestBroker(RequestBroker requestBroker)
   {
      this.requestBroker = requestBroker;
   }

   public RequestBroker getRequestBroker()
   {
      return this.requestBroker;
   }
}
