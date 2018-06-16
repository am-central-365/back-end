import sys
import mysql.connector

class Config:
    def __init__(self, args):
        self.args = args
        self.db_usr = self._get_param("-u", "--user") or "ittest"
        self.db_pwd = self._get_param("-p", "--pass") or "ittest"
        self.db_url = self._get_param("-d", "--db")   or "localhost/it"
        self.api_base = self._get_param("-a", "--api")   or "http://localhost:24941/v0.1"

    def __str__(self): return str({ "db_usr": self.db_usr, "db_pwd": "~not-shown~", "db_url": self.db_url, "api_base": self.api_base })

    def _get_param(self, *names):
        for k in xrange(1, len(self.args)):
            if self.args[k] in names:
                if k+1 >= len(self.args):
                    print "Fatal: arg", self.args[k], "requires a parameter"
                    sys.exit(2)
                return self.args[k+1]
        return None


config = None


def main():
    print "in main, config:", config

if __name__ == "__main__":
    config = Config(sys.argv)
    main()
