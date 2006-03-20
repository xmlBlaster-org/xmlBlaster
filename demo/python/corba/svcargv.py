import filesvcsender, win32serviceutil
argv=r'["-s", "localhost", "-l", "FileSender", "-f", r"F:\xmlBlaster\demo\omniorbpy\filesvcsender.log"]'
win32serviceutil.SetServiceCustomOption(filesvcsender.XMLBlasterFileSenderService, "argv", argv)
