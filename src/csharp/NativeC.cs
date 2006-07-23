// Simple layer to delegate C# calls to xmlBlaster client C library (using P/Invoke).
// libxmlBlasterClientC.so (Mono/Linux) or dll (Windows) is accessed and must be available
//
// This code is functional (connect/publish/subscribe/update/disconnect) but still pre-alpha (2006-07)
//
// Currently only tested on Linux with Mono, the port to Windows is still missing
//
// Features: All features of the client C library (compression, tunnel callbacks), see
//           http://www.xmlblaster.org/xmlBlaster/doc/requirements/client.c.socket.html
//
// @todo passing command line args crashes if not ended with empty '-XXXX')
//       does hang on exit (how to debug?)
//       publishOneway crashes for arrays > 2
//       unSubscribe (how to pass QosArr back?)
//       erase (how to pass QosArr back?)
//       get (how to pass MsgUnitArr back?)
//       port content from 'string' to bye[]
//
// @author mr@marcelruff.info
//
// @prepare  cd ~/xmlBlaster; build c-lib; cd ~/xmlBlaster/src/csharp; ln -s ../../lib/libxmlBlasterClientCD.so .
// @compile  mcs -debug+ NativeC.cs
// @run      mono NativeC.exe
//           mono NativeC.exe -logLevel TRACE -XX   (without a dummy -XX it crashes?!)
//
/*
Usage:
XmlBlaster C SOCKET client @version@

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
   -sleepInterval       Milliseconds to wait on callback messages [0]

Example:
  HelloWorld3 -logLevel TRACE -dispatch/connection/plugin/socket/hostname 192.168.2.9 -sleepInterval 100000
*/
/*
Up to now i have avoided unsafe code sections:
unsafe:
 http://msdn2.microsoft.com/en-us/library/chfa2zb8.aspx
because of:
 NativeC.cs(111,65): error CS0214: Pointers and fixed size buffers may only be used in an unsafe context
If using fixed char in exception struct

http://msdn2.microsoft.com/en-us/library/s9ts558h.aspx
*/
using System;
using System.Runtime.InteropServices;


namespace org::xmlBlaster
{

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
   
   /// Calling unmanagegd code: libxmlBlasterClientC.so (Mono) or xmlBlasterClient.dll (Windows)
   public class NativeC
   {
      const string Library = "xmlBlasterClientCD"; //libxmlBlasterClientCD.so

/*      
      const int EXCEPTIONSTRUCT_ERRORCODE_LEN = 56;
      const int EXCEPTIONSTRUCT_MESSAGE_LEN = 1024;
      
      // In C# 2.0, a struct can be declared with an embedded array:
      // http://msdn2.microsoft.com/en-us/library/zycewsya.aspx
      //   unsafe {
      [StructLayout(LayoutKind.Sequential)]
      public struct XmlBlasterException {
         public int remote;
         //char *errorCode = stackalloc char[EXCEPTIONSTRUCT_ERRORCODE_LEN];
         // may only be used in unsafe context
         // Accessing this member crashes in my environment
         internal fixed char errorCode[EXCEPTIONSTRUCT_ERRORCODE_LEN];
         internal fixed char message[EXCEPTIONSTRUCT_MESSAGE_LEN];
         //Frees the pointer?:
         //public String errorCode;
         //public String message;
      }
      //   }
*/

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
      
      //[StructLayout(LayoutKind.Sequential,CharSet=CharSet.Ansi,Pack=4)] /* default is 8 byte alignement */
      [StructLayout(LayoutKind.Sequential,CharSet=CharSet.Ansi)]
      public struct MsgUnit {
         public string key;

         public int contentLen;
         //[MarshalAs(UnmanagedType.I4)] public int contentLen;
         
         public string content; // TODO: Port to byte[]
         //public byte[] content; // ** ERROR **: Structure field of type Byte[] can't be marshalled as LPArray
         //unsafe internal byte* content; // warning CS8023: Only private or internal fixed sized buffers are supported by .NET 1.x
                                   // further down: error CS1503: Argument 1: Cannot convert from `byte*' to `byte[]'
         public string qos;
         
         public string responseQos;

