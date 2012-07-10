package com.alexecollins.secureedit;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.*;
import java.net.URI;
import java.security.spec.KeySpec;
import java.util.Arrays;
import java.util.Date;
import java.util.ResourceBundle;

import static java.text.MessageFormat.format;

/**
 * @author alexec (alex.e.c@gmail.com)
 */
public class EditorFrame extends JFrame {

	private final JLabel status = new JLabel() {
		{setHorizontalAlignment(SwingConstants.LEFT);}
		@Override
		public void setText(String text) {
			super.setText(new Date() + ": " + text);
		}
	};
	private final ResourceBundle resourceBundle = ResourceBundle.getBundle("com/alexecollins/secureedit/resources");
	private final JEditorPane text = new JEditorPane();
	private final String algorithm = "AES";
	private byte[] key;
	private File file;
	private boolean unsaved;

	private class MenuBar extends JMenuBar {
		private MenuBar() {
			add(Box.createHorizontalGlue());
			final JMenu help = new JMenu(resourceBundle.getString("help"));
			help.add(new JMenuItem(resourceBundle.getString("about")) {{
				addActionListener(new ActionListener() {
					@Override
					public void actionPerformed(ActionEvent e) {
						JOptionPane.showMessageDialog(EditorFrame.this,
								format(resourceBundle.getString("about.text"), algorithm));
					}
				});}
			});
			help.add(new JMenuItem(resourceBundle.getString("website")) {{
				addActionListener(new ActionListener() {
					@Override
					public void actionPerformed(ActionEvent actionEvent) {
						try {
							Desktop.getDesktop().browse(new URI("http://www.alexecollins.com/"));
						} catch (Exception e) {
							JOptionPane.showMessageDialog(EditorFrame.this, format(resourceBundle.getString("error.browser"), e.getMessage()));
						}
					}
				});
			}});
			add(help);
		}
	}

	private class EditorPanel extends JPanel {
		private EditorPanel() {
			super(new BorderLayout());

			add(new EditorToolBar(), BorderLayout.NORTH);
			add(new JScrollPane(text), BorderLayout.CENTER);
			add(new StatusPanel(), BorderLayout.SOUTH);
		}
	}

	private class EditorToolBar extends JToolBar {
		private EditorToolBar() {
			add(new JButton(resourceBundle.getString("new")) {{
				addActionListener(new ActionListener() {
					@Override
					public void actionPerformed(ActionEvent actionEvent) {
						neu();
					}
				});}
			});
			add(new JButton(resourceBundle.getString("load")) {{
				addActionListener(new ActionListener() {
					@Override
					public void actionPerformed(ActionEvent actionEvent) {
						load();
					}
				});}
			});
			add(new JButton(resourceBundle.getString("save")) {{
				addActionListener(new ActionListener() {
					@Override
					public void actionPerformed(ActionEvent actionEvent) {
						save();
					}
				});
			}});
		}
	}

	private void load() {
		JFileChooser chooser = new JFileChooser();
		if (chooser.showOpenDialog(EditorFrame.this) != JFileChooser.APPROVE_OPTION) {return;}
		try {
			open(chooser.getSelectedFile());
		} catch (Exception e) {
			JOptionPane.showMessageDialog(EditorFrame.this,
					format(resourceBundle.getString("error.open"), e.getMessage()), resourceBundle.getString("error"), JOptionPane.ERROR_MESSAGE);
		}
	}

	private class StatusPanel extends JPanel {
		private StatusPanel() {
			add(status);
		}
	}

	// new document
	private void neu() {

		if (unsaved) {
			if (JOptionPane.showConfirmDialog(this, resourceBundle.getString("confirm.unsaved"), resourceBundle.getString("new"),
					JOptionPane.YES_NO_OPTION)
					!= JOptionPane.YES_OPTION) {
				return;
			}
		}

		file = null;
		key = null;
		text.setText("");
		setTitle(resourceBundle.getString("untitled"));
		status.setText(resourceBundle.getString("status.new"));
		setUnsaved(false);
	}

