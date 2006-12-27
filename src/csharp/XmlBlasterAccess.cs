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

namespace org.xmlBlaster.client
{
   public class XmlBlasterAccessFactory
   {
      public static I_XmlBlasterAccess CreateInstance()
      {
         /*
         // Choose the PInvoke plugin:
#        if FORCE_PINVOKECE_PLUGIN

            return new PInvokeCE(); // Runs fine with WIN32 and WINCE (fails with MONO)

#        elif XMLBLASTER_MONO || FORCE_NATIVEC_PLUGIN
            
            return new NativeC(); // First try, runs fine with WIN32 and with Linux/MONO

#        else // WIN32 || WINCE || Smartphone || PocketPC || WindowsCE || FORCE_PINVOKECE
            
            return new PInvokeCE(); // Runs fine with WIN32 and WINCE (fails with MONO)

#        endif*/
         return CreateInstance("org.xmlBlaster.client.PInvokeCE");
      }

      /// <summary>
      /// Create a client library instance to access xmlBlaster. 
      /// </summary>
      /// <param name="typeName">For example "org.xmlBlaster.client.PInvokeCE", this
      /// plugin supports Windowx CE CF2, Windows .net 2 and Linux mono 1.2</param>
      /// <returns></returns>
      public static I_XmlBlasterAccess CreateInstance(string typeName)
      {
         System.Reflection.Assembly SourceAssembly = System.Reflection.Assembly.GetExecutingAssembly();
         return (I_XmlBlasterAccess)SourceAssembly.CreateInstance(typeName);
      }
   }

   /// <summary>
   /// Log levels copied from xmlBlaster client C library
   /// See helper.h enum XMLBLASTER_LOG_LEVEL_ENUM
   /// </summary>
   public enum LogLevel
   {
      /*NOLOG=0,  don't use */
      ERROR = 1,  // supported, use for programming errors
      WARN = 2,   // supported, use for user errors and wrong configurations
      INFO = 3,   // supported, use for success information only
      /*CALL=4,  don't use */
      /*TIME=5,  don't use */
      TRACE = 6,  // supported, use for debugging purposes
      DUMP = 7    // supported, use for debugging purposes
      /*PLAIN=8  don't use */
   }

   public interface I_Callback
   {
      string OnUpdate(string cbSessionId, MsgUnitUpdate msgUnit);
   }

   public interface I_LoggingCallback
   {
      void OnLogging(LogLevel logLevel, string location, string message);
   }

   /// <summary>
   /// Access xmlBlaster, for details see
   /// http://www.xmlblaster.org/xmlBlaster/doc/requirements/interface.html
   /// All methods can throw an XmlBlasterException
   /// </summary>
   public interface I_XmlBlasterAccess
   {
      /// <summary>
      /// Convenience to pass command line arguments
      /// </summary>
      /// <param name="argv">e.g. "-logLevel" "INFO"</param>
      void Initialize(string[] argv);

      /// <summary>
      /// Initialize the client library
      /// </summary>
      /// <param name="properties">e.g. "logLevel" "INFO"</param>
      void Initialize(Hashtable properties);

      ConnectReturnQos Connect(string qos, I_Callback listener);

      /// After calling diconnect() this class is not usable anymore
      /// you need to create a new instance to connect again
      bool Disconnect(string qos);

      /// <summary>
      /// Publish a message to xmlBlaster
      /// </summary>
      /// <param name="msgUnit">The message to send</param>
      /// <returns>The publish return qos</returns>
      PublishReturnQos Publish(MsgUnit msgUnit);

      /// <summary>
      /// Convenience method to publish a message 
      /// </summary>
      /// <param name="key">The topic description</param>
      /// <param name="content">Your data to send</param>
      /// <param name="qos">The required quality of service</param>
      /// <returns>The publish return qos</returns>
      PublishReturnQos Publish(string key, string content, string qos);

      void PublishOneway(MsgUnit[] msgUnitArr);

      SubscribeReturnQos Subscribe(string key, string qos);

      UnSubscribeReturnQos[] UnSubscribe(string key, string qos);

      EraseReturnQos[] Erase(string key, string qos);

      MsgUnitGet[] Get(string key, string qos);

      string Ping(string qos);

      /// <summary>
      /// Check if we have a connection to the xmlBlaster server
      /// </summary>
      /// <returns>true if we are connected</returns>
      bool IsConnected();

      string GetVersion();

      string GetUsage();

