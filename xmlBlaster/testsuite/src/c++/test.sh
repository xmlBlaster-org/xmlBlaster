export DELAY_TIME=4
echo "Please make sure there i no xmlBlaster server running when runnigng these tests"
echo "since the tests have an own embedded server"

echo "going to test the ConnectQos ..."
sleep $DELAY_TIME
bin/TestConnectQos
echo "going to test the CorbaDriver ..."
sleep $DELAY_TIME
bin/TestCorbaDriver
echo "going to test timeout ..."
sleep $DELAY_TIME
bin/TestTimeout
echo "going to test the timestamp"
sleep $DELAY_TIME
bin/TestTimestamp
echo "going to test the ram consumption"
sleep $DELAY_TIME
bin/TestRam
echo "going to test the 'get' method"
sleep $DELAY_TIME
bin/TestGet
echo "going to test the 'subscribe' method"
sleep $DELAY_TIME
bin/TestSub
echo "going to test the 'subscribe to XPath feature"
sleep $DELAY_TIME
bin/TestSubXPath
echo "going to the test the 'failsafe' feature"
sleep $DELAY_TIME
bin/TestFailsafe
echo "tests ended here. Goodbye !"
rm failsafe.dump


