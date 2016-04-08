import os
import argparse
import common

argparser = argparse.ArgumentParser(add_help=False)
dyntrace_group = argparser.add_argument_group('dyntrace arguments')

dyntrace_group.add_argument('--dyntrace-jar', metavar='<dyntrace-jar>',
                         action='store',default=None, dest='dyntrace_jar',
                         help='Path to dyntrace.jar')

dyntrace_group.add_argument('--out-dir', metavar='<out-dir>',
                         action='store',default=None, dest='out_dir',
                         help='Path for output')

def run(args, javac_commands, jars):
  if not args.dyntrace_jar:
    print "Could not run dyntrace tool: missing arg --dyntrace-jar"
    return
  if not args.out_dir:
    args.out_dir = "./dyntrace_output"

  dyntrace_command = ["java", "-jar", args.dyntrace_jar]

  i = 1

  for jc in javac_commands:
    javac_switches = jc['javac_switches']

    cmd = dyntrace_command + [common.classpath(jc), common.class_directory(jc), args.out_dir]

    common.run_cmd(cmd)
    i = i + 1
