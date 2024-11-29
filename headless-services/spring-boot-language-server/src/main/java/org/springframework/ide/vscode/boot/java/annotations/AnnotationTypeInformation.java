/*******************************************************************************
 * Copyright (c) 2024 Broadcom
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.annotations;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;

/**
 * @author Martin Lippert
 */
public class AnnotationTypeInformation {
	
	private final String fullyQualifiedName;
	private final Set<String> inheritedAnnotations;
	
	public AnnotationTypeInformation(String fullyQualifiedType, Set<String> inheritedAnnotations) {
		this.fullyQualifiedName = fullyQualifiedType;
		this.inheritedAnnotations = Collections.unmodifiableSet(inheritedAnnotations);
	}
	
	public String getFullyQualifiedName() {
		return fullyQualifiedName;
	}
	
	public Set<String> getInheritedAnnotations() {
		return inheritedAnnotations;
	}
	
	public boolean inherits(String fullyQualifiedAnnotationType) {
		return this.inheritedAnnotations.contains(fullyQualifiedAnnotationType);
	}

	@Override
	public int hashCode() {
		return fullyQualifiedName.hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		AnnotationTypeInformation other = (AnnotationTypeInformation) obj;
		return Objects.equals(fullyQualifiedName, other.fullyQualifiedName);
	}
	
}
