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

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.BodyDeclaration;
import org.eclipse.jdt.core.dom.IAnnotationBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;

/**
 * Helps analyzing annotations and their meta annotation relationships.
 * This class caches results, so it can be re-used across various classes using the
 * same AST (for example various reconcilers running in the same ASTs across multiple files.
 * 
 * The cache involved here is just kept in memory and does not get invalidated
 * automatically. You basically need to create a new instance of this annotation type collector
 * if you change files and re-run parsing, validations, etc.
 * 
 * The implementation skips annotations that start with "java.", assuming that users
 * would not ask for hierarchies and relationships for standard java. annotations. Everything
 * else will be taken into account here.
 * 
 * @author Martin Lippert
 */
public class AnnotationTypeCollector {
	
	private final ConcurrentMap<String, AnnotationTypeInformation> annotationTypes;
	
	public AnnotationTypeCollector() {
		this.annotationTypes = new ConcurrentHashMap<>();
	}
	
	/**
	 * check whether the given body declaration (e.g. type, method, field declarations) is
	 * annotated with an annotation of the given type
	 * 
	 * @param bodyDeclaration The declaration that should be checked whether it is annotated with an annotation of the given type
	 * @param annotationType The type of the annotation that we look for at the given body declaration
	 */
	public boolean isAnnotatedWith(BodyDeclaration bodyDeclaration, String annotationType) {
		List<?> modifiers = bodyDeclaration.modifiers();

		for (Object modifier : modifiers) {
			if (modifier instanceof Annotation) {
				if (inherits((Annotation) modifier, annotationType, false)) {
					return true;
				}
			}
		}
		return false;
	}
	
	/**
	 * check whether the given annotation is of the given fully qualified type (if excludeConcreteType is false)
	 * or the given fully qualified type is a meta annotation of the given annotation.
	 * 
	 * This is used, for example, to check whether a certain annotation in the AST is of type `@Bean`. 
	 * 
	 * @param annotation The annotation to check
	 * @param inheritedType The fully qualified type of a (maybe meta-) annotation
	 * @param excludeConcreteType If true, the method does not include the concrete type of the annotation
	 */
	public boolean inherits(Annotation annotation, String inheritedType, boolean excludeConcreteType) {
		if (inheritedType == null) return false;
		
		AnnotationTypeInformation information = getInformation(annotation);
		if (information == null) return false;

		if (!excludeConcreteType && information.getFullyQualifiedName().equals(inheritedType)) {
			return true;
		}
		
		return information != null ? information.inherits(inheritedType) : false;
	}
	
	/**
	 * finds all the annotations that an annotation "contains" in the same of the annotation itself
	 * is being annotated with additional meta annotations. For example, if you ask for the annotation hierarchy
	 * of the `@Configuration` annotation, it would include `@Component` (and more).
	 * 
	 * @param excludeConcreteType Specifies whether the hierarchy should include the root annotation type or not. If true, the returned list of annotation types does not include the type of the given annotation 
	 */
	public String[] getAnnotationHierarchy(Annotation annotation, boolean excludeConcreteType) {
		AnnotationTypeInformation information = getInformation(annotation);
		if (information == null) return new String[0];
		
		return information.getInheritedAnnotations().toArray(new String[0]);
	}

	private boolean ignore(String annotationType) {
		return annotationType != null ? annotationType.startsWith("java.") : true;
	}
	
	private AnnotationTypeInformation getInformation(Annotation annotation) {
		if (annotation == null) return null;
		
		ITypeBinding typeBinding = annotation.resolveTypeBinding();
		if (typeBinding == null) return null;
		
		String annotationType = typeBinding.getQualifiedName();
		if (ignore(annotationType)) return null;

		return this.annotationTypes.computeIfAbsent(annotationType, (type) -> compute(typeBinding));
	}
	
	private AnnotationTypeInformation compute(ITypeBinding annotationType) {
		Set<String> inherited = new LinkedHashSet<>();
		
		if (annotationType != null) {
			collectInheritedAnnotations(annotationType, inherited);
		}

		// make sure the set of inherited annotation types does not include the type itself, even if the algorithm finds that because of cycles
		inherited.remove(annotationType.getQualifiedName());

		return new AnnotationTypeInformation(annotationType.getQualifiedName(), inherited);
	}

	// recursively collect annotations of the given annotation type
	private void collectInheritedAnnotations(ITypeBinding annotationType, Set<String> inherited) {
		IAnnotationBinding[] annotations = annotationType.getAnnotations();

		if (annotations != null) {
			for (IAnnotationBinding annotation : annotations) {

				ITypeBinding type = annotation.getAnnotationType();
				if (type != null) {

					String typeName = type.getQualifiedName();
					if (!ignore(typeName)) {

						boolean notSeenYet = inherited.add(typeName);
						if (notSeenYet) {
							collectInheritedAnnotations(type, inherited);
						}

					}
				}
			}
		}
	}

}
