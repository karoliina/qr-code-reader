package com.karoliinaoksanen.qrreader;

/**
 * Decodes QR codes from a PNG image.
 * 
 * Copyright 2014 Karoliina Oksanen
 * 
 * @author Karoliina Oksanen, hkoksanen@gmail.com
 * 
 */

import java.awt.image.BufferedImage;
import java.awt.image.BufferedImageOp;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Hashtable;

//import javax.imageio.ImageIO;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;

import com.google.zxing.DecodeHintType;
import com.google.zxing.LuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.qrcode.QRCodeReader;
import com.google.zxing.BinaryBitmap;

public class QRDecoder {

	private static int DEFAULT_RESOLUTION = 72;
	private static int RETAKE_RESOLUTION = 200;
	private PDDocument pdf;
	private File resultsFile;
	private int currentPage; // current page of decoding, used for progress
								// monitor
	private ArrayList<String> decodedData; // data decoded from QR codes
	private ArrayList<Integer> badPages; // page numbers of unreadable pages

	public QRDecoder() {
		pdf = null;
		resultsFile = null;
		currentPage = 0;
		decodedData = new ArrayList<String>();
		badPages = new ArrayList<Integer>();
	}

	/**
	 * Attempts to open a PDF file and associate it with the decoder.
	 * 
	 * @param filename
	 * @return true if opening the file was successful, false otherwise
	 */
	public boolean openPdf(File pdfFile) {
		// close previous pdf file if needed
		if (hasOpenPdf())
			closePdf();
		// open new pdf file
		try {
			pdf = PDDocument.load(pdfFile);
			return true;
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		}
	}

	/**
	 * Associates a text file object with the decoder, the results of the
	 * decoding will be saved in this file.
	 * 
	 * @param rfile
	 */
	public void setResultsFile(File rfile) {
		if (rfile != null)
			resultsFile = rfile;
	}

	/**
	 * Processes the pdf file associated with this QRDecoder object, saving the
	 * results in the instance variables. Does not save all BufferedImage
	 * objects in a data structure in an attempt to save memory, but discards
	 * them after use.
	 * 
	 * @param resolution
	 *            The resolution at which a second attempt of reading a page is
	 *            made, when the first attempt at the default resolution of 72
	 *            DPI fails
	 */
	public boolean processFile() {
		if (!hasOpenPdf())
			return false; // no PDF file associated with the decoder

		// clear previous results, since decoder can be re-used in GUI
		decodedData.clear();
		badPages.clear();

		ArrayList<PDPage> pages = new ArrayList<PDPage>();
		for (Object o : pdf.getDocumentCatalog().getAllPages()) {
			pages.add((PDPage) o); // cast to avoid unchecked conversion warning
		}

		// process page by page
		currentPage = 0;
		for (PDPage page : pages) {
			currentPage++;
			BufferedImage pageImage = null;
			try {
				pageImage = page.convertToImage(BufferedImage.TYPE_BYTE_GRAY,
						DEFAULT_RESOLUTION);
			} catch (IOException e) {
				e.printStackTrace();
			}
			// read QR code from buffered image
			Result result = null;
			try {
				result = decode(pageImage);
			} catch (Exception e) {
				e.printStackTrace();
			}
			if (result == null) {

				// read page with a higher resolution, apply a slight blur
				// filter, and try again
				BufferedImage pageImageHires = null;
				try {
					pageImageHires = page.convertToImage(
							BufferedImage.TYPE_BYTE_GRAY, RETAKE_RESOLUTION);
				} catch (IOException e) {
					e.printStackTrace();
				}

				BufferedImage filteredImage = processImage(pageImageHires);

				// try decoding the filtered image
				try {
					result = decode(filteredImage);
				} catch (Exception e) {
					e.printStackTrace();
				}

				// if result is still null, filtering didn't help
				if (result == null) {
					badPages.add(currentPage);

					// save page as image file for debugging/inspection
//					File outputfile = new File("page" + currentPage + ".png");
//					File outputfileFiltered = new File("page" + currentPage
//							+ "_filter.png");
//					try {
//						ImageIO.write(pageImage, "png", outputfile);
//						ImageIO.write(filteredImage, "png", outputfileFiltered);
//					} catch (IOException e1) {
//						e1.printStackTrace();
//					}
					
				} else {
					decodedData.add(result.getText());
				}
			} else {
				decodedData.add(result.getText());
			}
		}
		
		return true;
	}