         public MsgUnit(string key, string content, string qos) {
            this.key = key;
            this.contentLen = content.Length;
            this.content = content;
            this.qos = qos;
            this.responseQos = null;
         }

         public override string ToString() {
            return key + "\n" + content + "\n" + qos;
         }
      }
      
      public struct MsgUnitUnmanagedArr {
         public string secretSessionId;
         public int len;
         public MsgUnit[] msgUnitArr;
      }

///////////////////
      //Dll_Export void setTest1(Test1 *test1) {
      public struct Test1 {
         [MarshalAs(UnmanagedType.ByValTStr, SizeConst=256)]
         public string secretSessionId;
      }
      [DllImport(Library)]      
      private extern static void setTest1(ref Test1 test1);
      [DllImport(Library)]      
      private extern static Test1 getTest1();
      public struct Test2 {
         public int len;
         public MsgUnit p;
      }
      [DllImport(Library)]      
      private extern static Test2 getTest2();

      // Dll_Export int getTest3(MsgUnit** pList);
      [DllImport(Library)]      
      public static extern int getTest3(out IntPtr pList);
      //public static extern int MtGetAccountList(out IntPtr pList);
      //public static extern int getTest3([MarshalAs(typeof(MsgUnit*))]out IntPtr pList);
      
      [DllImport(Library)]      
      extern static int MinArray(MsgUnit[] pData, int length);
      // ref MsgUnit[] pData does tranport unreadable to C
      // Structure field of type MsgUnit[] can't be marshalled as LPArray

      [DllImport(Library)]      
      //extern static int setMsgUnitArr(MsgUnitArr pData);
      extern static int setMsgUnitArr(MsgUnitUnmanagedArr pData);


      public void test() {
         Test1 test1 = new Test1();
         test1.secretSessionId = "Very secret";
         setTest1(ref test1); // Success
         
         Test1 res = getTest1();
         Console.WriteLine("C#: got from C: '" + res.secretSessionId + "'"); // Success, but is empty for C: Test1*getTest1();

         Test2 test2 = getTest2();
         Console.WriteLine("C#: got from C: '" + test2.len + "' " + test2.p); // But how to access the elements??

         {
         MsgUnit[] sampleData = new MsgUnit[3];
         sampleData[0] = new MsgUnit("<key oid='0'/>","Hi0","<qos/>");
         sampleData[1] = new MsgUnit("<key oid='1'/>","HiHo1","<qos/>");
         sampleData[2] = new MsgUnit("<key oid='2'/>","HiHoHa2","<qos/>");
         int result = MinArray(sampleData, sampleData.Length);
         Console.WriteLine("C# got from MinArray(): " + result);
         }

         {
         MsgUnit[] sampleData = new MsgUnit[3];
         sampleData[0] = new MsgUnit("<key oid='0'/>","Hi0","<qos/>");
         sampleData[1] = new MsgUnit("<key oid='1'/>","HiHo1","<qos/>");
         sampleData[2] = new MsgUnit("<key oid='2'/>","HiHoHa2","<qos/>");
         MsgUnitUnmanagedArr arr = new MsgUnitUnmanagedArr();
         arr.secretSessionId = "SECRET";
         arr.len = sampleData.Length;
         arr.msgUnitArr = sampleData;
         setMsgUnitArr(arr);
         }

         /*
         IntPtr pList;
         int num = getTest3(out pList);
         MsgUnit[] m_pList = new  MsgUnit[num];
         
         IntPtr names;
result = ReadList(db, out names, ref count);
MessageBox.Show(Marshal.PtrToStringAnsi(names));

then I can retrieve the first string. But I'm afraid there is an
additional pointer dereferencing going on here... advancing the
IntPtr by using

IntPtr n2 = new IntPtr(names.toInt32() + Marshal.SizeOf(typeOf(IntPtr)));
MessageBox.Show(Marshal.PtrToStringAnsi(n2)); 
*/
/*
         IntPtr current = pList;
         for (int i=0; i<num; i++) {
            m_pList[i] = new MsgUnit();
           // Copy the struct the "current" pointer points on into the C# array element
           Marshal.PtrToStructure( current, m_pList[ i ]);
           //MsgUnit ds = (MsgUnit) Marshal.PtrToStructure (m_pList[ i ] , typeof(MsgUnit));

           // In some case call one of this functions if one have to destroy the dynamically allocated
           // memory in the unmanaged DLL...
           // Marshal.FreeCoTaskMem( (IntPtr)Marshal.ReadInt32( current ));
           // Marshal.DestroyStructure( current, typeof(TMList) );

           // let the "current" pointer point to the next unmanaged C struct
           current = (IntPtr)((int)current + Marshal.SizeOf( m_pList[ i ] ));
         }
         // Eventually clean dynamically created unmanaged memory block within the DLL
         // Marshal.FreeCoTaskMem( pList ); 
         foreach (MsgUnit msgUnit in m_pList) {
            Console.WriteLine("C# got from getTest3(): " + msgUnit);         
         }
*/
      }
///////////////////

