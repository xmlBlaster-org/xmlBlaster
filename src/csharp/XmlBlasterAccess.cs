/*----------------------------------------------------------------------------
Name:      XmlBlasterAccess.cs
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Provides abstraction to xmlBlaster access from C#
Author:    "Marcel Ruff" <xmlBlaster@marcelruff.info>
Date:      07/2006
See:       http://www.xmlblaster.org/xmlBlaster/doc/requirements/interface.html
-----------------------------------------------------------------------------*/
using System;
using System.Text;
using System.Collections;
using System.Runtime.InteropServices;
using org.xmlBlaster.util;

namespace org.xmlBlaster.client
{
   /// Calling unmanagegd code: xmlBlasterClientC.dll
   public class XmlBlasterAccess : I_XmlBlasterAccess, I_ConnectionStateListener, I_ProgressCallback, I_LoggingCallback
   {
      private I_XmlBlasterAccess delegateXb;

      private Hashtable properties;

      private long pingIntervalMillis = 10000;

      private long pollIntervalMillis = 5000;

      private I_ConnectionStateListener connectionStateListener;

      private XbPinger xbPinger;

      private XbPoller xbPoller;

      private I_LoggingCallback loggingCallback;

      private I_ProgressCallback progressCallback;

      private I_Callback callback;

      private volatile string connectQosStr;

      private volatile ConnectReturnQos connectReturnQos;

      private volatile bool connectCalled = false;

      private volatile bool initializeCalled = false;

      private volatile bool disconnectCalled = false;

      private volatile bool isDead = false;

      private volatile bool pollOnInitialConnectFail = false;

      private volatile ConnectionStateEnum currentState = ConnectionStateEnum.UNDEF;

      public XmlBlasterAccess()
      {
         this.delegateXb = CreateNative();
      }

      private I_XmlBlasterAccess CreateNative()
      {
         I_XmlBlasterAccess native = XmlBlasterAccessFactory.CreateInstance("org.xmlBlaster.client.PInvokeCE");
         native.RegisterConnectionListener(this);
         native.AddLoggingListener(this);
         native.AddCallbackProgressListener(this);
         return native;
      }

      public void Initialize(string[] argv)
      {
         this.delegateXb.Initialize(argv);
      }

      public void Initialize(Hashtable properties)
      {
         this.initializeCalled = true;
         if (properties == null) properties = new Hashtable();
         this.properties = properties;
         this.pingIntervalMillis = Stuff.Get(properties, "dispatch/connection/pingInterval", this.pingIntervalMillis);
         this.pollIntervalMillis = Stuff.Get(properties, "dispatch/connection/delay", this.pollIntervalMillis);
         this.pollOnInitialConnectFail = Stuff.Get(properties, "dispatch/connection/pollOnInitialConnectFail", this.pollOnInitialConnectFail);
         int retries = (int)Stuff.Get(properties, "dispatch/connection/retries", -1);
         this.xbPinger = new XbPinger(this, this.pingIntervalMillis, this);
         this.xbPoller = new XbPoller(this, this.pollIntervalMillis, this);
         this.delegateXb.Initialize(this.properties);
      }

      public void RegisterConnectionListener(I_ConnectionStateListener connectionStateListener)
      {
         this.connectionStateListener = connectionStateListener;
         this.delegateXb.RegisterConnectionListener(this);
      }

      public void CheckValid()
      {
         if (!this.initializeCalled)
            throw new XmlBlasterException("user.illegalargument", "Please initialize XmlBlasterAccess first");
         if (this.disconnectCalled)
            throw new XmlBlasterException("user.illegalargument", "XmlBlasterAccess is shutdown");
         if (this.isDead)
            throw new XmlBlasterException("user.illegalargument", "XmlBlasterAccess is connection is DEAD");
      }

      public ConnectReturnQos Connect(string qos, I_Callback listener)
      {
         try
         {
            this.CheckValid();
            this.connectQosStr = qos;
            this.callback = listener;
            this.connectCalled = true; // before connect()
            this.disconnectCalled = false;
            this.connectReturnQos = this.delegateXb.Connect(qos, listener);
            this.xbPoller.Stop();
            this.xbPinger.Start();
            return this.connectReturnQos;
         }
         catch (Exception e)
         {
            if (this.pollOnInitialConnectFail)
            {
               StartPolling();
               return null;
            }
            else
            {
               throw e;
            }
         }
      }

