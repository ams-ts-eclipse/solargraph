package io.github.pyvesb.eclipse_solargraph.server;

import static io.github.pyvesb.eclipse_solargraph.preferences.BooleanPreferences.UPDATE_GEM;
import static io.github.pyvesb.eclipse_solargraph.preferences.StringPreferences.GEM_PATH;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.IJobChangeEvent;
import org.eclipse.core.runtime.jobs.JobChangeAdapter;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.lsp4e.server.ProcessStreamConnectionProvider;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.dialogs.PreferencesUtil;
import org.osgi.framework.FrameworkUtil;

import io.github.pyvesb.eclipse_solargraph.preferences.PreferencePage;
import io.github.pyvesb.eclipse_solargraph.utils.CommandHelper;
import io.github.pyvesb.eclipse_solargraph.utils.CommandJob;

public class SolargraphStreamConnectionProvider extends ProcessStreamConnectionProvider {

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
		if (UPDATE_GEM.getValue() && !HAS_UPDATED_SOLARGRAPH.getAndSet(true)) {
			updateSolargraph();
		}
	}

	private static List<String> getSolargraphCommand() {
		return new File(GEM_PATH.getValue()).exists() ? Arrays.asList(GEM_PATH.getValue(), "stdio") : Arrays.asList();
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
		String pluginStateLocation = getPluginStateLocation();
		String[] command = CommandHelper.getPlatformCommand("gem install -V " + "-n " + pluginStateLocation + " solargraph");
		CommandJob installCommandJob = new CommandJob(command, "Installation in progress");
		installCommandJob.addJobChangeListener(new JobChangeAdapter() {

			@Override
			public void done(IJobChangeEvent event) {
				if (event.getResult() == Status.OK_STATUS) {
					String solargraphExecutable = CommandHelper.isWindows() ? "solargraph.bat" : "solargraph";
					String solargraphPath = getPluginStateLocation() + File.separator + solargraphExecutable;
					GEM_PATH.setValue(solargraphPath);
					HAS_UPDATED_SOLARGRAPH.set(true);
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
		String[] command = CommandHelper
				.getPlatformCommand("gem update -V " + "-n " + getPluginStateLocation() + " solargraph");
		new CommandJob(command, "Update in progress").schedule(30000L);
	}

	private String getPluginStateLocation() {
		return Platform.getStateLocation(FrameworkUtil.getBundle(this.getClass())).toOSString();
	}

}
