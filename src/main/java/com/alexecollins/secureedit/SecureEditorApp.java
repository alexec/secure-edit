package com.alexecollins.secureedit;

import javax.swing.*;
import java.io.File;

/**
 * @author alexec (alex.e.c@gmail.com)
 */
public class SecureEditorApp {
	public static void main(final String[] args) throws Exception {
		SwingUtilities.invokeAndWait(new Runnable() {
			@Override
			public void run() {
				try {
					UIManager.setLookAndFeel(
							UIManager.getSystemLookAndFeelClassName());
					final EditorFrame f = new EditorFrame();
					if (args.length == 1) {
						f.open(new File(args[0]));
					}
					f.pack();
					f.setVisible(true);
				} catch (Exception e) {
					throw new RuntimeException(e);
				}
			}
		});
	}
}
