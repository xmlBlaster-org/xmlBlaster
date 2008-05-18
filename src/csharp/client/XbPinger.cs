/*----------------------------------------------------------------------------
Name:      XbPinger.cs
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Provides abstraction to xmlBlaster access from C#
Author:    "Marcel Ruff" <xmlBlaster@marcelruff.info>
Date:      05/2008
See:       http://www.xmlblaster.org/xmlBlaster/doc/requirements/interface.html
-----------------------------------------------------------------------------*/
using System;
using System.Text;
using System.Threading;
using System.Collections;
using System.Collections.Generic;

//using org.xmlBlaster.util;

namespace org.xmlBlaster.client
{
   public class XbPinger
   {

      private readonly string ME = "XbPinger";
      private I_LoggingCallback logger;
      private volatile bool running;
      private XmlBlasterAccess xbAccess;
      private int sleepMillis;
      private Thread thread;
      private object locker = new object();
      public static readonly int MIN_PING_MILLIS = 5000;

      public XbPinger(XmlBlasterAccess xbAccess, long sleepMillis, I_LoggingCallback listener)
      {
         this.xbAccess = xbAccess;
         this.sleepMillis = (int)sleepMillis;
         if (this.sleepMillis < MIN_PING_MILLIS) this.sleepMillis = MIN_PING_MILLIS;
         this.logger = listener;
         this.running = false;
      }

      public bool IsConfiguredToWork()
      {
         return this.sleepMillis > 0;
      }

      public bool IsStarted()
      {
         return this.running;
      }

      public bool Start()
      {
         if (this.sleepMillis < 1)
            return false;
         lock (locker)
         {
            if (this.running) return false;
            this.running = true;
            this.thread = new Thread(this.Run);
            this.thread.Start();
         }
         logger.OnLogging(XmlBlasterLogLevel.INFO, ME, "Start pingInterval=" + sleepMillis);
         return true;
      }

      public bool Stop()
      {
         lock (locker)
         {
            if (!this.running) return false;
            this.running = false;
         }
         logger.OnLogging(XmlBlasterLogLevel.INFO, ME, "Stop");
         return true;
      }

      private void Run()
      {
         try
         {
            //logger.OnLogging(XmlBlasterLogLevel.TRACE, ME, "working...");
            while (this.running)
            {
               try
               {
                  logger.OnLogging(XmlBlasterLogLevel.TRACE, ME, "Ping ...");
                  string pong = this.xbAccess.Ping("");
                  logger.OnLogging(XmlBlasterLogLevel.TRACE, ME, "Ping sucess: " + pong);
               }
               catch (Exception e)
               {
                  logger.OnLogging(XmlBlasterLogLevel.WARN, ME, "Ping: " + e.ToString());
                  Stop();
                  this.xbAccess.OnPingFailed(e);
               }

               if (!this.running) break;

               Thread.Sleep(sleepMillis);
            }
            logger.OnLogging(XmlBlasterLogLevel.INFO, ME, "terminating gracefully.");
         }
         catch (Exception e2)
         {
            logger.OnLogging(XmlBlasterLogLevel.INFO, ME, "terminating abort: " + e2.ToString());
         }
      }

      public long GetSleepMillis()
      {
         return this.sleepMillis;
      }

      public void Shutdown()
      {
         Stop();
         if (this.thread != null)
            this.thread.Abort();
      }
   }
}
