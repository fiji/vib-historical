import java.awt.*;
import java.awt.event.*;

import java.util.Vector;
import java.io.File;

import ij.IJ;
import ij.ImagePlus;
import ij.gui.GenericDialog;
import ij.plugin.PlugIn;
import ij.io.OpenDialog;
import ij.io.SaveDialog;

import vib.app.gui.Console;
import vib.app.gui.FileGroupDialog;
import vib.app.FileGroup;
import vib.app.Options;
import vib.app.module.EndModule;
import vib.app.module.Module;

public class VIB_Protocol implements PlugIn, ActionListener {

	// indices in the getStringFields() / getNumericFields()
	static final int WD = 0;
	static final int TEMPL = 1;
	static final int NO_CHANNEL = 0;
	static final int REF_CHANNEL = 1;
	static final int RES_F = 2;

	private Button fg, load, save, templateButton;
	private Options options;
	private GenericDialog gd;
	private FileGroupDialog fgd;
	private File template;
	
	public void run(String arg) {
		options = new Options();
		System.out.println(System.getProperty("user.dir"));

		gd = new GenericDialog("VIB Protocol");
		fgd = new FileGroupDialog(options.getFileGroup());
		templateButton = fgd.getTemplateButton();
		templateButton.addActionListener(this);
		
		gd.addPanel(fgd);
		gd.addStringField("Working directory","", 25);
		gd.addStringField("Template", "", 25);
		gd.addNumericField("No of channels", 2, 0);
		gd.addNumericField("No of the reference channel", 2, 0);
		gd.addNumericField("Resampling factor", 2, 0);

		final TextField wdtf = (TextField)gd.getStringFields().get(WD);
		wdtf.addFocusListener(new FocusAdapter() {
			public void focusLost(FocusEvent e) {
				File f = new File(wdtf.getText() + 
							File.separator + "options.config");
				if(f.exists()) {
					options.loadFrom(f.getAbsolutePath());
					initTextFields();
				}
			}
		});

		// make the template textfield ineditable
		((TextField)gd.getStringFields().get(TEMPL)).setEditable(false);

		gd.showDialog();
		if(gd.wasCanceled())
			return;

		initOptions();
		options.saveTo(options.getWorkingDirectory() 
					+ File.separator + "options.config");

		Console console = Console.instance();
		final Frame f = new Frame();
		f.addWindowListener(new WindowAdapter() {
			public void windowClosing(WindowEvent e) {
				f.dispose();
			}
		});
		f.add(console);
		f.setSize(200,200);
		f.setVisible(true);
		Module m = null;
		m = new EndModule(null, options);
		m.run();
	}

	public void initTextFields() {
		template = options.getTemplate();
		setString(WD, options.getWorkingDirectory().getAbsolutePath());
		setString(TEMPL, template.getName());
		setNumber(NO_CHANNEL, options.getNumChannels());
		setNumber(REF_CHANNEL, options.getRefChannel());
		setNumber(RES_F, options.getResamplingFactor());
		fgd.update();
	}

	public void initOptions() {
		options.setWorkingDirectory(new File(getString(WD)));
		options.setTemplate(template);
		options.setNumChannels(getNumber(NO_CHANNEL));
		options.setRefChannel(getNumber(REF_CHANNEL));
		options.setResamplingFactor(getNumber(RES_F));
	}

	private String getChoice(int i) {
		Choice c = (Choice)gd.getChoices().get(i);
		return c.getSelectedItem();
	}

	private void setChoice(int i, String val) {
		((Choice)gd.getChoices().get(i)).select(val);
	}

	private int getNumber(int i) {
		TextField tf = (TextField)gd.getNumericFields().get(i);
		double d = 0;
		try {
			d = Double.parseDouble(tf.getText());
		} catch (NumberFormatException e) {
			IJ.error(tf.getText() + " is not a number");
		}
		return (int)Math.round(d);
	}

	private String getString(int i) {
		TextField tf = (TextField)gd.getStringFields().get(i);
		return tf.getText();
	}

	private void setNumber(int i, int num) {
		((TextField)gd.getNumericFields().get(i)).
			setText(Integer.toString(num));
	}

	private void setString(int i, String st) {
		((TextField)gd.getStringFields().get(i)).setText(st);
	}

	public void actionPerformed(ActionEvent e) {
		if(e.getSource() == templateButton) {
			File selected = fgd.getSelected();
			if(selected != null) {
				template = selected;
				setString(TEMPL, selected.getName());
			}
		}
	}
}
