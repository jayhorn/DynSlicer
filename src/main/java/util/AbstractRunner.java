package util;

import java.lang.ProcessBuilder.Redirect;
import java.util.List;

import org.apache.commons.lang.StringUtils;

public abstract class AbstractRunner {

	private static final long DEFAULT_TIMEOUT = 10000;

	protected void execute(List<String> cmd) {
		execute(cmd, DEFAULT_TIMEOUT);
	}

	protected void execute(List<String> cmd, long ms_timeout) {
		execute(StringUtils.join(cmd, " "), ms_timeout);
	}

	protected void execute(String cmd) {
		execute(cmd, DEFAULT_TIMEOUT);
	}

	protected void execute(String cmd, long ms_timeout) {
		System.out.println("exec: " + cmd);
		ProcessBuilder builder = new ProcessBuilder(cmd.split("\\s+"));
		builder.redirectOutput(Redirect.INHERIT);
		builder.redirectError(Redirect.INHERIT);
		try {
			final Process process = builder.start();
			process.waitFor();
		} catch (Exception e) {
			e.printStackTrace(System.err);
		}
		System.out.println("Done.");
		// System.out.println("exec: " + cmd);
		// try {
		// Process process = Runtime.getRuntime()
		// .exec(cmd.toString());
		// System.err.println(process.waitFor());
		// } catch (Exception e) {
		// e.printStackTrace(System.err);
		// }
		// try {
		// Process process = Runtime.getRuntime()
		// .exec(cmd.toString());
		// Worker worker = new Worker(process);
		// worker.start();
		// try {
		// worker.join(10000);
		// } catch (InterruptedException ex) {
		// worker.interrupt();
		// Thread.currentThread().interrupt();
		// System.err.println("Failed to generate PDF from dot");
		// } finally {
		// process.destroy();
		// }
		//
		// } catch (Throwable e) {
		// System.err.println(e.toString());
		// }
	}

	static class Worker extends Thread {
		public final Process process;
		public Integer exit;

		Worker(Process process) {
			this.process = process;
		}

		public void run() {
			try {
				exit = process.waitFor();
			} catch (InterruptedException ignore) {
				return;
			}
		}
	}
}
