package com.web.communication;

import java.util.List;

import org.codehaus.jackson.annotate.JsonCreator;
import org.codehaus.jackson.annotate.JsonProperty;

import com.web.model.BookSettings;

/**
 * Communication object for the gui to request the segmentation of different pages.
 * Contains the segmentation settings and a list of page numbers to segment.
 * 
 */
public class SegmentationRequest {

	@JsonProperty("settings")
	private BookSettings settings;
	@JsonProperty("page")
	private Integer page;
	@JsonProperty("allowLoadLocal")
	private boolean allowToLoadLocal;

	@JsonCreator
	public SegmentationRequest(@JsonProperty("settings") BookSettings settings,
			@JsonProperty("page") Integer page,
			@JsonProperty("allowLoadLocal") boolean allowToLoadLocal) {
		this.page = page;
		this.settings = settings;
		this.allowToLoadLocal = allowToLoadLocal;
	}

	public Integer getPages() {
		return page;
	}

	public BookSettings getSettings() {
		return settings;
	}
	
	public boolean isAllowToLoadLocal() {
		return allowToLoadLocal;
	}
}
