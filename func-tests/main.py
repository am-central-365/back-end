import sys
from os import listdir
from os.path import isfile, join

TESTS_DIR = "tests"

config = None

class Config:
    def __init__(self, args):
        self.args = args
        self.db_usr = self._consume_2param("-u", "--user") or "ituser"
        self.db_pwd = self._consume_2param("-p", "--pass") or "itpass"
        self.db_url = self._consume_2param("-d", "--db") or "localhost/it"
        self.api_base = self._consume_2param("-a", "--api") or "http://localhost:24941/v0.1"
        self.tests = self.args or self._get_test_filenames(TESTS_DIR)
        del self.args

    def __str__(self): return str(self.__dict__)

    def _consume_2param(self, *names):
        """ Read parameter """
        for k in xrange(1, len(self.args)):
            if self.args[k] in names:
                if k+1 >= len(self.args):
                    print "Fatal: arg", self.args[k], "requires a parameter"
                    sys.exit(2)
                v = self.args[k+1]
                del self.args[k]
                del self.args[k]
                return v
        return None

    def _get_test_filenames(self, dir_name):
        return sorted([f for f in listdir(dir_name) if f.endswith(".py") and f != "__init__.py" and isfile(join(dir_name, f))])


def run_tests():
    print "in main, config:", config
    for test_file in config.tests:
        test_name = test_file[:-3]  # strip off '.py'
        imp_mod = __import__(TESTS_DIR+"."+test_name)
        test_module = getattr(imp_mod, test_name)
        test_module.main(config)

if __name__ == "__main__":
    config = Config(sys.argv[1:])
    run_tests()
