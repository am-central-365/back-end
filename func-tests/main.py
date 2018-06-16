import sys
from os import listdir, getcwd
from os.path import isfile, join
import traceback

import lib.logger as logger

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
                    logger.raw_log("Fatal: arg %s requires a parameter" % self.args[k])
                    sys.exit(2)
                v = self.args[k+1]
                del self.args[k]
                del self.args[k]
                return v
        return None

    def _get_test_filenames(self, dir_name):
        return sorted([f for f in listdir(dir_name) if f.endswith(".py") and f != "__init__.py" and isfile(join(dir_name, f))])


def run_tests():
    logger.raw_log("Running %d tests: %s" % (len(config.tests), config.tests))
    cur_dir = getcwd()
    tests_cnt = 0
    keep_going = True
    k = 0
    while keep_going and k < len(config.tests):
        test_name = config.tests[k][:-3]  # strip off '.py'
        logger.raw_log("configuring %s" % test_name)
        imp_mod = __import__(TESTS_DIR+"."+test_name)
        test_module = getattr(imp_mod, test_name)

        keep_going = True
        if hasattr(test_module, "main"):
            logger.raw_log("running %s.main()..." % test_name)
            try:
                keep_going = test_module.main(config)
                tests_cnt += 1 if keep_going else 0
                logger.log("SUCCEEDED: %s", test_name)
            except: # Exception as x:
                # Only print trace from our code.
                #   We detect our code by their path starting with the current dir
                # ? shouldn't it be if not v[0].startswith(cur_dir) ?
                _, _, exc_traceback = sys.exc_info()
                stack_depth = 0
                for k,v in enumerate(traceback.extract_tb(exc_traceback)):
                    if v[0].startswith(cur_dir):
                        stack_depth = k
                #stack_depth = next((k for k,v in enumerate(traceback.extract_stack()) if v[0].startswith(cur_dir)), 1)
                print "++ stack_depth:", stack_depth
                traceback.print_exc(stack_depth+1)
                sys.stderr.flush()
                logger.log("FAILED: %s", test_name)
                keep_going = False
        else:
            logger.log("SKIPPED %s: no main() was found" % test_name)

        k += 1

    status = "SUCCEEDED" if tests_cnt == len(config.tests) else "FAILED"
    logger.raw_log("%s, ran %d tests" % (status, tests_cnt))


if __name__ == "__main__":
    config = Config(sys.argv[1:])
    run_tests()