      const int MAX_SESSIONID_LEN = 256;
      
      public struct MsgUnitArr {
         public bool isOneway;
         [MarshalAs(UnmanagedType.ByValTStr, SizeConst=256)]
         public string secretSessionId;
         //internal fixed char secretSessionId[MAX_SESSIONID_LEN];
         //public string secretSessionId;
         [MarshalAs(UnmanagedType.I8)] public long len;
         public MsgUnit[] msgUnitArr;
      }

      //public delegate bool UpdateFp(ref MsgUnitArr msg, ref IntPtr userData, ref XmlBlasterException xmlBlasterException);
      delegate string UpdateFp(string cbSessionId, ref MsgUnit msgUnit, ref XmlBlasterUnmanagedException exception);

      /// Callback by xmlBlaster, see UpdateFp
      static string update(string cbSessionId, ref MsgUnit msgUnit, ref XmlBlasterUnmanagedException exception) {
         Console.WriteLine("C# update invoked START ==================");
         Console.WriteLine(msgUnit.key);
         //unsafe { Console.WriteLine(ByteArrayToString(msgUnit.content)); }
         string content = msgUnit.content.Substring(0,msgUnit.contentLen);
         Console.WriteLine(content);
         Console.WriteLine(msgUnit.qos);
         string ret = "<qos><state id='OK'/></qos>";
         Console.WriteLine("C# update invoked DONE ===================");
         return ret;
      }
      
      // C# to convert a string to a byte array.
      public static byte[] StrToByteArray(string str) {
         //byte[] data = Encoding.UTF8.GetBytes(str); // TODO
         System.Text.ASCIIEncoding  encoding=new System.Text.ASCIIEncoding();
         return encoding.GetBytes(str);
      }
      //public static byte[] StrToByteArray(string stringToConvert) {
      //   return (new UnicodeEncoding()).GetBytes(stringToConvert);
      //}

      // C# to convert a byte array to a string.
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
		
      // Declares managed prototypes for unmanaged functions.
      // http://msdn2.microsoft.com/en-us/library/e765dyyy.aspx
      //[DllImport( "..\\LIB\\PinvokeLib.dll" )]
		//[DllImport("user32.dll", CharSet = CharSet.Auto)]
      [DllImport(Library)]      
      private extern static IntPtr getXmlBlasterAccessUnparsed(int argc, string[] argv);
      
      [DllImport(Library)]      
      private extern static void freeXmlBlasterAccessUnparsed(IntPtr xa);

      [DllImport(Library)]
      private extern static string xmlBlasterUnmanagedConnect(IntPtr xa, string qos, UpdateFp update, ref XmlBlasterUnmanagedException exception);
      
      [DllImport(Library)]
      private extern static bool xmlBlasterUnmanagedInitialize(IntPtr xa, UpdateFp update, ref XmlBlasterUnmanagedException exception);

      [DllImport(Library)]
      private extern static bool xmlBlasterUnmanagedDisconnect(IntPtr xa, string qos, ref XmlBlasterUnmanagedException exception);

      [DllImport(Library)]
      private extern static string xmlBlasterUnmanagedPublish(IntPtr xa, ref MsgUnit msgUnit, ref XmlBlasterUnmanagedException exception);

