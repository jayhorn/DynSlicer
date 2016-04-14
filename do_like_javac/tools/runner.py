import os
import argparse
import common

argparser = argparse.ArgumentParser(add_help=False)
dyntrace_group = argparser.add_argument_group('dyntrace arguments')


dyntrace_group.add_argument('--out-dir', metavar='<out-dir>',
                         action='store',default=None, dest='out_dir',
                         help='Path for output')

def run(args, javac_commands, jars):
  if not args.out_dir:
    args.out_dir = "./dyntrace_output"

  current_dir = os.path.dirname(os.path.realpath(__file__))
  current_dir = os.path.join(current_dir, os.pardir)
  current_dir = os.path.join(current_dir, os.pardir)
  current_dir += "/"


  dyntrace_command = ["java", "-jar", os.path.join(current_dir, "build/libs/DynSlicer.jar")]

  i = 1

  for jc in javac_commands:
    javac_switches = jc['javac_switches']
    cmd = dyntrace_command + [common.classpath(jc), common.class_directory(jc), args.out_dir, current_dir]

    common.run_cmd(cmd)
    i = i + 1