      /// <summary>
      /// Register the given listener to receive all logging output
      /// of the C library and C# wrapper code.
      /// </summary>
      /// <param name="listener">The logging is redirected to this listener</param>
      void AddLoggingListener(I_LoggingCallback listener);

      /// <summary>
      /// Remove the given listener
      /// </summary>
      /// <param name="listener">Redirection is stopped for this listener</param>
      void RemoveLoggingListener(I_LoggingCallback listener);

      /// <summary>
      /// Returns the telephone EMEI id if available. 
      /// The International Mobile Equipment Identity (IMEI) is a 15 digit number
      /// and unique over time and space for your PDA.
      /// </summary>
      /// <returns>null if not found</returns>
      string GetEmeiId();

      /// <summary>
      /// Returns the device unique id, to be used in favour of getEmeiId().
      /// Supported since Windows CE 5.0.1, please check
      /// the correct behaviour on your device as it may not
      /// be implemented properly by your vendor.
      /// </summary>
      /// <returns>return null if not found</returns>
      string GetDeviceUniqueId();
   }

   public class XmlBlasterException : ApplicationException
   {
      private bool remote;
      public bool Remote
      {
         get { return remote; }
      }
      private string errorCode;
      public string ErrorCode
      {
         get { return errorCode; }
      }
      //string message;
      public XmlBlasterException(string errorCode, string message)
         : base(message)
      {
         this.remote = false;
         this.errorCode = errorCode;
      }
      public XmlBlasterException(string errorCode, string message, Exception inner)
         : base(message, inner)
      {
         this.remote = false;
         this.errorCode = errorCode;
      }
      public XmlBlasterException(bool remote, string errorCode, string message)
         : base(message)
      {
         this.remote = remote;
         this.errorCode = errorCode;
      }
      public override string ToString()
      {
         string ret = "errorCode=" + this.errorCode;
         if (remote)
            ret += " thrownFromRemote";
         if (Message != null)
            ret += " message=" + Message;
         Exception inner = InnerException;
         if (inner != null)
            ret += inner.ToString();
         return ret;
      }
   }

   public class MsgUnitGet : MsgUnit
   {
      private GetQos getQos;
      private GetKey getKey;
      public MsgUnitGet(string key, byte[] content, string qos)
         : base(key, content, qos)
      {
         this.getKey = new GetKey(key);
         this.getQos = new GetQos(qos);
      }

      public GetKey GetGetKey()
      {
         return this.getKey;
      }
      public GetQos GetGetQos()
      {
         return this.getQos;
      }
   }


   public class MsgUnitUpdate : MsgUnit
   {
      private UpdateQos updateQos;
      private UpdateKey updateKey;
      public MsgUnitUpdate(string key, byte[] content, string qos)
         : base(key, content, qos)
      {
         this.updateKey = new UpdateKey(key);
         this.updateQos = new UpdateQos(qos);
      }

      public UpdateKey GetUpdateKey()
      {
         return this.updateKey;
      }
      public UpdateQos GetUpdateQos()
      {
         return this.updateQos;
      }
   }

   /// <summary>
   /// Holds a message unit
   /// </summary>
   public class MsgUnit
   {
      protected string key;
      protected byte[] content;
      protected string qos;
      protected bool oneway;
      public MsgUnit() { }
      public MsgUnit(string key, byte[] content, string qos)
      {
         this.key = key;
         this.content = (content == null) ? new byte[0] : content;
         this.qos = qos;
      }
      public MsgUnit(string key, string contentStr, string qos)
      {
         this.key = key;
         SetContentStr(contentStr);
         this.qos = qos;
      }
      public string GetKeyStr()
      {
         return this.key;
      }
      public string GetQosStr()
      {
         return this.qos;
      }
      public byte[] GetContent()
      {
         return this.content;
      }
      /// We return a string in the UTF-8 codeset
      public string GetContentStr()
      {
         System.Text.UTF8Encoding enc = new System.Text.UTF8Encoding();
         return enc.GetString(this.content, 0, this.content.Length);
      }
      /// The binary string is UTF-8 encoded
      public void SetContentStr(string contentStr)
      {
         System.Text.UTF8Encoding enc = new System.Text.UTF8Encoding();
         this.content = enc.GetBytes(contentStr);
      }
      public bool IsOneway()
      {
         return this.oneway;
      }
      public override string ToString()
      {
         return key + "\n" + GetContentStr() + "\n" + qos;
      }
   }
}