      //[DllImport(Library)]
      //private extern static QosArr xmlBlasterUnmanagedPublishArr(IntPtr xa, ref MsgUnitArr msgUnitArr, ref XmlBlasterUnmanagedException exception);

      [DllImport(Library)]
      private extern static void xmlBlasterUnmanagedPublishOneway(IntPtr xa, MsgUnit[] msgUnitArr, int length, ref XmlBlasterUnmanagedException exception);
      //private extern static void xmlBlasterUnmanagedPublishOneway(IntPtr xa, ref MsgUnitArr msgUnitArr, ref XmlBlasterUnmanagedException exception);

      [DllImport(Library)]
      private extern static string xmlBlasterUnmanagedSubscribe(IntPtr xa, string key, string qos, ref XmlBlasterUnmanagedException exception);

      [DllImport(Library)]
      private extern static QosArr xmlBlasterUnmanagedUnSubscribe(IntPtr xa, string key, string qos, ref XmlBlasterUnmanagedException exception);

      //[DllImport(Library)]
      //private extern static QosArr xmlBlasterUnmanagedErase(IntPtr xa, string key, string qos, ref XmlBlasterUnmanagedException exception);

      //[DllImport(Library)]
      //private extern static MsgUnitArr xmlBlasterUnmanagedGet(IntPtr xa, string key, string qos, ref XmlBlasterUnmanagedException exception);

      [DllImport(Library)]
      private extern static string xmlBlasterUnmanagedPing(IntPtr xa, string qos, ref XmlBlasterUnmanagedException exception);
 
      [DllImport(Library)]
      private extern static bool xmlBlasterUnmanagedIsConnected(IntPtr xa);
      
      private IntPtr xa;
      private UpdateFp myUpdateFp;
      
      public NativeC(string[] argv) {
         if (argv == null) argv = new String[0];

         myUpdateFp = new UpdateFp(NativeC.update);

         // Convert command line arguments: C client lib expects the executable name as first entry
         string[] c_argv = new string[argv.Length+1];
         c_argv[0] = "NativC"; // my executable name
         for (int i=0; i<argv.Length; ++i)
            c_argv[i+1] = argv[i];
            
         xa = getXmlBlasterAccessUnparsed(c_argv.Length, c_argv);
            
         Console.WriteLine("NativeC() ...");         
      }

      ~NativeC() {
         if (xa != new IntPtr(0))
            freeXmlBlasterAccessUnparsed(xa);
         Console.WriteLine("~NativeC() ...");         
      }
      
      void check(string methodName) {
         Console.WriteLine(methodName + "() ...");         
         if (xa == new IntPtr(0))
            throw new XmlBlasterException("internal.illegalState", "Can't process " + methodName + ", xmlBlaster pointer is reset, please create a new instance of NativeC.cs class after a disconnect() call");
      }
      
