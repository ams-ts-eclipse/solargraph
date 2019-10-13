package io.github.pyvesb.eclipse_solargraph.server;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.IJobChangeEvent;
import org.eclipse.core.runtime.jobs.JobChangeAdapter;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.lsp4e.server.ProcessStreamConnectionProvider;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.dialogs.PreferencesUtil;

import io.github.pyvesb.eclipse_solargraph.SolargraphPlugin;
import io.github.pyvesb.eclipse_solargraph.preferences.PreferencePage;
import io.github.pyvesb.eclipse_solargraph.preferences.Preferences;
import io.github.pyvesb.eclipse_solargraph.utils.CommandHelper;
import io.github.pyvesb.eclipse_solargraph.utils.CommandJob;

public class SolargraphStreamConnectionProvider extends ProcessStreamConnectionProvider {

	private static final String PLUGIN_STATE_LOCATION = SolargraphPlugin.getDefault().getStateLocation().toOSString();
	private static final AtomicBoolean HAS_DISPLAYED_NOT_FOUND_WARNING = new AtomicBoolean();
	private static final AtomicBoolean HAS_UPDATED_SOLARGRAPH = new AtomicBoolean();

	public SolargraphStreamConnectionProvider() {
		super(getSolargraphCommand(), System.getProperty("user.dir"));
	}

	@Override
	public void start() throws IOException {
		if (getCommands().isEmpty()) {
			// Attempt to find and set Solargraph command again - the gem may have been installed in the meantime.
			setCommands(getSolargraphCommand());
		}
		if (getCommands().isEmpty() && !HAS_DISPLAYED_NOT_FOUND_WARNING.getAndSet(true)) {
			displayNotFoundWarning();
		} 
		super.start();
		if (SolargraphPlugin.getPreferences().getBoolean(Preferences.UPDATE_GEM, Preferences.UPDATE_GEM_DEFAULT)
				&& !HAS_UPDATED_SOLARGRAPH.getAndSet(true)) {
			updateSolargraph();
		}
	}

	@Override
	public String toString() {
		return "SolargraphStreamConnectionProvider [command=" + getCommands().stream().collect(Collectors.joining(" "))
				+ ", directory=" + getWorkingDirectory() + "]";
	}

	private static List<String> getSolargraphCommand() {
		String solargraphPath = SolargraphPlugin.getPreferences().get(Preferences.GEM_PATH, Preferences.GEM_PATH_DEFAULT);
		return new File(solargraphPath).exists() ? Arrays.asList(solargraphPath, "stdio") : Arrays.asList();
	}

	private void displayNotFoundWarning() {
		Display display = Display.getDefault();
		display.asyncExec(() -> {
			MessageDialog dialog = new MessageDialog(display.getActiveShell(), "Solargraph was not found", null,
					"Key features will not be available. Let Eclipse install the gem locally or specify its path "
							+ "after running \"gem install solargraph\" in a terminal.",
					MessageDialog.WARNING, 0, "Install gem", "Specify path");
			if (dialog.open() == 0) { // First button index, install.
				installSolargraph();
			} else {
				PreferencesUtil.createPreferenceDialogOn(null, PreferencePage.PAGE_ID, null, null).open();
			}
		});
	}

	private void installSolargraph() {
		List<String> gemCommand = CommandHelper.getPlatformCommand("gem install -V "
				+ "-n " + PLUGIN_STATE_LOCATION + " solargraph");
		CommandJob installCommandJob = new CommandJob(gemCommand, "Installation in progress");
		installCommandJob.addJobChangeListener(new JobChangeAdapter() {

			@Override
			public void done(IJobChangeEvent event) {
				if (event.getResult() == Status.OK_STATUS) {
					String solargraphExecutable = CommandHelper.isWindows() ? "solargraph.bat" : "solargraph";
					String solargraphPath = PLUGIN_STATE_LOCATION + File.separator + solargraphExecutable;
					SolargraphPlugin.getPreferences().put(Preferences.GEM_PATH, solargraphPath);
				} else {
					Display display = Display.getDefault();
					display.asyncExec(() -> MessageDialog.openError(display.getActiveShell(),
							"Solargraph intallation failed", "Please open the Error Log view for details. To manually "
									+ "install it, run \"gem install solargraph\" in a terminal and specify the path in the plugin's preferences."));
				}
			}
		});
		installCommandJob.schedule();
	}

	private void updateSolargraph() {
		List<String> gemCommand = CommandHelper.getPlatformCommand("gem update -V "
				+ "-n " + PLUGIN_STATE_LOCATION + " solargraph");
		new CommandJob(gemCommand, "Update in progress").schedule();
	}

}
