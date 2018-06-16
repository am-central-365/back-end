
IDNET = 2 * ' '

def raw_log(msg):
    print msg

def idented_raw_log(msg):
    print IDNET + msg

def log(msg, *args):
    idented_raw_log(msg % args)