      public string connect(string qos) {
         check("connect");
         try {
            XmlBlasterUnmanagedException exception = new XmlBlasterUnmanagedException();
            bool bb = xmlBlasterUnmanagedInitialize(xa, myUpdateFp, ref exception);
            if (exception.errorCode.Length > 0) {
               Console.WriteLine("xmlBlasterUnmanagedInitialize: Got exception from C: exception=" + exception.errorCode + " - " + exception.message);
               throw new XmlBlasterException(exception.remote!=0, exception.errorCode, exception.message);
            }
            else
               Console.WriteLine("xmlBlasterUnmanagedInitialize: SUCCESS '" + bb + "' xa:"/* + xa.isInitialized*/);
            
            string ret = xmlBlasterUnmanagedConnect(xa, qos, myUpdateFp, ref exception);
            if (exception.errorCode.Length > 0) {
               Console.WriteLine("xmlBlasterUnmanagedConnect: Got exception from C: '" + ret + "' exception=" + exception.errorCode + " - " + exception.message);
               throw new XmlBlasterException(exception.remote!=0, exception.errorCode, exception.message);
            }
            else
               Console.WriteLine("xmlBlasterUnmanagedConnect: SUCCESS '" + ret + "'");
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
               Console.WriteLine("xmlBlasterUnmanagedDisconnect: Got exception from C: exception=" + exception.errorCode + " - " + exception.message);
               throw new XmlBlasterException(exception.remote!=0, exception.errorCode, exception.message);
            }
            else
               Console.WriteLine("xmlBlasterUnmanagedDisconnect: SUCCESS '" + bb + "'");

            freeXmlBlasterAccessUnparsed(xa);
            xa = new IntPtr(0);
            Console.WriteLine("xmlBlasterUnmanagedDisconnect: SUCCESS freed all resources");

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
            msgUnit.content =  content;
            //unsafe { msgUnit.content =  StrToByteArray(content); }
            msgUnit.contentLen = content.Length;
            msgUnit.qos = qos;
            string ret = xmlBlasterUnmanagedPublish(xa, ref msgUnit, ref exception);
            if (exception.errorCode.Length > 0) {
               Console.WriteLine("xmlBlasterUnmanagedPublish: Got exception from C: exception=" + exception.errorCode + " - " + exception.message);
               throw new XmlBlasterException(exception.remote!=0, exception.errorCode, exception.message);
            }
            else
               Console.WriteLine("xmlBlasterUnmanagedPublish: SUCCESS '" + ret + "'");
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
      public void publishOneway(MsgUnit[] msgUnitArr) {
         check("publishOneway");
         try {
            XmlBlasterUnmanagedException exception = new XmlBlasterUnmanagedException();
            xmlBlasterUnmanagedPublishOneway(xa, msgUnitArr, msgUnitArr.Length, ref exception);
            if (exception.errorCode.Length > 0) {
               Console.WriteLine("publishOneway: Got exception from C: exception=" + exception.errorCode + " - " + exception.message);
               throw new XmlBlasterException(exception.remote!=0, exception.errorCode, exception.message);
            }
            else
               Console.WriteLine("publishOneway: SUCCESS");
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
               Console.WriteLine("xmlBlasterUnmanagedSubscribe: Got exception from C: exception=" + exception.errorCode + " - " + exception.message);
               throw new XmlBlasterException(exception.remote!=0, exception.errorCode, exception.message);
            }
            else
               Console.WriteLine("xmlBlasterUnmanagedSubscribe: SUCCESS '" + ret + "'");
            return ret;
         }
         catch (XmlBlasterException e) {
            throw e;
         }
         catch (Exception e) {
            throw new XmlBlasterException("internal.unknown", "subscribe failed", e);
         }
      }

      // fails: ** ERROR **: Structure field of type String[] can't be marshalled as LPArray
      public string[] unSubscribe(string key, string qos) {
         check("unSubscribe");
         try {
            XmlBlasterUnmanagedException exception = new XmlBlasterUnmanagedException();
            /*TODO: QosArr ret = */xmlBlasterUnmanagedUnSubscribe(xa, key, qos, ref exception);
            if (exception.errorCode.Length > 0) {
               Console.WriteLine("xmlBlasterUnmanagedUnSubscribe: Got exception from C: exception=" + exception.errorCode + " - " + exception.message);
               throw new XmlBlasterException(exception.remote!=0, exception.errorCode, exception.message);
            }
            else
               Console.WriteLine("xmlBlasterUnmanagedUnSubscribe: SUCCESS '"/* + ret.qosArr[0] */+"'");
            return new String[0]; /*ret.qosArr;*/
         }
         catch (XmlBlasterException e) {
            throw e;
         }
         catch (Exception e) {
            //try {
            //   disconnect("<qos/>");
            //}
            //catch (Exception e2) {
            //   Console.WriteLine("xmlBlasterUnmanagedUnSubscribe: Ignoring " + e2.ToString() + " root was " +e.ToString());
            //}
            throw new XmlBlasterException("internal.unknown", "unSubscribe failed", e);
         }
      }

