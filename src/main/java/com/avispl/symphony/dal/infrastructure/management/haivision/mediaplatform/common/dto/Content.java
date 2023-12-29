/*
 *  Copyright (c) 2023 AVI-SPL, Inc. All Rights Reserved.
 */

package com.avispl.symphony.dal.infrastructure.management.haivision.mediaplatform.common.dto;

/**
 * Represents content with its unique identifier, name, and type.
 *
 * @author Harry / Symphony Dev Team<br>
 * Created on 12/28/2023
 * @since 1.0.0
 */
public class Content {
	private String id;
	private String name;
	private String type;

	/**
	 * Constructs a Content object with specified parameters.
	 *
	 * @param id The unique identifier for the content.
	 * @param name The name of the content.
	 * @param type The type/category of the content.
	 */
	public Content(String id, String name, String type) {
		this.id = id;
		this.name = name;
		this.type = type;
	}

	/**
	 * Default constructor for Content.
	 */
	public Content() {
	}

	/**
	 * Retrieves {@link #id}
	 *
	 * @return value of {@link #id}
	 */
	public String getId() {
		return id;
	}

	/**
	 * Sets {@link #id} value
	 *
	 * @param id new value of {@link #id}
	 */
	public void setId(String id) {
		this.id = id;
	}

	/**
	 * Retrieves {@link #name}
	 *
	 * @return value of {@link #name}
	 */
	public String getName() {
		return name;
	}

	/**
	 * Sets {@link #name} value
	 *
	 * @param name new value of {@link #name}
	 */
	public void setName(String name) {
		this.name = name;
	}

	/**
	 * Retrieves {@link #type}
	 *
	 * @return value of {@link #type}
	 */
	public String getType() {
		return type;
	}

	/**
	 * Sets {@link #type} value
	 *
	 * @param type new value of {@link #type}
	 */
	public void setType(String type) {
		this.type = type;
	}
}