	public void open(File file) throws Exception {
		if (unsaved) {
			if (JOptionPane.showConfirmDialog(this,resourceBundle.getString("confirm.unsaved"), resourceBundle.getString("new"),
					JOptionPane.YES_NO_OPTION)
					!= JOptionPane.YES_OPTION) {
				return;
			}
		}
		if (!requestKey(false)) {return;}
		SecretKeySpec keySpec = new SecretKeySpec(key, algorithm);
		Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
		cipher.init(Cipher.DECRYPT_MODE, keySpec);
		BufferedReader in = new BufferedReader(new InputStreamReader(new CipherInputStream(new BufferedInputStream(new FileInputStream(file)), cipher)));
		try {
			StringBuilder b = new StringBuilder();
			String l;
			while ((l = in.readLine()) != null) {
				b.append(l).append('\n');
			}
			text.setText(b.toString());
		} finally {
			in.close();
		}
		this.file = file;
		setTitle(file.toString());
		setUnsaved(false);
		status.setText(format(resourceBundle.getString("status.opened"), file));
	}

	private boolean requestKey(boolean confirm) throws Exception {

		JPanel panel = new JPanel(new GridLayout(2,2));
		final JPasswordField passwordField = new JPasswordField();
		panel.add(new JLabel("Password"));
		panel.add(passwordField);

		final JPasswordField passwordField1 = new JPasswordField();
		if (confirm) {
			panel.add(new JLabel("Confirm password"));
			panel.add(passwordField1);
		}
		JOptionPane.showConfirmDialog(this, panel, resourceBundle.getString("input.password"),  JOptionPane.OK_OPTION, JOptionPane.PLAIN_MESSAGE);

		if (confirm && !Arrays.equals(passwordField.getPassword(), passwordField1.getPassword())) {
			JOptionPane.showMessageDialog(this, resourceBundle.getString("error.passwordMatch"));
			return false;
		}

		char[] password = passwordField.getPassword();

		SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
		byte[] salt = new byte[] {1,2,3,4}; // TODO - new salt
		KeySpec spec = new PBEKeySpec(password, salt, 65536, 256);
		key = factory.generateSecret(spec).getEncoded();
		return true;
	}

	private void save() {

		try {
			if (key == null) {
				if (!requestKey(true)) {return;}
			}

			if (file == null) {
				JFileChooser chooser = new JFileChooser();
				if (chooser.showSaveDialog(EditorFrame.this) != JFileChooser.APPROVE_OPTION) {return;}
				file = chooser.getSelectedFile();
			}
			SecretKeySpec keySpec = new SecretKeySpec(key, algorithm);
			Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
			cipher.init(Cipher.ENCRYPT_MODE, keySpec);
			PrintWriter out = new PrintWriter(new CipherOutputStream(new BufferedOutputStream(new FileOutputStream(file)), cipher));
			try {
				out.print(text.getText());
				out.flush();
			} finally {
				out.close();
			}
			status.setText(format(resourceBundle.getString("status.saved"), file));
			setUnsaved(false);
			setTitle(file.toString());
		} catch (Exception e) {
			JOptionPane.showMessageDialog(EditorFrame.this,
					format(resourceBundle.getString("error.save"), e.getMessage()),
					resourceBundle.getString("save"), JOptionPane.ERROR_MESSAGE);
		}
	}

	private void setUnsaved(boolean unsaved) {
		if (unsaved)
			status.setText(resourceBundle.getString("unsaved"));
		this.unsaved = unsaved;
	}

	@Override
	public void setTitle(String s) {
		super.setTitle(format(resourceBundle.getString("title"), s, algorithm));
	}

	public EditorFrame() {
		super();
		setMinimumSize(new Dimension(400, 300));
		setJMenuBar(new MenuBar());
		getContentPane().add(new EditorPanel());

		text.getDocument().addDocumentListener(new DocumentListener() {
			@Override
			public void insertUpdate(DocumentEvent documentEvent) {
				setUnsaved(true);
			}

			@Override
			public void removeUpdate(DocumentEvent documentEvent) {
				setUnsaved(true);
			}

			@Override
			public void changedUpdate(DocumentEvent documentEvent) {
				setUnsaved(true);
			}
		});

		setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);

		addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent windowEvent) {
				if (unsaved) {
					if (JOptionPane.showConfirmDialog(EditorFrame.this, resourceBundle.getString("confirm.unsaved"),
							resourceBundle.getString("close"),
							JOptionPane.YES_NO_OPTION)
							!= JOptionPane.YES_OPTION) {
						return;
					}
				}
				System.exit(0);
			}
		});

		setTitle(resourceBundle.getString("new"));
		status.setText(format(resourceBundle.getString("status.encryption"), algorithm));
	}
}
