TARGET=$(MAKECMDGOALS)
ifeq ($(TARGET), )
TARGET=all
endif

include Makefile.def

$(TARGET):
	echo target is $(TARGET)
ifeq ($(USE_CPP),true) 
	$(CD) $(GEN_DIR); make $(TARGET); $(CD) ..
#	$(CD) $(JUTIL_DIR); make $(TARGET); $(CD) ..
	$(CD) $(XMLBLASTER_HOME)/src/java/org/jutils/cpp; make $(TARGET); $(CD) $(XMLBLASTER_HOME)/src/c++
	$(CD) $(UTIL_DIR); make $(TARGET); $(CD) ..
	$(CD) $(CLIENT_DIR); make $(TARGET); $(CD) ..
#	$(CD) $(TEST_DIR); make $(TARGET); $(CD) ..
endif
