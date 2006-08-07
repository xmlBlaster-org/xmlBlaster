// NativeC.cs
// Simple layer to delegate C# calls to xmlBlaster client C library (using P/Invoke).
// libxmlBlasterClientC.so (Mono/Linux) or dll (Windows) is accessed and must be available
//
// This code is functional but still pre-alpha (2006-07)
//
// Currently only tested on Linux with Mono, the port to Windows is still missing
//
// Features: All features of the client C library (compression, tunnel callbacks), see
//           http://www.xmlblaster.org/xmlBlaster/doc/requirements/client.c.socket.html
//
// IMPORTANT: Please edit/change the lookup path below for xmlBlasterClientC.dll
//            and set the PATH to access it
//
// @todo     port content from 'string' to byte[]
//           publishOneway crashes
//           OnUpdate() throwing exception seems not to be passed to C
//           logging with log4net
//           port to Windows
//           write a testsuite
//           write a requirement
//           create an assembly with ant or nant
//           create the same wrapper for the xmlBlaster C++ library
//
// @author   mr@marcelruff.info
//
// @prepare Linux: cd ~/xmlBlaster; build c-lib; cd ~/xmlBlaster/src/csharp; ln -s ../../lib/libxmlBlasterClientCD.so .
// @compile Linux: mcs /d:NATIVE_C_MAIN /d:XMLBLASTER_CLIENT_MONO -debug+ -out:NativeC.exe NativeC.cs
//
// @prepare Windows: Compile the C client library first (see xmlBlaster\src\c\xmlBlasterClientC.sln)
// @compile Windows: csc /d:NATIVE_C_MAIN -debug+ -out:NativeC.exe NativeC.cs   (Windows)
//
// @run      mono NativeC.exe
//           mono NativeC.exe --help
//           mono NativeC.exe -logLevel TRACE
// @see      http://www.xmlblaster.org/xmlBlaster/doc/requirements/client.csharp.html
// @c        http://www.xmlBlaster/org
//
/*
Usage:
XmlBlaster C SOCKET client

   -dispatch/connection/plugin/socket/hostname [localhost]
                       Where to find xmlBlaster.
   -dispatch/connection/plugin/socket/port [7607]
                       The port where xmlBlaster listens.
   -dispatch/connection/plugin/socket/localHostname [NULL]
                       Force the local IP, useful on multi homed computers.
   -dispatch/connection/plugin/socket/localPort [0]
                       Force the local port, useful to tunnel firewalls.
   -dispatch/connection/plugin/socket/compress/type []
                       Switch on compression with 'zlib:stream'.
   -dispatch/connection/plugin/socket/useUdpForOneway [false]
                       Use UDP for publishOneway() calls.
   -dispatch/callback/plugin/socket/hostname [localhost]
                       The IP where to establish the callback server.
                       Can be useful on multi homed hosts.
   -dispatch/callback/plugin/socket/port [7611]
                       The port of the callback server.
   -plugin/socket/multiThreaded  [true]
                       If true the update() call to your client code is a separate thread.
   -plugin/socket/responseTimeout  [60000 (one minute)]
                       The time in millis to wait on a response, 0 is forever.
   -logLevel            ERROR | WARN | INFO | TRACE | DUMP [WARN]

Example:
  mono NativeC -logLevel TRACE -dispatch/connection/plugin/socket/hostname 192.168.2.9
*/
using System;
using System.Runtime.InteropServices;

namespace org.xmlBlaster
{
   public class XmlBlasterAccessFactory
   {
      public static I_XmlBlasterAccess createInstance(String[] argv) {
         return new NativeC(argv);
      }
   }
   
   public interface I_Callback
   {
      string OnUpdate(string cbSessionId, MsgUnit msgUnit);
   }

   public interface I_XmlBlasterAccess
   {
      string connect(string qos, I_Callback listener);
      /// After calling diconnect() this class is not usable anymore
      /// you need to create a new instance to connect again
      bool disconnect(string qos);
      string publish(string key, string content, string qos);
      void publishOneway(MsgUnit[] msgUnitArr);
      string subscribe(string key, string qos);
      string[] unSubscribe(string key, string qos);
      string[] erase(string key, string qos);
      MsgUnit[] get(string key, string qos);
      string ping(string qos);
      bool isConnected();
   }