	/**
	 * Saves results of the decoding to an ASCII file given as the parameter, if
	 * results exist.
	 * 
	 * @return true if saving was successful, false otherwise
	 */
	public boolean saveResults() {
		if (decodedData.size() > 0) {
			BufferedWriter writer = null;
			String newline = System.getProperty("line.separator");
			try {
				writer = new BufferedWriter(new FileWriter(resultsFile));
			} catch (IOException e) {
				e.printStackTrace();
			}
			if (writer != null) {
				for (String s : decodedData) {
					try {
						writer.write(s);
						writer.write(newline);
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
				try {
					writer.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}

			return true;
		} else { // no data to save
			return false;
		}
	}

	/**
	 * Decodes a QR code from a BufferedImage object.
	 * 
	 * @param image
	 * @return a Result object containing the decoded data or information about
	 *         an unsuccessful decoding attempt
	 * @throws Exception
	 */
	private Result decode(BufferedImage image) throws Exception {

		// create a luminance source from the BufferedImage
		LuminanceSource lumSource = new BufferedImageLuminanceSource(image);
		// create a binary bitmap from the luminance source. a Binarizer
		// converts luminance data to 1 bit data.
		BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(lumSource));

		// a reader for decoding
		QRCodeReader reader = new QRCodeReader();

		// attempt decoding and return result
		Hashtable<DecodeHintType, Boolean> hints = new Hashtable<DecodeHintType, Boolean>();
		hints.put(DecodeHintType.TRY_HARDER, true);
		return reader.decode(bitmap, hints);
	}

	/**
	 * Uses a blur filter on an image in an attempt to make it readable by the
	 * QRCodeReader.
	 * 
	 * @param image
	 *            the page in a BufferedImage object
	 * @return another BufferedImage object with the filters applied to it
	 */
	private BufferedImage processImage(BufferedImage image) {

		// default: 3x3 filter for a 72 DPI image
		double DEFAULT_SIZE = 9.0;

		// calculate filtering matrix size based on the image resolution
		int dimension = (int) Math.sqrt(DEFAULT_SIZE
				/ (double) DEFAULT_RESOLUTION * RETAKE_RESOLUTION);
		int size = dimension * dimension;

		float[] matrix = new float[size];
		for (int i = 0; i < size; i++)
			matrix[i] = 1.0f / (float) size;

		BufferedImageOp op = new ConvolveOp(new Kernel(dimension, dimension,
				matrix), ConvolveOp.EDGE_NO_OP, null);
		return op.filter(image, null);

	}

	/**
	 * Manually adds a data field into the results list (from the GUI).
	 */
	public void addResult(String data) {
		decodedData.add(data);
	}

	public boolean hasOpenPdf() {
		if (pdf != null)
			return true;
		else
			return false;
	}

	public boolean hasResultsFile() {
		if (resultsFile != null)
			return true;
		else
			return false;
	}

	public ArrayList<String> getDecodedData() {
		return decodedData;
	}

	public ArrayList<Integer> getBadPages() {
		return badPages;
	}

	public int getCurrentPage() {
		return currentPage;
	}

	public int getNumberOfPages() {
		if (hasOpenPdf())
			return pdf.getNumberOfPages();
		else
			return 0;
	}

	public void closePdf() {
		if (pdf == null)
			return; // no PDF file associated with the decoder
		try {
			pdf.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

}
