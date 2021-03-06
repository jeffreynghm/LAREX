package com.web.facade;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.apache.tomcat.util.http.fileupload.ByteArrayOutputStream;
import org.opencv.core.Size;
import org.springframework.context.annotation.Scope;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import com.web.communication.SegmentationStatus;
import com.web.controller.FileManager;
import com.web.model.Book;
import com.web.model.BookSettings;
import com.web.model.Page;
import com.web.model.PageSegmentation;
import com.web.model.Polygon;
import com.web.model.database.FileDatabase;
import com.web.model.database.IDatabase;

import larex.export.PageXMLReader;
import larex.export.PageXMLWriter;
import larex.export.SettingsReader;
import larex.export.SettingsWriter;
import larex.regionOperations.Merge;
import larex.regions.RegionManager;
import larex.segmentation.Segmenter;
import larex.segmentation.parameters.Parameters;
import larex.segmentation.result.ResultRegion;
import larex.segmentation.result.SegmentationResult;

/**
 * Segmenter using the Larex project/algorithm
 * 
 */
@Component
@Scope("session")
public class LarexFacade implements IFacade {

	private FileManager fileManager;
	private Map<Integer, larex.dataManagement.Page> exportPages = new HashMap<Integer, larex.dataManagement.Page>();
	private Map<Integer, Document> exportSettings = new HashMap<Integer, Document>();
	private boolean isInit = false;

	@Override
	public void init(FileManager fileManager) {
		this.fileManager = fileManager;
		this.isInit = true;
	}

	@Override
	public boolean isInit() {
		return isInit;
	}

	@Override
	public void clear() {
		this.isInit = false;
	}

	@Override
	public PageSegmentation segmentPage(BookSettings settings, int pageNr, boolean allowLocalResults) {
		Book book = getBook(settings.getBookID());

		Page page = book.getPage(pageNr);
		String imagePath = fileManager.getBooksPath() + File.separator + page.getImage();
		String xmlPath = imagePath.substring(0, imagePath.lastIndexOf('.')) + ".xml";

		if (allowLocalResults && new File(xmlPath).exists()) {
			SegmentationResult loadedResult = PageXMLReader.loadSegmentationResultFromDisc(xmlPath);
			PageSegmentation segmentation = LarexWebTranslator.translateResultRegionsToSegmentation(loadedResult.getRegions(), page.getId());
			segmentation.setStatus(SegmentationStatus.LOADED);
			return segmentation;
		} else {
			return segment(settings, page);
		}
	}

	@Override
	public BookSettings getDefaultSettings(Book book) {
		RegionManager regionmanager = new RegionManager();
		Parameters parameters = new Parameters(regionmanager, 0);
		return LarexWebTranslator.translateParametersToSettings(parameters, book);
	}

	@Override
	public void prepareExport(PageSegmentation segmentation, int bookID) {
		larex.dataManagement.Page exportPage = getLarexPage(getBook(bookID).getPage(segmentation.getPage()));
		exportPage.setSegmentationResult(WebLarexTranslator.translateSegmentationToSegmentationResult(segmentation));
		exportPages.put(bookID, exportPage);
	}

	@Override
	public ResponseEntity<byte[]> getPageXML(String version, int bookID) {
		larex.dataManagement.Page exportPage = exportPages.get(bookID);
		if (exportPage != null) {
			exportPage.initPage();
			Document document = PageXMLWriter.getPageXML(exportPage, version);
			exportPage.clean();
			return convertDocumentToByte(document, exportPage.getFileName());
		} else {
			throw new IllegalStateException("PageXML can't be returned. No Page has been prepared for export.");
		}
	}

	@Override
	public void savePageXMLLocal(String saveDir, String version, int bookID) {
		larex.dataManagement.Page exportPage = exportPages.get(bookID);
		if (exportPage != null) {
			exportPage.initPage();
			Document document = PageXMLWriter.getPageXML(exportPage, version);
			exportPage.clean();
			PageXMLWriter.saveDocument(document, exportPage.getFileName(), saveDir);
		}
	}

	@Override
	public void prepareSettings(BookSettings settings) {
		Parameters parameters = WebLarexTranslator.translateSettingsToParameters(settings, new Size());
		exportSettings.put(settings.getBookID(), SettingsWriter.getSettingsXML(parameters));
	}

	@Override
	public ResponseEntity<byte[]> getSettingsXML(int bookID) {
		if (exportSettings != null) {
			return convertDocumentToByte(exportSettings.get(bookID), "settings_" + getBook(bookID).getName());
		} else {
			throw new IllegalStateException("Setting can't be returned. No Setting has been prepared for export.");
		}
	}

	private ResponseEntity<byte[]> convertDocumentToByte(Document document, String filename) {
		// convert document to bytes
		byte[] documentbytes = null;
		try {
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			TransformerFactory factory = TransformerFactory.newInstance();
			Transformer transformer = factory.newTransformer();
			transformer.transform(new DOMSource(document), new StreamResult(out));
			documentbytes = out.toByteArray();
		} catch (TransformerConfigurationException e) {
			e.printStackTrace();
		} catch (TransformerException e) {
			e.printStackTrace();
		}

		// create ResponseEntry
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.parseMediaType("application/xml"));
		headers.setContentDispositionFormData(filename, filename + ".xml");
		headers.setCacheControl("must-revalidate, post-check=0, pre-check=0");

