TARGET=$(MAKECMDGOALS)
ifeq ($(TARGET), )
TARGET=all
endif

include Makefile.def

$(TARGET):
	echo target is $(TARGET)
ifeq ($(USE_CPP),true) 
	$(CD) $(GEN_DIR); make $(TARGET); $(CD) ..
	$(CD) $(XMLBLASTER_HOME)/src/java/org/xmlBlaster/util/cpp; make $(TARGET); $(CD) $(XMLBLASTER_HOME)/src/c++
	$(CD) $(UTIL_DIR); make $(TARGET); $(CD) ..
	$(CD) $(CLIENT_DIR); make $(TARGET); $(CD) ..
	$(RM) $(LIB_DIR)/$(SHARED_LIB_NAME)
	$(LD) -o $(LIB_DIR)/$(SHARED_LIB_NAME).$(VERSION) $(CLIENT_OBJ)
	$(SYMLINK) $(LIB_DIR)/$(SHARED_LIB_NAME).$(VERSION) $(LIB_DIR)/$(SHARED_LIB_NAME)
	$(SLIB_ATTR) $(LIB_DIR)/$(SHARED_LIB_NAME).$(VERSION) $(LIB_DIR)/$(SHARED_LIB_NAME)
endif
