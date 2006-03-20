set USE_EMBEDDED="-embeddedServer true"

echo "Please make sure there i no xmlBlaster server running when runnigng these tests"
echo "since the tests have an own embedded server"

echo "going to test the TestStringTrim ..."
bin\TestStringTrim
echo "going to test the ConnectQos ..."
bin\TestConnectQos
echo "going to test the CorbaDriver ..."
bin\TestCorbaDriver
echo "going to test timeout ..."
bin\TestTimeout
echo "going to test the timestamp"
bin\TestTimestamp
bin\TestThread

# these are the methods which use an xmlBlaster server (either embedded or external)

echo "going to test the ram consumption"
bin\TestRam %USE_EMBEDDED%
echo "going to test the 'get' method"
bin\TestGet %USE_EMBEDDED%
echo "going to test the 'subscribe' method"
bin\TestSub %USE_EMBEDDED%
echo "going to test the 'subscribe to XPath feature"
bin\TestSubXPath %USE_EMBEDDED%
echo "going to the test the 'failsafe' feature"
bin\TestFailsafe %USE_EMBEDDED%
echo "tests ended here. Goodbye !"