   public class XmlBlasterException : Exception {
      bool remote;
      public bool Remote {
         get { return remote; }
      }
      string errorCode;
      public string ErrorCode {
         get { return errorCode; }
      }
      //string message;
      public XmlBlasterException(string errorCode, string message) : base(message) {
         this.remote = false;
         this.errorCode = errorCode;
      }
      public XmlBlasterException(string errorCode, string message, Exception inner) : base(message,inner) {
         this.remote = false;
         this.errorCode = errorCode;
      }
      public XmlBlasterException(bool remote, string errorCode, string message) : base(message) {
         this.remote = remote;
         this.errorCode = errorCode;
      }
      public override string ToString() {
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
   
   // SEE http://msdn2.microsoft.com/en-us/library/2k1k68kw.aspx
   // Declares a class member for each structure element.
   // Must match exactly the C struct MsgUnit (sequence!)
   [ StructLayout( LayoutKind.Sequential, CharSet=CharSet.Ansi )]
   public class MsgUnit 
   {
      public string key;
      public int contentLen;
      // Without MarshalAs: ** ERROR **: Structure field of type Byte[] can't be marshalled as LPArray
      //[MarshalAs (UnmanagedType.ByValArray, SizeConst=100)] // does not work unlimited without SizeConst -> SIGSEGV
      // public byte[] content; // Ensure UTF8 encoding for strings
      public string content;
      public string qos;
      public string responseQos;
      public MsgUnit() { }
      public MsgUnit(string key, string contentStr, string qos) {
         this.key = key;
         this.contentLen = contentStr.Length;
         setContentStr(contentStr);
         this.qos = qos;
      }
      /// We return a string in the default codeset
      public string getContentStr() {
         //System.Text.ASCIIEncoding enc = new System.Text.ASCIIEncoding();
         //return enc.GetString(this.content);
         // How does this work? System.Text.Decoder d = System.Text.Encoding.UTF8.GetDecoder();
         return this.content;
      }
      /// The binary string is UTF8 encoded (xmlBlaster default)
      public void setContentStr(string contentStr) {
         //this.content = System.Text.Encoding.UTF8.GetBytes(contentStr);
         this.content = contentStr;
      }
      public override string ToString() {
         return key + "\n" + content + "\n" + qos;
      }
   }

   /// Calling unmanagegd code: libxmlBlasterClientC.so (Mono) or xmlBlasterClientC.dll (Windows)
   public class NativeC : I_XmlBlasterAccess
   {
      bool verbose = false; // TODO: log4net

#     if XMLBLASTER_CLIENT_MONO // Linux Debug, set LD_LIBRARY_PATH to find the shared library
         const string XMLBLASTER_C_LIBRARY = "xmlBlasterClientCD"; //libxmlBlasterClientCD.so
#     else // Windows
         // http://msdn2.microsoft.com/en-us/library/e765dyyy.aspx
         //[DllImport("user32.dll", CharSet = CharSet.Auto)]
         const string XMLBLASTER_C_LIBRARY = "..\\..\\lib\\xmlBlasterClientC.dll";
#     endif

      // Helper struct for DLL calls to avoid 'fixed' and unsafe
      struct XmlBlasterUnmanagedException {
         public int remote;
         public string errorCode;
         public string message;
      }
      
      public struct QosArr {
         public int len;  /* Number of XML QoS strings */
         public string[] qosArr;
      }

      public struct MsgUnitUnmanagedArr {
         public string secretSessionId;
         public int len;
         public MsgUnit[] msgUnitArr;
      }



      [ StructLayout( LayoutKind.Sequential, CharSet=CharSet.Ansi )]
      public class StringArr {
         public string str;
      }

      public void logger(String str) {
         if (verbose) Console.WriteLine(str);
      }

      delegate string UpdateUnmanagedFp(string cbSessionId, MsgUnit msgUnit, ref XmlBlasterUnmanagedException exception);

      /// Callback by xmlBlaster, see UpdateUnmanagedFp
      string updateUnmanaged(string cbSessionId, MsgUnit msgUnit, ref XmlBlasterUnmanagedException exception) {
         if (null != onUpdate) {
            try {
               return onUpdate(cbSessionId, msgUnit);
            }
            // TODO: Exception seems not to reach the C code
            catch (XmlBlasterException e) {
               logger("OnUpdate() exception: " + e.ToString());
               exception.errorCode = e.ErrorCode;
               exception.message = e.Message;
               exception.remote = 1;
               return null;
            }
            catch (Exception e) {
               logger("OnUpdate() exception: " + e.ToString());
               exception.errorCode = "user.update.internalError";
               exception.message = e.ToString();
               exception.remote = 1;
               return null;
            }
         }
         logger("C# updateUnmanaged invoked START ==================");
         logger(msgUnit.key);
         logger(msgUnit.getContentStr());
         logger(msgUnit.qos);
         string ret = "<qos><state id='OK'/></qos>";
         logger("C# updateUnmanaged invoked DONE ===================");
         return ret;
      }
      
      // Convert a string to a byte array.
      public static byte[] StrToByteArray(string str) {
         //return (new UnicodeEncoding()).GetBytes(stringToConvert);
         //byte[] data = System.Text.Encoding.UTF8.GetBytes(str); // TODO
         System.Text.ASCIIEncoding  encoding=new System.Text.ASCIIEncoding();
         return encoding.GetBytes(str);
      }

      // Convert a byte array to a string.
      public static string ByteArrayToString(byte[] dBytes) {
         string str;
         System.Text.ASCIIEncoding enc = new System.Text.ASCIIEncoding();
         str = enc.GetString(dBytes);
         return str;
      }
      
      /*
         [StructLayout(LayoutKind.Explicit)]
         public struct Rect {
             [FieldOffset(0)] public int left;
             [FieldOffset(4)] public int top;
             [FieldOffset(8)] public int right;
             [FieldOffset(12)] public int bottom;
         }
      */
      
      [DllImport(XMLBLASTER_C_LIBRARY )]      
      private extern static IntPtr getXmlBlasterAccessUnparsedUnmanaged(int argc, string[] argv);
      
      [DllImport(XMLBLASTER_C_LIBRARY )]      
      private extern static void freeXmlBlasterAccessUnparsedUnmanaged(IntPtr xa);

      [DllImport(XMLBLASTER_C_LIBRARY )]
      private extern static string xmlBlasterUnmanagedConnect(IntPtr xa, string qos, UpdateUnmanagedFp updateUnmanaged, ref XmlBlasterUnmanagedException exception);
      
      [DllImport(XMLBLASTER_C_LIBRARY )]
      private extern static bool xmlBlasterUnmanagedInitialize(IntPtr xa, UpdateUnmanagedFp updateUnmanaged, ref XmlBlasterUnmanagedException exception);

      [DllImport(XMLBLASTER_C_LIBRARY )]
      private extern static bool xmlBlasterUnmanagedDisconnect(IntPtr xa, string qos, ref XmlBlasterUnmanagedException exception);

      [DllImport(XMLBLASTER_C_LIBRARY )]
      private extern static string xmlBlasterUnmanagedPublish(IntPtr xa, MsgUnit msgUnit, ref XmlBlasterUnmanagedException exception);

      //[DllImport(XMLBLASTER_C_LIBRARY )]
      //private extern static QosArr xmlBlasterUnmanagedPublishArr(IntPtr xa, MsgUnitArr msgUnitArr, ref XmlBlasterUnmanagedException exception);

      [DllImport(XMLBLASTER_C_LIBRARY )]
      private extern static void xmlBlasterUnmanagedPublishOneway(IntPtr xa, MsgUnit[] msgUnitArr, int length, ref XmlBlasterUnmanagedException exception);

      [DllImport(XMLBLASTER_C_LIBRARY )]
      private extern static string xmlBlasterUnmanagedSubscribe(IntPtr xa, string key, string qos, ref XmlBlasterUnmanagedException exception);

      [DllImport(XMLBLASTER_C_LIBRARY )]
      private extern static void xmlBlasterUnmanagedUnSubscribe(IntPtr xa, string key, string qos, ref XmlBlasterUnmanagedException exception, out int size, out IntPtr ptr);

      [DllImport(XMLBLASTER_C_LIBRARY )]
      private extern static void xmlBlasterUnmanagedErase(IntPtr xa, string key, string qos, ref XmlBlasterUnmanagedException exception, out int size, out IntPtr ptr);

      [DllImport(XMLBLASTER_C_LIBRARY )]
      private extern static void xmlBlasterUnmanagedGet(IntPtr xa, string key, string qos, ref XmlBlasterUnmanagedException exception, out int size, out IntPtr ptr);

      [DllImport(XMLBLASTER_C_LIBRARY )]
      private extern static string xmlBlasterUnmanagedPing(IntPtr xa, string qos, ref XmlBlasterUnmanagedException exception);
 
      [DllImport(XMLBLASTER_C_LIBRARY )]
      private extern static bool xmlBlasterUnmanagedIsConnected(IntPtr xa);
      
      [DllImport(XMLBLASTER_C_LIBRARY )]
      private extern static string xmlBlasterUnmanagedUsage();

      
      private IntPtr xa;
      private UpdateUnmanagedFp updateUnmanagedFp;
      
      public NativeC(string[] argv) {
         if (argv == null) argv = new String[0];

         updateUnmanagedFp = new UpdateUnmanagedFp(this.updateUnmanaged);

         // Convert command line arguments: C client lib expects the executable name as first entry
         string[] c_argv = new string[argv.Length+1];
         c_argv[0] = "NativC"; // my executable name
         for (int i=0; i<argv.Length; ++i) {
            if (argv[i] == "--help") {
               Console.WriteLine("Usage:\n" + xmlBlasterUnmanagedUsage());
               throw new XmlBlasterException("user.usage", "Good bye");
            }
            c_argv[i+1] = argv[i];
         }
            
         xa = getXmlBlasterAccessUnparsedUnmanaged(c_argv.Length, c_argv);
            
         logger("NativeC() ...");         
      }

      ~NativeC() {
         if (xa != new IntPtr(0))
            freeXmlBlasterAccessUnparsedUnmanaged(xa);
         logger("~NativeC() ...");         
      }
      
      void check(string methodName) {
         logger(methodName + "() ...");         
         if (xa == new IntPtr(0))
            throw new XmlBlasterException("internal.illegalState", "Can't process " + methodName + ", xmlBlaster pointer is reset, please create a new instance of NativeC.cs class after a disconnect() call");
      }

      private delegate string OnUpdate(string cbSessionId, MsgUnit msgUnit);
      private event OnUpdate onUpdate;
      
      public string connect(string qos, I_Callback listener) {
         check("connect");
         if (listener != null) {
            onUpdate += new OnUpdate(listener.OnUpdate);
         }
         try {
            XmlBlasterUnmanagedException exception = new XmlBlasterUnmanagedException();
            bool bb = xmlBlasterUnmanagedInitialize(xa, updateUnmanagedFp, ref exception);
            if (exception.errorCode.Length > 0) {
               logger("xmlBlasterUnmanagedInitialize: Got exception from C: exception=" + exception.errorCode + " - " + exception.message);
               throw new XmlBlasterException(exception.remote!=0, exception.errorCode, exception.message);
            }
            else
               logger("xmlBlasterUnmanagedInitialize: SUCCESS '" + bb + "' xa:"/* + xa.isInitialized*/);
            
            string ret = xmlBlasterUnmanagedConnect(xa, qos, updateUnmanagedFp, ref exception);
            if (exception.errorCode.Length > 0) {
               logger("xmlBlasterUnmanagedConnect: Got exception from C: '" + ret + "' exception=" + exception.errorCode + " - " + exception.message);
               throw new XmlBlasterException(exception.remote!=0, exception.errorCode, exception.message);
            }
            else
               logger("xmlBlasterUnmanagedConnect: SUCCESS '" + ret + "'");
            return ret;
         }
         catch (XmlBlasterException e) {
            throw e;
         }
         catch (Exception e) {
            throw new XmlBlasterException("internal.unknown", "connect failed", e);
         }
      }

      /// After calling diconnect() this class is not usable anymore
      /// you need to create a new instance to connect again
      public bool disconnect(string qos) {
         check("disconnect");
         try {
            XmlBlasterUnmanagedException exception = new XmlBlasterUnmanagedException();
            bool bb = xmlBlasterUnmanagedDisconnect(xa, qos, ref exception);
            if (exception.errorCode.Length > 0) {
               logger("xmlBlasterUnmanagedDisconnect: Got exception from C: exception=" + exception.errorCode + " - " + exception.message);
               throw new XmlBlasterException(exception.remote!=0, exception.errorCode, exception.message);
            }
            else
               logger("xmlBlasterUnmanagedDisconnect: SUCCESS '" + bb + "'");

            freeXmlBlasterAccessUnparsedUnmanaged(xa);
            xa = new IntPtr(0);
            logger("xmlBlasterUnmanagedDisconnect: SUCCESS freed all resources");

            return bb;
         }
         catch (XmlBlasterException e) {
            throw e;
         }
         catch (Exception e) {
            throw new XmlBlasterException("internal.unknown", "disconnect failed", e);
         }
      }

      public string publish(string key, string content, string qos) {
         check("publish");
         try {
            XmlBlasterUnmanagedException exception = new XmlBlasterUnmanagedException();
            MsgUnit msgUnit = new MsgUnit();
            msgUnit.key = key;
            msgUnit.setContentStr(content);
            //unsafe { msgUnit.content =  StrToByteArray(content); }
            msgUnit.contentLen = content.Length;
            msgUnit.qos = qos;
            string ret = xmlBlasterUnmanagedPublish(xa, msgUnit, ref exception);
            if (exception.errorCode.Length > 0) {
               logger("xmlBlasterUnmanagedPublish: Got exception from C: exception=" + exception.errorCode + " - " + exception.message);
               throw new XmlBlasterException(exception.remote!=0, exception.errorCode, exception.message);
            }
            else
               logger("xmlBlasterUnmanagedPublish: SUCCESS '" + ret + "'");
            return ret;
         }
         catch (XmlBlasterException e) {
            throw e;
         }
         catch (Exception e) {
            throw new XmlBlasterException("internal.unknown", "publish failed", e);
         }
      }

      // TODO: Crashs for array size > 2
      /*
       Invalid read of size 1
         at 0x63FFF14: encodeMsgUnitArr (xmlBlasterSocket.c:222)
            by 0x63FD11E: xmlBlasterPublishOneway (XmlBlasterConnectionUnparsed.c:1004)
            by 0x63F9125: xmlBlasterPublishOneway (XmlBlasterAccessUnparsed.c:726)
            by 0x63FF0B8: xmlBlasterUnmanagedPublishOneway (XmlBlasterUnmanaged.c:365)
            by 0x6524CC8: ???
      */
      public void publishOneway(MsgUnit[] msgUnitArr) {
         check("publishOneway");
         try {
            XmlBlasterUnmanagedException exception = new XmlBlasterUnmanagedException();
            xmlBlasterUnmanagedPublishOneway(xa, msgUnitArr, msgUnitArr.Length, ref exception);
            if (exception.errorCode.Length > 0) {
               logger("publishOneway: Got exception from C: exception=" + exception.errorCode + " - " + exception.message);
               throw new XmlBlasterException(exception.remote!=0, exception.errorCode, exception.message);
            }
            else
               logger("publishOneway: SUCCESS");
         }
         catch (XmlBlasterException e) {
            throw e;
         }
         catch (Exception e) {
            throw new XmlBlasterException("internal.unknown", "publishOneway failed", e);
         }
      }
      
      public string subscribe(string key, string qos) {
         check("subscribe");
         try {
            XmlBlasterUnmanagedException exception = new XmlBlasterUnmanagedException();
            string ret = xmlBlasterUnmanagedSubscribe(xa, key, qos, ref exception);
            if (exception.errorCode.Length > 0) {
               logger("xmlBlasterUnmanagedSubscribe: Got exception from C: exception=" + exception.errorCode + " - " + exception.message);
               throw new XmlBlasterException(exception.remote!=0, exception.errorCode, exception.message);
            }
            else
               logger("xmlBlasterUnmanagedSubscribe: SUCCESS '" + ret + "'");
            return ret;
         }
         catch (XmlBlasterException e) {
            throw e;
         }
         catch (Exception e) {
            throw new XmlBlasterException("internal.unknown", "subscribe failed", e);
         }
      }

      public string[] unSubscribe(string key, string qos) {
         check("unSubscribe");
         try {
            XmlBlasterUnmanagedException exception = new XmlBlasterUnmanagedException();
            int size;
            IntPtr outArray;
            xmlBlasterUnmanagedUnSubscribe(xa, key, qos, ref exception, out size, out outArray);
            if (exception.errorCode.Length > 0) {
               logger("xmlBlasterUnmanagedUnSubscribe: Got exception from C: exception=" + exception.errorCode + " - " + exception.message);
               throw new XmlBlasterException(exception.remote!=0, exception.errorCode, exception.message);
            }
            StringArr[] manArray = new StringArr[ size ];
            String[] retQosArr = new String[size];
            IntPtr current = outArray;
            for( int i = 0; i < size; i++ ) {
               manArray[ i ] = new StringArr();
               Marshal.PtrToStructure( current, manArray[ i ]);
               Marshal.DestroyStructure( current, typeof(StringArr) );
               current = (IntPtr)((long)current + Marshal.SizeOf( manArray[ i ] ));
               //Console.WriteLine( "Element {0}: str={1}", i, manArray[ i ].str );
               retQosArr[i] = manArray[ i ].str;
            }
            Marshal.FreeCoTaskMem( outArray );
            logger("xmlBlasterUnmanagedUnSubscribe: SUCCESS");
            return retQosArr;
         }
         catch (XmlBlasterException e) {
            throw e;
         }
         catch (Exception e) {
            //try {
            //   disconnect("<qos/>");
            //}
            //catch (Exception e2) {
            //   logger("xmlBlasterUnmanagedUnSubscribe: Ignoring " + e2.ToString() + " root was " +e.ToString());
            //}
            throw new XmlBlasterException("internal.unknown", "unSubscribe failed", e);
         }
      }

      public string[] erase(string key, string qos) {
         check("erase");
         try {
            XmlBlasterUnmanagedException exception = new XmlBlasterUnmanagedException();
            int size;
            IntPtr outArray;
            xmlBlasterUnmanagedErase(xa, key, qos, ref exception, out size, out outArray);
            if (exception.errorCode.Length > 0) {
               logger("xmlBlasterUnmanagedErase: Got exception from C: exception=" + exception.errorCode + " - " + exception.message);
               throw new XmlBlasterException(exception.remote!=0, exception.errorCode, exception.message);
            }
            StringArr[] manArray = new StringArr[ size ];
            String[] retQosArr = new String[size];
            IntPtr current = outArray;
            for( int i = 0; i < size; i++ ) {
               manArray[ i ] = new StringArr();
               Marshal.PtrToStructure( current, manArray[ i ]);
               Marshal.DestroyStructure( current, typeof(StringArr) );
               current = (IntPtr)((long)current + Marshal.SizeOf( manArray[ i ] ));
               //Console.WriteLine( "Element {0}: str={1}", i, manArray[ i ].str );
               retQosArr[i] = manArray[ i ].str;
            }
            Marshal.FreeCoTaskMem( outArray );
            logger("xmlBlasterUnmanagedErase: SUCCESS");
            return retQosArr;
         }
         catch (XmlBlasterException e) {
            throw e;
         }
         catch (Exception e) {
            throw new XmlBlasterException("internal.unknown", "unSubscribe failed", e);
         }
      }

      public MsgUnit[] get(string key, string qos) {
         check("get");
         try {
            XmlBlasterUnmanagedException exception = new XmlBlasterUnmanagedException();
            int size;
            IntPtr outArray;
            xmlBlasterUnmanagedGet(xa, key, qos, ref exception, out size, out outArray);
            if (exception.errorCode.Length > 0) {
               logger("xmlBlasterUnmanagedGet: Got exception from C: exception=" + exception.errorCode + " - " + exception.message);
               throw new XmlBlasterException(exception.remote!=0, exception.errorCode, exception.message);
            }
            
            logger("get() size=" + size);
            MsgUnit[] manArray = new MsgUnit[ size ];
            IntPtr current = outArray;
            for( int i = 0; i < size; i++ ) {
               manArray[ i ] = new MsgUnit();
               Marshal.PtrToStructure( current, manArray[ i ]);
               //Marshal.FreeCoTaskMem( (IntPtr)Marshal.ReadInt32( current ));
               Marshal.DestroyStructure( current, typeof(MsgUnit) );
               current = (IntPtr)((long)current + 
               Marshal.SizeOf( manArray[ i ] ));
               //Console.WriteLine( "Element {0}: key={1} qos={2} buffer={3} contentLength={4}", i, 
               //   manArray[ i ].key, manArray[ i ].qos, manArray[ i ].content, manArray[ i ].contentLen );
            }
            Marshal.FreeCoTaskMem( outArray );
            logger("xmlBlasterUnmanagedGet: SUCCESS");
            return manArray;
         }
         catch (XmlBlasterException e) {
            throw e;
         }
         catch (Exception e) {
            throw new XmlBlasterException("internal.unknown", "get failed", e);
         }
      }

      public string ping(string qos) {
         check("ping");
         try {
            XmlBlasterUnmanagedException exception = new XmlBlasterUnmanagedException();
            string ret = xmlBlasterUnmanagedPing(xa, qos, ref exception);
            if (exception.errorCode.Length > 0) {
               logger("xmlBlasterUnmanagedPing: Got exception from C: exception=" + exception.errorCode + " - " + exception.message);
               throw new XmlBlasterException(exception.remote!=0, exception.errorCode, exception.message);
            }
            else
               logger("xmlBlasterUnmanagedPing: SUCCESS '" + ret + "'");
            return ret;
         }
         catch (XmlBlasterException e) {
            throw e;
         }
         catch (Exception e) {
            throw new XmlBlasterException("internal.unknown", "ping failed", e);
         }
      }
 
      public bool isConnected() {
         logger("isConnected() ...");         
         if (xa == new IntPtr(0)) return false;
         try {
            bool bb = xmlBlasterUnmanagedIsConnected(xa);
            logger("xmlBlasterUnmanagedIsConnected: SUCCESS '" + bb + "'");
            return bb;
         }
         catch (XmlBlasterException e) {
            throw e;
         }
         catch (Exception e) {
            throw new XmlBlasterException("internal.unknown", "isConnected failed", e);
         }
      }

      public static string usage() {
         return xmlBlasterUnmanagedUsage();
      }

#if NATIVE_C_MAIN
      static void Main(string[] argv) {
        
         I_XmlBlasterAccess nc = XmlBlasterAccessFactory.createInstance(argv);

         // crashed with [3], why??
         MsgUnit[] msgUnitArr = new MsgUnit[3];
         msgUnitArr[0] = new MsgUnit("<key oid='0'/>","Hi0","<qos/>");
         msgUnitArr[1] = new MsgUnit("<key oid='1'/>","HiHoHa1","<qos/>");
         msgUnitArr[2] = new MsgUnit("<key oid='2'/>","HiHoHa2","<qos/>");

         const string callbackSessionId = "secretCb";
         string connectQos = String.Format(
               "<qos>"+
               " <securityService type='htpasswd' version='1.0'>"+
               "  <![CDATA["+
               "   <user>fritz</user>"+
               "   <passwd>secret</passwd>"+
               "  ]]>"+
               " </securityService>"+
               " <queue relating='callback' maxEntries='50000' maxEntriesCache='10000'>"+
               "   <callback type='SOCKET' sessionId='{0}'>"+
               "   </callback>"+
               " </queue>"+
               "</qos>", callbackSessionId);  //"    socket://{1}:{2}"+
         Console.WriteLine(connectQos);         

         I_Callback callback = null;
         nc.connect(connectQos, callback);
         for (int i=0; i<50; i++) {
            //nc.publishOneway(msgUnitArr);
            string srq = nc.subscribe("<key oid='Hello'/>", "<qos/>");
            Console.WriteLine("subscribe() returned " + srq);         
            //nc.publish("<key oid='Hello'/>", "HIII", "<qos/>");
            string prq = nc.publish("<key oid='C#C#C#'/>", "HIIIHAAAA", "<qos/>");
            Console.WriteLine("publish() returned " + prq);         
            nc.publish("<key oid='C#C#C#'/>", "HIIIHOOO", "<qos/>");
            Console.WriteLine("publish() returned " + prq);         
            MsgUnit[] msgs = nc.get("<key oid='C#C#C#'/>", "<qos><history numEntries='6'/></qos>");
            Console.WriteLine("get() returned " + msgs.Length + " messages");         
            string p = nc.ping("<qos/>");
            Console.WriteLine("ping() returned " + p);         
            bool b = nc.isConnected();
            Console.WriteLine("isConnected() returned " + b);         
            string[] urq = nc.unSubscribe("<key oid='Hello'/>", "<qos/>");
            Console.WriteLine("unSubscribe() returned " + urq[0]);         
            string[] erq = nc.erase("<key oid='C#C#C#'/>", "<qos/>");
            Console.WriteLine("erase() returned " + erq[0]);         
            Console.WriteLine("\nHit a key " + i);         
            Console.ReadLine();
         }
         nc.disconnect("<qos/>");
         nc.isConnected();
         Console.Out.WriteLine("DONE");
      }
#endif
   }
}
