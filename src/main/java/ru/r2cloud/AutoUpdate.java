package ru.r2cloud;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import ru.r2cloud.uitl.Configuration;
import ru.r2cloud.uitl.Util;

public class AutoUpdate {

	private static final Logger LOG = Logger.getLogger(AutoUpdate.class.getName());
	private final static String FILE_LOCK = "DO_NOT_UPDATE";

	private final File basepath;

	public AutoUpdate(Configuration config) {
		basepath = Util.initDirectory(config.getProperty("auto.update.basepath.location"));
	}

	public boolean isEnabled() {
		return !new File(basepath, FILE_LOCK).exists();
	}

	public void setEnabled(boolean enabled) {
		File fileLock = new File(basepath, FILE_LOCK);
		if (!enabled && !fileLock.exists()) {
			try {
				if (!fileLock.createNewFile()) {
					LOG.log(Level.SEVERE, "unable to create file lock for auto update at: " + fileLock.getAbsolutePath());
				}
			} catch (IOException e) {
				LOG.log(Level.SEVERE, "unable to create file lock for auto update at: " + fileLock.getAbsolutePath(), e);
			}
		}
		if (enabled && fileLock.exists()) {
			if (!fileLock.delete()) {
				LOG.log(Level.SEVERE, "unable to remove file lock for auto update at: " + fileLock.getAbsolutePath());
			}
		}
	}

}