      public bool Disconnect(string qos)
      {
         this.CheckValid();
         this.disconnectCalled = true;
         bool ret = this.delegateXb.Disconnect(qos);
         Shutdown();
         return ret;
      }

      public void LeaveServer()
      {
         this.CheckValid();
         this.disconnectCalled = true;
         this.delegateXb.LeaveServer();
         Shutdown();
      }

      private void Shutdown()
      {
         this.isDead = true;
         ConnectionStateEnum oldState = this.currentState;
         StopPinging();
         StopPolling();
         this.currentState = ConnectionStateEnum.DEAD;
         callStateListener(oldState, ConnectionStateEnum.DEAD);
      }

      public PublishReturnQos Publish(MsgUnit msgUnit)
      {
         this.CheckValid();
         return this.delegateXb.Publish(msgUnit);
      }

      public PublishReturnQos Publish(string key, string content, string qos)
      {
         this.CheckValid();
         return this.delegateXb.Publish(key, content, qos);
      }

      public void PublishOneway(MsgUnit[] msgUnitArr)
      {
         this.CheckValid();
         this.delegateXb.PublishOneway(msgUnitArr);
      }

      public SubscribeReturnQos Subscribe(string key, string qos)
      {
         this.CheckValid();
         return this.delegateXb.Subscribe(key, qos);
      }

      public UnSubscribeReturnQos[] UnSubscribe(string key, string qos)
      {
         this.CheckValid();
         return this.delegateXb.UnSubscribe(key, qos);
      }

      public EraseReturnQos[] Erase(string key, string qos)
      {
         this.CheckValid();
         return this.delegateXb.Erase(key, qos);
      }

      public MsgUnitGet[] Get(string key, string qos)
      {
         this.CheckValid();
         return this.delegateXb.Get(key, qos);
      }

      public string Ping(string qos)
      {
         this.CheckValid();
         return this.delegateXb.Ping(qos);
      }

      public bool IsConnected()
      {
         try {
            this.CheckValid();
         }
         catch (Exception) {
            return false;
         }
         return this.delegateXb.IsConnected();
      }

      public string GetVersion()
      {
         return this.delegateXb.GetVersion();
      }

      public string GetUsage()
      {
         return this.delegateXb.GetUsage();
      }

      public void OnLogging(XmlBlasterLogLevel logLevel, string location, string message)
      {
         I_LoggingCallback l = this.loggingCallback;
         if (l != null)
            l.OnLogging(logLevel, location, message);
         else
            Console.WriteLine(logLevel + " " + location + " " + message);
      }

      public void AddLoggingListener(I_LoggingCallback listener)
      {
         this.loggingCallback = listener;
         this.delegateXb.AddLoggingListener(this);
      }

      public void RemoveLoggingListener(I_LoggingCallback listener)
      {
         this.loggingCallback = null; // listener;
         this.delegateXb.RemoveLoggingListener(listener);
      }

      public void AddCallbackProgressListener(I_ProgressCallback listener)
      {
         this.progressCallback = listener;
         this.delegateXb.AddCallbackProgressListener(this);
      }

      public void RemoveCallbackProgressListener(I_ProgressCallback listener)
      {
         this.progressCallback = null; // listener;
         this.delegateXb.RemoveCallbackProgressListener(listener);
      }

      public string GetEmeiId()
      {
         return this.delegateXb.GetEmeiId();
      }

      public string GetDeviceUniqueId()
      {
         return this.delegateXb.GetDeviceUniqueId();
      }

      public void OnData(bool read, int currBytesRead, int nbytes)
      {
         I_ProgressCallback l = this.progressCallback;
         if (l != null)
         {
            l.OnData(read, currBytesRead, nbytes);
         }
      }

      public void reachedAlive(ConnectionStateEnum oldStateBeneath, I_XmlBlasterAccess connection)
      {
         ConnectionStateEnum oldState = this.currentState;
         this.currentState = ConnectionStateEnum.ALIVE;
         I_ConnectionStateListener l = this.connectionStateListener;
         if (l != null)
         {
            try
            {
               l.reachedAlive(oldState, connection);
            }
            catch (Exception e)
            {
               OnLogging(XmlBlasterLogLevel.WARN, "reachedAlive", "User code failed: " + e.ToString());
            }
         }
         StopPolling();
         StartPinging();
      }