      public string ping(string qos) {
         check("ping");
         try {
            XmlBlasterUnmanagedException exception = new XmlBlasterUnmanagedException();
            string ret = xmlBlasterUnmanagedPing(xa, qos, ref exception);
            if (exception.errorCode.Length > 0) {
               Console.WriteLine("xmlBlasterUnmanagedPing: Got exception from C: exception=" + exception.errorCode + " - " + exception.message);
               throw new XmlBlasterException(exception.remote!=0, exception.errorCode, exception.message);
            }
            else
               Console.WriteLine("xmlBlasterUnmanagedPing: SUCCESS '" + ret + "'");
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
         Console.WriteLine("isConnected() ...");         
         if (xa == new IntPtr(0)) return false;
         try {
            bool bb = xmlBlasterUnmanagedIsConnected(xa);
            Console.WriteLine("xmlBlasterUnmanagedIsConnected: SUCCESS '" + bb + "'");
            return bb;
         }
         catch (XmlBlasterException e) {
            throw e;
         }
         catch (Exception e) {
            throw new XmlBlasterException("internal.unknown", "isConnected failed", e);
         }
      }

      static void Main(string[] argv) {
         /*
         if (false) {
            NativeC nc = new NativeC(argv);
            nc.test();
         }
         */
         if (true) {
            NativeC nc = new NativeC(argv);
            
            // crashed with [3], why??
            MsgUnit[] msgUnitArr = new MsgUnit[2];
            msgUnitArr[0] = new MsgUnit("<key oid='0'/>","Hi0","<qos/>");
            msgUnitArr[1] = new MsgUnit("<key oid='1'/>","HiHoHa1","<qos/>");
            //msgUnitArr[2] = new MsgUnit("<key oid='2'/>","HiHoHa2","<qos/>");

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
                  "</qos>", callbackSessionId);  /*"    socket://{1}:{2}"+*/
            Console.WriteLine(connectQos);         
            
            nc.connect(connectQos);
            nc.publishOneway(msgUnitArr);
            nc.subscribe("<key oid='Hello'/>", "<qos/>");
            nc.publish("<key oid='C#C#C#'/>", "HIII", "<qos/>");
            nc.ping("<qos/>");
            nc.isConnected();

            //nc.unSubscribe("<key oid='Hello'/>", "<qos/>"); // ** ERROR **: Structure field of type String[] can't be marshalled as LPArray
            
            Console.WriteLine("Hit a key");         
            Console.ReadLine();
            nc.disconnect("<qos/>");
            nc.isConnected();
         }
         Console.Out.WriteLine("DONE");
      }
   }
}

/*
using System;
 using System.Runtime.InteropServices;
 public class LibWrap
 {
 // C# doesn't support varargs so all arguments must be explicitly defined.
 // CallingConvention.Cdecl must be used since the stack is 
 // cleaned up by the caller.
 // int printf(stringformat [, argument]...)
 [DllImport("msvcrt.dll", CharSet=CharSet.Ansi, CallingConvention=CallingConvention.Cdecl)]
 public static extern int printf(String format, int i, double d); 
 [DllImport("msvcrt.dll", CharSet=CharSet.Ansi, CallingConvention=CallingConvention.Cdecl)]
 public static extern int printf(String format, int i, String s); 
 }
 public class App
 {
     public static void Main()
     {
         LibWrap.printf("\nPrint params: %i %f", 99, 99.99);
         LibWrap.printf("\nPrint params: %i %s", 99, "abcd");
     }
 }
*/

/*
 typedef struct
// {
//  char szBLZ[15];
//  char szKNr[15];
//  char szType[15];
//  char  szCurrency[15];
//  char cSH;
//  long  lSaldo;
//  char szCustomer[30];

> } TMList;

[StructLayout(LayoutKind.Explicit, Size=95, CharSet=CharSet.Ansi)]
  public class TMList
  {
   [MarshalAs(UnmanagedType.ByValTStr, SizeConst=15)]
   [FieldOffset(0)]public String szBLZ;

   [MarshalAs(UnmanagedType.ByValTStr, SizeConst=15)]
   [FieldOffset(15)]public String szKNr;

   [MarshalAs(UnmanagedType.ByValTStr, SizeConst=15)]
   [FieldOffset(30)]public String szType;

   [MarshalAs(UnmanagedType.ByValTStr, SizeConst=15)]
   [FieldOffset(45)]public String szCurrency;

   [FieldOffset(60)]public char cSH;
   [FieldOffset(61)]public int lSaldo;

   [MarshalAs(UnmanagedType.ByValTStr, SizeConst=30)]
   [FieldOffset(65)]public String szCustomer;

  }; 
  */