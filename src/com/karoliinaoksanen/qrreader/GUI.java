package com.karoliinaoksanen.qrreader;

import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.util.ArrayList;

import javax.swing.*;
import javax.swing.border.BevelBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;

/**
 * GUI for the QR code reader.
 * 
 * Copyright 2014 Karoliina Oksanen
 * 
 * @author Karoliina Oksanen, hkoksanen@gmail.com
 *
 */

public class GUI extends JFrame {

	// auto-generated serial version UID
	private static final long serialVersionUID = -3419095517174994786L;

	private JPanel panel;
	private JLabel statusLabel;
	private JButton pdfFileButton;
	private JButton saveResultsButton;
	private JButton decodeButton;
	private JButton addDataButton;
	private JLabel pdfFileField;
	// private JTextField resultsFileField;
	private JTextArea resultsArea;
	private JTextArea unreadablePagesArea;
	private ProgressMonitor progressMonitor;
	private Task task;

	private QRDecoder decoder;

	class Task extends SwingWorker<Void, Void> {
		private int numberOfPages = decoder.getNumberOfPages();

		@Override
		protected Void doInBackground() throws Exception {
			int progress = 0;
			setProgress(0);
			try {
				statusLabel.setText("Decoding, please wait...");
				setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));

				class DecodeThread extends Thread {
					public void run() {
						decoder.processFile();
					}
				}

				DecodeThread dt = new DecodeThread();
				dt.start();

				Thread.sleep(2000);
				while (progress < 100 && !isCancelled()) {
					// get progress from decoder
					progress = (int) (((double) decoder.getCurrentPage() / numberOfPages) * 100);
					setProgress(Math.min(progress, 100));
				}

				dt.join();
			} catch (InterruptedException ignore) {
			}