      public void reachedPolling(ConnectionStateEnum oldStateBeneath, I_XmlBlasterAccess connection)
      {
         ConnectionStateEnum oldState = this.currentState;
         this.currentState = ConnectionStateEnum.POLLING;
         I_ConnectionStateListener l = this.connectionStateListener;
         if (l != null)
         {
            try
            {
               l.reachedPolling(oldState, connection);
            }
            catch (Exception e)
            {
               OnLogging(XmlBlasterLogLevel.WARN, "reachedPolling", "User code failed: " + e.ToString());
            }
         }
         StopPinging();
         StartPolling();
      }

      public void reachedDead(ConnectionStateEnum oldStateBeneath, I_XmlBlasterAccess connection)
      {
         ConnectionStateEnum oldState = this.currentState;
         if (this.xbPoller.IsConfiguredToWork()) // We know better than the layer below
         {
            this.currentState = ConnectionStateEnum.POLLING;
            callStateListener(oldState, this.currentState);
            StopPinging();
            StartPolling();
            return;
         }
         this.currentState = ConnectionStateEnum.DEAD;
         callStateListener(oldState, this.currentState);
         StopPinging();
         StopPolling();
      }

      private bool StartPolling()
      {
         this.CheckValid();
         //if (this.disconnectCalled) return false;
         if (this.pollIntervalMillis < 1L) return false;
         bool started = this.xbPoller.Start();
         if (started)
         {
            ConnectionStateEnum oldState = this.currentState;
            this.currentState = ConnectionStateEnum.POLLING;
            callStateListener(oldState, this.currentState);
         }
         return started;
      }

      private void StopPolling()
      {
         this.xbPoller.Stop();
      }


      private bool StartPinging()
      {
         this.CheckValid();
         //if (this.connectReturnQos == null) return false;
         //if (this.disconnectCalled) return false;
         if (this.pingIntervalMillis < 1L) return false;
         bool started = this.xbPinger.Start();
         if (started)
         {
            ConnectionStateEnum oldState = this.currentState;
            this.currentState = ConnectionStateEnum.ALIVE;
            callStateListener(oldState, this.currentState);
         }
         return started;
      }

      private void callStateListener(ConnectionStateEnum oldState, ConnectionStateEnum newState)
      {
         if (oldState == newState)
         {
            OnLogging(XmlBlasterLogLevel.WARN, "XmlBlasterAccess", "Same states in transition: " + newState);
            return;
         }
         
         I_ConnectionStateListener l = this.connectionStateListener;
         if (l != null)
         {
            try
            {
               if (newState == ConnectionStateEnum.ALIVE)
                  l.reachedAlive(oldState, this);
               else if (newState == ConnectionStateEnum.POLLING)
                  l.reachedPolling(oldState, this);
               else if (newState == ConnectionStateEnum.DEAD)
                  l.reachedDead(oldState, this);
            }
            catch (Exception e)
            {
               OnLogging(XmlBlasterLogLevel.WARN, ""+newState, "User code failed: " + e.ToString());
            }
         }
      }

      private bool StopPinging()
      {
         return this.xbPinger.Stop();
      }

      public void OnReconnectTry()
      {
         if (!this.connectCalled || this.disconnectCalled)
         {
            StopPolling();
            return;
         }

         I_XmlBlasterAccess old = this.delegateXb;
         if (old != null) {
            this.delegateXb = null;
            old.LeaveServer();
         }

         this.delegateXb = CreateNative();

         ConnectReturnQos qr = Connect(this.connectQosStr, this.callback);
         if (qr == null)
            throw new XmlBlasterException("communication.noConnection", "we are polling for the server");

         StopPolling();
         StartPinging();
         //OnLogging(XmlBlasterLogLevel.WARN, "XmlBlasterAccess", "No connection to server: " + e.ToString());
      }

      public void OnPingFailed(Exception ex)
      {
         if (this.disconnectCalled)
         {
            StopPinging();
            return;
         }
         OnSocketDisconnected(ex);
      }

      // socket EOF
      public void OnSocketDisconnected(Exception ex)
      {
         StopPinging();
         StartPolling();
      }
   }
}
