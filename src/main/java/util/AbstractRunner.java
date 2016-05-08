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
			throw new RuntimeException(e.getMessage());
		}		
		
	}

}