			return null;
		}

		@Override
		public void done() {
			statusLabel.setText("Ready");
			setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
			addDataButton.setEnabled(true);

			// print decoded data and unreadable pages in text areas
			resultsArea.setText("Results:\n\n");
			unreadablePagesArea.setText("Unreadable pages: ");
			String newline = System.getProperty("line.separator");
			for (String str : decoder.getDecodedData())
				resultsArea.append(str + newline);
			ArrayList<Integer> badPages = decoder.getBadPages();
			for (int i = 0; i < badPages.size() - 1; i++)
				unreadablePagesArea.append(badPages.get(i) + ", ");
			unreadablePagesArea.append(badPages.get(badPages.size() - 1)
					.toString());
			resultsArea.setCaretPosition(0);
			unreadablePagesArea.setCaretPosition(0);
			
		}

	}

	public static void main(String[] args) {
		GUI window = new GUI();
		window.setVisible(true);
	}

	public GUI() {
		createComponents();
		decoder = new QRDecoder();
	}

	/**
	 * Creates the components of the GUI.
	 */
	private void createComponents() {
		this.setTitle("QR Code Reader");
		this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		panel = new JPanel();
		panel.setLayout(new BorderLayout());

		// create buttons
		pdfFileButton = new JButton("Choose PDF file to process");
		saveResultsButton = new JButton("Save results");
		decodeButton = new JButton("Decode");
		addDataButton = new JButton("Manually insert data");

		class FileChooserListener implements ActionListener {
			final JFileChooser fc = new JFileChooser();
			boolean init = true;

			public void actionPerformed(ActionEvent event) {
				if (init) { // only set filters once per program run
					FileNameExtensionFilter filter = new FileNameExtensionFilter(
							"PDF files", "pdf");
					fc.setFileFilter(filter);
					init = false;
				}
				int returnVal = fc.showOpenDialog(panel);
				if (returnVal == JFileChooser.APPROVE_OPTION) {
					File pdfFile = fc.getSelectedFile();
					pdfFileField.setText(pdfFile.getName());
					if (decoder.hasOpenPdf()) // close previous pdf
						decoder.closePdf();
					decoder.openPdf(pdfFile);
					statusLabel.setText("Opened " + pdfFile.getName());
					
					// enable the decode button
					decodeButton.setEnabled(true);
					// set other buttons and results fields to their starting states
					addDataButton.setEnabled(false);
					resultsArea.setText("Results:\n\n");
					unreadablePagesArea.setText("Unreadable pages: ");
				}
			}
		}

		class SaveResultsListener implements ActionListener {
			final JFileChooser fc = new JFileChooser();
			boolean init = true;

			public void actionPerformed(ActionEvent event) {
				if (decoder.getDecodedData().size() == 0) {
					JOptionPane.showMessageDialog(panel, "No results to save.");
				} else {
					if(init) { // only set filters once per program run
					FileNameExtensionFilter txtFilter = new FileNameExtensionFilter(
							"Text files", "txt");
					FileNameExtensionFilter csvFilter = new FileNameExtensionFilter(
							"CSV files", "csv");
					fc.setFileFilter(csvFilter);
					fc.addChoosableFileFilter(txtFilter);
					init = false;
					}
					int returnVal = fc.showSaveDialog(panel);
					if (returnVal == JFileChooser.APPROVE_OPTION) {
						File resultsFile = fc.getSelectedFile();
						decoder.setResultsFile(resultsFile);
						String resultsFileName = resultsFile.getName();
						int len = resultsFileName.length();
						String format = resultsFileName.substring(len - 3, len);
						if(!(format.equalsIgnoreCase("csv") || format.equalsIgnoreCase("txt"))) {
							JOptionPane.showMessageDialog(panel, "Supported file formats: .csv and .txt");
							statusLabel.setText("Failed to save results");
						} else {
							decoder.saveResults();
							statusLabel.setText("Saved " + resultsFile.getName());
						}
					}
				}
			}

		}

		class AddDataListener implements ActionListener {

			public void actionPerformed(ActionEvent event) {
				// show dialog in which user can input data
				String newData = JOptionPane.showInputDialog("Enter data:");
				decoder.addResult(newData);
				// update results pane to include new data
				resultsArea.append(newData + "\n");
				resultsArea.setCaretPosition(resultsArea.getText().length());
			}

		}

		ActionListener pdfListener = new FileChooserListener();
		pdfFileButton.addActionListener(pdfListener);
		ActionListener resultsListener = new SaveResultsListener();
		saveResultsButton.addActionListener(resultsListener);
		ActionListener dataListener = new AddDataListener();
		addDataButton.addActionListener(dataListener);
		addDataButton.setEnabled(false); // enable after a file has been decoded

		// listener for both the action on the decode button and the property
		// changes of the progress monitor
		class DecodeListener implements ActionListener, PropertyChangeListener {
			public void actionPerformed(ActionEvent event) {
				if (decoder.hasOpenPdf()) {
					// decode pdf
					try {
						// create progress monitor
						progressMonitor = new ProgressMonitor(decodeButton,
								"Decoding file...", "", 0, 100);
						progressMonitor.setProgress(0);
						progressMonitor.setMillisToDecideToPopup(0);
						progressMonitor.setMillisToPopup(0);
						// create task, which calls decoder.processFile()
						task = new Task();
						task.addPropertyChangeListener(this);
						task.execute();
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			}

			@Override
			public void propertyChange(PropertyChangeEvent evt) {
				if ("progress" == evt.getPropertyName()) {
					int progress = (Integer) evt.getNewValue();
					progressMonitor.setProgress(progress);
					String message = String.format("Completed %d%%.\n",
							progress);
					progressMonitor.setNote(message);
					if (progressMonitor.isCanceled() || task.isDone()) {
						if (progressMonitor.isCanceled()) {
							task.cancel(true);
							statusLabel.setText("Decoding canceled.");
						} else {
							statusLabel.setText("Decoding completed.");
						}
					}
				}

			}
		}

		ActionListener dlistener = new DecodeListener();
		decodeButton.addActionListener(dlistener);
		decodeButton.setEnabled(false); // disabled until PDF file has been
										// opened

		// create text fields for filenames
		pdfFileField = new JLabel();
		pdfFileField.setText("No PDF file chosen");

		JPanel pdfFilePanel = new JPanel();
		pdfFilePanel.setLayout(new GridLayout(1, 2, 20, 20));
		pdfFilePanel.add(pdfFileButton);
		pdfFilePanel.add(pdfFileField);

		JPanel decodePanel = new JPanel();
		decodePanel.setLayout(new GridLayout(1, 2, 20, 20));
		decodePanel.add(decodeButton);
		decodePanel.add(saveResultsButton);
		decodePanel.add(addDataButton);

		// arrange buttons and text fields in panel
		JPanel topPanel = new JPanel();
		topPanel.setLayout(new GridLayout(2, 1, 20, 20));
		topPanel.add(pdfFilePanel);
		topPanel.add(decodePanel);
		topPanel.setBorder(new EmptyBorder(20, 20, 10, 20));

		// create text areas for results and put them inside scroll panes
		resultsArea = new JTextArea("Results:\n\n", 20, 30);
		unreadablePagesArea = new JTextArea("Unreadable pages: ", 3, 30);
		resultsArea.setEditable(false);
		unreadablePagesArea.setEditable(false);
		unreadablePagesArea.setLineWrap(true);
		unreadablePagesArea.setWrapStyleWord(true);
		JScrollPane resultsScrollPane = new JScrollPane(resultsArea);
		JScrollPane unreadablePagesScrollPane = new JScrollPane(
				unreadablePagesArea);

		// arrange text areas in a panel
		JPanel resultsPanel = new JPanel();
		resultsPanel.setLayout(new BorderLayout());
		resultsPanel.setBorder(new EmptyBorder(5, 20, 5, 20));
		resultsPanel.add(resultsScrollPane, BorderLayout.CENTER);
		resultsPanel.add(unreadablePagesScrollPane, BorderLayout.SOUTH);

		// create status bar
		JPanel statusPanel = new JPanel();
		statusPanel.setBorder(new BevelBorder(BevelBorder.LOWERED));
		statusPanel.setPreferredSize(new Dimension(panel.getWidth(), 20));
		statusPanel.setLayout(new BoxLayout(statusPanel, BoxLayout.X_AXIS));
		statusLabel = new JLabel("Ready");
		statusLabel.setHorizontalAlignment(SwingConstants.LEFT);
		statusPanel.add(statusLabel);

		// add components to main panel
		panel.add(topPanel, BorderLayout.NORTH);
		panel.add(resultsPanel, BorderLayout.CENTER);
		panel.add(statusPanel, BorderLayout.SOUTH);

		this.add(panel);
		this.pack();
	}

	// close pdf file when exiting program
	protected void processWindowEvent(WindowEvent e) {
		if (e.getID() == WindowEvent.WINDOW_CLOSING) {
			if (decoder.hasOpenPdf()) {
				decoder.closePdf();
			}
			dispose();
		}
	}

}
