CPP_DIR=$(XMLBLASTER_HOME)/src/c++

include $(CPP_DIR)/Makefile.def

EVERYTHING= $(BIN_DIR)/TestGet \
	 $(BIN_DIR)/TestSub \
	 $(BIN_DIR)/RamTest \
	 $(BIN_DIR)/TestLogin \
	 $(BIN_DIR)/clientPOA

#    $(BIN_DIR)/clientBOA 

ifeq ($(CORBA_CPP),mico)
ALL=$(EVERYTHING) $(BIN_DIR)/clientPOA	
else
ALL=$(EVERYTHING)
endif


.cpp.o:
	$(CC) $(CORBA_INC) $(XMLCPP_INC) -I$(CPP_DIR) -c $<

all: bin $(ALL)

test: all
	./test.sh

bin:
	$(MKDIR) bin

$(BIN_DIR)/TestGet: $(LIB_DIR)/$(SHARED_LIB_NAME) TestGet.o
ifeq ($(USE_CPP),true) 
	$(CL) TestGet.o -o$(BIN_DIR)/TestGet $(XMLCPP_LIB) $(CORBA_LIB) $(COS_LIB) -L$(LIB_DIR) -l$(SHARED_LIB) -lstdc++
endif

$(BIN_DIR)/TestSub: $(LIB_DIR)/$(SHARED_LIB_NAME) TestSub.o
ifeq ($(USE_CPP),true) 
	$(CL) TestSub.o -o$(BIN_DIR)/TestSub $(XMLCPP_LIB) $(CORBA_LIB) $(COS_LIB) -L$(LIB_DIR) -l$(SHARED_LIB) -lstdc++
endif

$(BIN_DIR)/RamTest: $(LIB_DIR)/$(SHARED_LIB_NAME) RamTest.o
ifeq ($(USE_CPP),true) 
	$(CL) RamTest.o -o$(BIN_DIR)/RamTest $(XMLCPP_LIB) $(CORBA_LIB) $(COS_LIB) -L$(LIB_DIR) -l$(SHARED_LIB) -lstdc++
endif

$(BIN_DIR)/TestLogin: $(LIB_DIR)/$(SHARED_LIB_NAME) TestLogin.o
ifeq ($(USE_CPP),true) 
	$(CL) TestLogin.o -o$(BIN_DIR)/TestLogin $(XMLCPP_LIB) $(CORBA_LIB) $(COS_LIB) -L$(LIB_DIR) -l$(SHARED_LIB) -lstdc++
endif

$(BIN_DIR)/clientPOA: $(LIB_DIR)/$(SHARED_LIB_NAME) clientPOA.o
ifeq ($(USE_CPP),true) 
	$(CL) clientPOA.o -o$(BIN_DIR)/clientPOA $(XMLCPP_LIB) $(CORBA_LIB) $(COS_LIB) -L$(LIB_DIR) -l$(SHARED_LIB) -lstdc++
endif

# Does not compile ruff@swand.lake.de 2001-09-03
#$(BIN_DIR)/clientBOA: $(LIB_DIR)/$(SHARED_LIB_NAME) clientBOA.o
#ifeq ($(USE_CPP),true) 
#ifeq ($(CORBA_CPP),mico)
#	$(CL) clientBOA.o $(CLIENT_OBJ) -o$(BIN_DIR)/clientBOA $(XMLCPP_LIB) $(CORBA_LIB) $(COS_LIB) -L$(LIB_DIR) -l$(SHARED_LIB) -lstdc++
#endif
#endif

clean: 
	$(RM) *.o $(ALL)