		return new ResponseEntity<byte[]>(documentbytes, headers, HttpStatus.OK);
	}

	@Override
	public Polygon merge(List<Polygon> segments, int pageNr, int bookID) {
		ArrayList<ResultRegion> resultRegions = new ArrayList<ResultRegion>();
		for (Polygon segment : segments)
			resultRegions.add(WebLarexTranslator.translateSegmentToResultRegion(segment));

		Book book = getBook(bookID);
		larex.dataManagement.Page page = getLarexPage(book.getPage(pageNr));
		page.initPage();
		ResultRegion mergedRegion = Merge.merge(resultRegions, page.getBinary().size());
		page.clean();
		System.gc();

		return LarexWebTranslator.translateResultRegionToSegment(mergedRegion);
	}

	private PageSegmentation segment(BookSettings settings, Page page) {
		PageSegmentation segmentation = null;
		larex.dataManagement.Page currentLarexPage = segmentLarex(settings, page);

		if (currentLarexPage != null) {
			SegmentationResult segmentationResult = currentLarexPage.getSegmentationResult();
			currentLarexPage.setSegmentationResult(segmentationResult);

			ArrayList<ResultRegion> regions = segmentationResult.getRegions();

			segmentation = LarexWebTranslator.translateResultRegionsToSegmentation(regions, page.getId());
		} else {
			segmentation = new PageSegmentation(page.getId(), new HashMap<String, Polygon>(),
					SegmentationStatus.MISSINGFILE, new ArrayList<String>());
		}
		return segmentation;
	}

	private larex.dataManagement.Page segmentLarex(BookSettings settings, Page page) {
		String imagePath = fileManager.getBooksPath() + File.separator + page.getImage();

		if (new File(imagePath).exists()) {
			larex.dataManagement.Page currentLarexPage = new larex.dataManagement.Page(imagePath);
			currentLarexPage.initPage();

			Size pagesize = currentLarexPage.getOriginal().size();

			Parameters parameters = WebLarexTranslator.translateSettingsToParameters(settings, pagesize);
			parameters.getRegionManager().setPointListManager(
					WebLarexTranslator.translateSettingsToPointListManager(settings, page.getId()));

			Segmenter segmenter = new Segmenter(parameters);
			SegmentationResult segmentationResult = segmenter.segment(currentLarexPage.getOriginal());
			currentLarexPage.setSegmentationResult(segmentationResult);

			currentLarexPage.clean();

			System.gc();
			return currentLarexPage;
		} else {
			System.err.println(
					"Warning: Image file could not be found. Segmentation result will be empty. File: " + imagePath);
			return null;
		}
	}

	private larex.dataManagement.Page getLarexPage(Page page) {
		String imagePath = fileManager.getBooksPath() + File.separator + page.getImage();

		if (new File(imagePath).exists()) {
			return new larex.dataManagement.Page(imagePath);
		}
		return null;
	}

	@Override
	public BookSettings readSettings(byte[] settingsFile, int bookID) {
		BookSettings settings = null;
		try {
			DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
			DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
			Document document = dBuilder.parse(new ByteArrayInputStream(settingsFile));

			Book book = getBook(bookID);
			Page page = book.getPage(0);
			String imagePath = fileManager.getBooksPath() + File.separator + page.getImage();
			larex.dataManagement.Page currentLarexPage = new larex.dataManagement.Page(imagePath);
			currentLarexPage.initPage();

			Parameters parameters = SettingsReader.loadSettings(document, currentLarexPage.getBinary());
			settings = LarexWebTranslator.translateParametersToSettings(parameters, book);

			currentLarexPage.clean();
			System.gc();
		} catch (SAXException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (ParserConfigurationException e) {
			e.printStackTrace();
		}
		return settings;
	}

	@Override
	public PageSegmentation readPageXML(byte[] pageXML, int pageNr, int bookID) {
		try {
			DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
			DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
			Document document = dBuilder.parse(new ByteArrayInputStream(pageXML));
			Page page = getBook(bookID).getPage(pageNr);

			SegmentationResult result = PageXMLReader.getSegmentationResult(document);
			PageSegmentation pageSegmentation = LarexWebTranslator
					.translateResultRegionsToSegmentation(result.getRegions(), page.getId());

			List<String> readingOrder = new ArrayList<String>();
			for (ResultRegion region : result.getReadingOrder()) {
				readingOrder.add(region.getId());
			}
			pageSegmentation.setReadingOrder(readingOrder);

			return pageSegmentation;
		} catch (SAXException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (ParserConfigurationException e) {
			e.printStackTrace();
		}
		return null;
	}

	public Book getBook(int bookID) {
		IDatabase database = new FileDatabase(new File(fileManager.getBooksPath()));
		return database.getBook(bookID);
	}
}